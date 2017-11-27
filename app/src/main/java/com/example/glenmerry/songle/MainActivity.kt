package com.example.glenmerry.songle

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.net.ConnectivityManager
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.text.InputType
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.ProgressBar
import kotlinx.android.synthetic.main.content_main.*
import org.jetbrains.anko.*
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException
import java.io.InputStream
import java.math.BigDecimal
import java.net.HttpURLConnection
import java.net.URL
import java.util.*
import kotlin.collections.ArrayList

var favourites = arrayListOf<Song>()
var songs = arrayListOf<Song>()
var songsUnlocked = mutableSetOf<Song>()
val prefsFile = "MyPrefsFile" // for storing preferences

class MainActivity : AppCompatActivity() {

    private var receiver = NetworkReceiver()
    private var connectionLost = false
    private var selectedDifficulty: Int? = null
    private val difficulties = listOf("Beginner", "Easy", "Medium", "Hard", "Impossible")
    private var walkingTarget: Int? = null
    private var walkingTargetWithUnit = String()
    private var walkingTargetProgress = 0
    private var walkingTargetProgressWithUnit = String()
    private var targetMet = false
    private var distanceWalked = 0
    private var distanceWalkedWithUnit = String()
    private var distanceWakedHidden = false
    private var titlesUnlockedLoad = mutableSetOf<String>()
    private var titlesFavLoad = mutableSetOf<String>()
    private var titlesSkippedLoad = mutableSetOf<String>()
    private var songsSkipped = arrayListOf<Song>()
    private var songToPlayIndexString: String? = null
    private var resetTriggered = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Song progress text view is blank until songs downloaded
        textViewProgress.text = ""

        downloadSongs()

        buttonSelectDifficulty.setOnClickListener {
            // Dialog prompting user to select difficulty level
            selector("Please select a difficulty", difficulties, { _, i ->
                selectedDifficulty = (5-i)
                // Update difficulty level text view
                textViewShowDiff.text = "Current Difficulty: ${difficulties[i]}"
            })
        }

        buttonPlay.setOnClickListener {
            if (!connectionLost) {
                if (selectedDifficulty != null) {
                    // If difficulty has been selected, start Maps Activity
                    startMaps()
                } else {
                    // If no difficulty selected, first prompt user to select difficulty
                    // and only then start Maps Activity
                    selector("Please select a difficulty", difficulties, { _, i ->
                        selectedDifficulty = (5 - i)
                        textViewShowDiff.text = "Current Difficulty: ${difficulties[i]}"
                        startMaps()
                    })
                }
            } else {
                // If no internet connection, do not launch Maps Activity and show alert dialog
                alert("Please check your internet connection", "Songle is unable to connect") {
                    positiveButton("Ok") { }
                }.show()
            }
        }

        /*if (walkingTarget != null) {
            progressBarWalkingTarget.max = walkingTarget!!
            if (distanceWalked < walkingTarget!!) {
                progressBarWalkingTarget.progress = distanceWalked
                distanceWalkedWithUnit = if (distanceWalked < 1000) {
                    "${distanceWalked}m"
                } else {
                    "${distanceWalked / 1000}km"
                }
                textViewProgressWalkingTarget.text = "$distanceWalkedWithUnit walked of $walkingTargetWithUnit target!"
            } else {
                progressBarWalkingTarget.progress = walkingTarget!!
                textViewProgressWalkingTarget.text = "Congratulations you've reached your walking target of $walkingTarget, set a new one?"
            }
        }*/

        buttonSetTarget.setOnClickListener {
            // Create alert dialog in which user can enter desired walking target
            val alert = AlertDialog.Builder(this)
            alert.setTitle("Set a new walking target in metres")
            val input = EditText(this)
            // User is given numeric keyboard as we only want numeric input
            input.inputType = InputType.TYPE_CLASS_NUMBER
            input.setRawInputType(Configuration.KEYBOARD_12KEY)
            alert.setView(input)
            alert.setPositiveButton("Set", { _, _ ->
                if (input.text.isNotEmpty()) {
                    // New walking target set
                    progressBarWalkingTarget.visibility = View.VISIBLE
                    targetMet = false
                    walkingTarget = input.text.toString().toInt()
                    // Walking target progress is reset
                    walkingTargetProgress = 0
                    progressBarWalkingTarget.progress = 0
                    walkingTargetProgressWithUnit = "0m"
                    walkingTargetWithUnit = distToString(walkingTarget!!)
                    progressBarWalkingTarget.max = walkingTarget!!
                    if (distanceWalked > 0) {
                        distanceWalkedWithUnit = if (distanceWalked < 1000) {
                            "${distanceWalked}m"
                        } else {
                            "${BigDecimal(distanceWalked.toDouble() / 1000).setScale(2, BigDecimal.ROUND_HALF_UP)}km"
                        }
                        if (distanceWalked == walkingTarget) {
                            "$distanceWalkedWithUnit walked of $walkingTargetWithUnit target!\n"
                        } else {
                            textViewProgressWalkingTarget.text = "$walkingTargetProgressWithUnit walked of $walkingTargetWithUnit target!\n" +
                                    "$distanceWalkedWithUnit total walked while playing Songle!"
                        }
                    } else {
                        if (distanceWalked > 0) {
                            textViewProgressWalkingTarget.text = "0m walked of $walkingTargetWithUnit target\n" +
                                    "$distanceWalkedWithUnit total walked while playing Songle!"
                        } else {
                            textViewProgressWalkingTarget.text = "0m walked of $walkingTargetWithUnit target!"
                        }
                    }
                } else {
                    toast("Please enter a number!")
                }
            })
            alert.setNegativeButton("Cancel", { _, _ -> })
            alert.show()
        }

        buttonSongsUnlocked.setOnClickListener {
            val intent = Intent(this, SongsUnlockedActivity::class.java)
            intent.putParcelableArrayListExtra("songs", ArrayList(songs))
            startActivity(intent)
        }

        // Restore preferences
        val settings = getSharedPreferences(prefsFile, Context.MODE_PRIVATE)

        selectedDifficulty = settings.getInt("storedSelectedDifficulty", 6)
        if (selectedDifficulty == 6) {
            selectedDifficulty = null
        } else {
            val diffString: String = when (selectedDifficulty) {
                1 -> "Impossible"
                2 -> "Hard"
                3 -> "Medium"
                4 -> "Easy"
                5 -> "Beginner"
                else -> ""
            }
            textViewShowDiff.text = "Selected Difficulty: $diffString"
        }
        distanceWakedHidden = settings.getBoolean("storedDistanceWalkedHidden", false)
        if (distanceWakedHidden) {
            buttonSetTarget.visibility = View.GONE
            progressBarWalkingTarget.visibility = View.GONE
            textViewProgressWalkingTarget.visibility = View.GONE
        }

        titlesUnlockedLoad = settings.getStringSet("storedSongsUnlocked", setOf(""))
        titlesFavLoad = settings.getStringSet("storedFavourites", setOf(""))

        distanceWalked = settings.getInt("storedDistanceWalked", 0)
        walkingTarget = settings.getInt("storedWalkingTarget", -1)
        walkingTargetProgress = settings.getInt("storedWalkingTargetProgress", 0)

        distanceWalkedWithUnit = if (distanceWalked < 1000) {
            "${distanceWalked}m"
        } else {
            "${BigDecimal(distanceWalked.toDouble() / 1000).setScale(2, BigDecimal.ROUND_HALF_UP)}km"
        }

        if (walkingTarget != -1 && walkingTarget != null) {
            progressBarWalkingTarget.max = walkingTarget!!
            progressBarWalkingTarget.progress = walkingTargetProgress

            walkingTargetWithUnit = if (walkingTarget!! < 1000) {
                "${walkingTarget}m"
            } else {
                "${BigDecimal(walkingTarget!!.toDouble() / 1000).setScale(2, BigDecimal.ROUND_HALF_UP)}km"
            }
            walkingTargetProgressWithUnit = if (walkingTargetProgress < 1000) {
                "${walkingTargetProgress}m"
            } else {
                "${BigDecimal(walkingTargetProgress.toDouble() / 1000).setScale(2, BigDecimal.ROUND_HALF_UP)}km"
            }

            if (walkingTargetProgress < walkingTarget!!) {
                if (walkingTargetProgress != distanceWalked) {
                    textViewProgressWalkingTarget.text = "${walkingTargetProgressWithUnit} walked of $walkingTargetWithUnit target!\n" +
                            "$distanceWalkedWithUnit total walked while playing Songle!"
                } else {
                    textViewProgressWalkingTarget.text = "${walkingTargetProgressWithUnit} walked of $walkingTargetWithUnit target!"
                }
            } else {
                if (walkingTargetProgress != distanceWalked) {
                    targetMet = true
                }
                textViewProgressWalkingTarget.text = "$walkingTargetWithUnit target reached!\n" +
                        "$distanceWalkedWithUnit total walked while playing Songle!"
            }

        } else {
            walkingTarget = null
            progressBarWalkingTarget.progress = 0
            progressBarWalkingTarget.visibility = View.GONE
            textViewProgressWalkingTarget.text = "\n$distanceWalkedWithUnit walked while playing Songle!"
        }
    }

    private fun indexToString(i: Int): String {
        return if (i < 10) {
            "0$i"
        } else {
            i.toString()
        }
    }

    private fun distToString(d: Int): String {
        return if (d < 1000) {
            "${d}m"
        } else {
            "${BigDecimal(d.toDouble() / 1000).setScale(2, BigDecimal.ROUND_HALF_UP)}km"
        }
    }

    private fun startMaps() {
        if (songs.isEmpty()) {
            alert("Songle song list not yet downloaded", "Please wait") {
                positiveButton("Ok") { }
            }.show()
        }
        val intent = Intent(this, MapsActivity::class.java)
        if (songToPlayIndexString == null || songsUnlocked.contains(songs[songToPlayIndexString!!.toInt()-1])) {
            val i = getSongIndex()
            if (i != null) {
                songToPlayIndexString = if ((i + 1) < 10) {
                    "0${i + 1}"
                } else {
                    (i + 1).toString()
                }
                println("song to play index is >>>> ${i+1}")
                startMaps()
            } else {
                alert("Reset the game to play again?", "Nice one! You unlocked all of Songle's songs!") {
                    positiveButton("Reset Game") {
                        resetGame()
                    }
                    negativeButton("No Thanks") { }
                }.show()
                return
            }
        }
        intent.putExtra("difficulty", selectedDifficulty!!)
        intent.putExtra("songToPlay", songToPlayIndexString)
        intent.putExtra("songsSkipped", songsSkipped)
        intent.putExtra("distanceWalked", distanceWalked)
        intent.putExtra("walkingTarget", walkingTarget)
        intent.putExtra("walkingTargetProgress", walkingTargetProgress)
        intent.putExtra("targetMet", targetMet)
        startActivityForResult(intent, 1)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_activity_main, menu)
        if (distanceWakedHidden) {
            menu.getItem(0).isChecked = false
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_reset -> {
            alert("All progress will be lost!", "Are you sure you want to reset the game?") {
                positiveButton("Yes, I'm sure") {
                    resetGame()
                }
                negativeButton("No, abort") {}
            }.show()
            true
        }
        R.id.action_collect_distance_pref -> {
            item.isChecked = !item.isChecked
            distanceWakedHidden = !distanceWakedHidden
            if (item.isChecked) {
                buttonSetTarget.visibility = View.VISIBLE
                progressBarWalkingTarget.visibility = View.VISIBLE
                textViewProgressWalkingTarget.visibility = View.VISIBLE
            } else {
                buttonSetTarget.visibility = View.GONE
                progressBarWalkingTarget.visibility = View.GONE
                textViewProgressWalkingTarget.visibility = View.GONE
            }
            true
        }
        R.id.action_collect_distance_reset -> {
            alert("Distance walked data and target will be lost!", "Are you sure you want to reset distance walked?") {
                positiveButton("Yes, I'm sure") {
                    distanceWalked = 0
                    distanceWalkedWithUnit = "0m"
                    walkingTarget = null
                    walkingTargetWithUnit = ""
                    progressBarWalkingTarget.progress = 0
                    textViewProgressWalkingTarget.text = ""
                }
                negativeButton("No, abort") {}
            }.show()
            true
        }
        R.id.action_help -> {
            val intent = Intent(this, HelpActivity::class.java)
            startActivity(intent)
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    override fun onStart() {
        super.onStart()

        // Register BroadcastReceiver to track connection changes.
        val filter = IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)
        this.registerReceiver(receiver, filter)

        // Restore preferences
        val settings = getSharedPreferences(prefsFile, Context.MODE_PRIVATE)

        songToPlayIndexString = settings.getString("storedSongToPlayIndexString", "?")
        if (songToPlayIndexString.equals("?")) {
            println("Main Activity >>>> Song to play index string set to null")
            songToPlayIndexString = null
        }

        titlesSkippedLoad = settings.getStringSet("storedSongsSkipped", setOf(""))
        titlesUnlockedLoad = settings.getStringSet("storedSongsUnlocked", setOf(""))

        if (songs.isNotEmpty()) {
            songs
                    .filter { titlesSkippedLoad.contains(it.title) }
                    .forEach { if (!songsSkipped.contains(it)) {
                        songsSkipped.add(it)
                    }}
            songs
                    .filter { titlesUnlockedLoad.contains(it.title) }
                    .forEach { if (!songsUnlocked.contains(it)) {
                        songsUnlocked.add(it)
                    }}
        }
    }

    override fun onPause() {
        super.onPause()

        try {
            unregisterReceiver(receiver)
        } catch(e: IllegalArgumentException) {
            println("Receiver not registered")
        }

        if (resetTriggered) {
            // If game has been reset, return early and do not store shared preferences
            return
        }

        // All objects are from android.context.Context
        val settings = getSharedPreferences(prefsFile, Context.MODE_PRIVATE)

        // We need an Editor object to make preference changes.
        val editor = settings.edit()

        editor.putBoolean("storedDistanceWalkedHidden", distanceWakedHidden)
        editor.putInt("storedDistanceWalked", distanceWalked)

        if (walkingTarget != null) {
            editor.putInt("storedWalkingTarget", walkingTarget!!)
        } else {
            editor.putInt("storedWalkingTarget", -1)
        }
        editor.putInt("storedWalkingTargetProgress", walkingTargetProgress)

        if (selectedDifficulty != null) {
            editor.putInt("storedSelectedDifficulty", selectedDifficulty!!)
        }

        val titlesUnlocked = songsUnlocked
                .map { it.title }
                .toSet()
        editor.putStringSet("storedSongsUnlocked", titlesUnlocked)

        val titlesFav = favourites
                .map { it.title }
                .toSet()
        editor.putStringSet("storedFavourites", titlesFav)

        if (songToPlayIndexString != null) {
            editor.putString("storedSongToPlayIndexString", songToPlayIndexString)
        }
        editor.apply()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent) {
        if (requestCode == 1) {
            if (resultCode == Activity.RESULT_OK) {
                distanceWalked = data.getIntExtra("returnDistance", distanceWalked)
                distanceWalkedWithUnit = if (distanceWalked < 1000) {
                    "${distanceWalked}m"
                } else {
                    "${BigDecimal(distanceWalked.toDouble() / 1000).setScale(2, BigDecimal.ROUND_HALF_UP)}km"
                }

                val returnWalkingTarget = data.getIntExtra("returnWalkingTarget", Int.MAX_VALUE)

                if (returnWalkingTarget != Int.MAX_VALUE) {
                    walkingTarget = returnWalkingTarget
                    walkingTargetWithUnit = if (walkingTarget!! < 1000) {
                        "${walkingTarget}m"
                    } else {
                        "${BigDecimal(walkingTarget!!.toDouble() / 1000).setScale(2, BigDecimal.ROUND_HALF_UP)}km"
                    }
                    walkingTargetProgress = data.getIntExtra("returnWalkingTargetProgress", 0)
                    walkingTargetProgressWithUnit = if (walkingTargetProgress < 1000) {
                        "${walkingTargetProgress}m"
                    } else {
                        "${BigDecimal(walkingTargetProgress.toDouble() / 1000).setScale(2, BigDecimal.ROUND_HALF_UP)}km"
                    }

                    progressBarWalkingTarget.max = walkingTarget!!
                    progressBarWalkingTarget.progress = walkingTargetProgress
                    if (walkingTargetProgress < walkingTarget!!) {
                        if (walkingTargetProgress != distanceWalked) {
                            textViewProgressWalkingTarget.text = "${walkingTargetProgressWithUnit} walked of $walkingTargetWithUnit target!\n" +
                                    "$distanceWalkedWithUnit total walked while playing Songle!"
                        } else {
                            textViewProgressWalkingTarget.text = "${walkingTargetProgressWithUnit} walked of $walkingTargetWithUnit target!"
                        }
                    } else {
                        if (walkingTargetProgress != distanceWalked) {
                            targetMet = true
                            textViewProgressWalkingTarget.text = "$walkingTargetWithUnit target reached!\n" +
                                    "$distanceWalkedWithUnit total walked while playing Songle!"
                        }
                    }
                } else {
                    textViewProgressWalkingTarget.text = "$distanceWalkedWithUnit walked while playing Songle!"
                }

                val skip = data.getBooleanExtra("returnSkip", false)
                if (skip) {
                    if (songToPlayIndexString != null && !songsSkipped.contains(songs[songToPlayIndexString!!.toInt()-1])) {
                        songsSkipped.add(songs[songToPlayIndexString!!.toInt()-1])
                    }
                    val i = getSongIndex()
                    if (i != null) {
                        songToPlayIndexString = if ((i+1) < 10) {
                            "0${i+1}"
                        } else {
                            (i+1).toString()
                        }
                        println("song to play index is >>>> ${i+1}")
                        startMaps()
                    } else {
                        alert("Reset the game to play again?", "Nice one! You unlocked all of Songle's songs!") {
                            positiveButton("Reset Game") {
                                resetGame()
                            }
                            negativeButton("No Thanks") { }
                        }.show()
                    }
                }

                val unlocked = data.getBooleanExtra("returnUnlocked", false)
                if (unlocked) {
                    if (songToPlayIndexString != null && !songsUnlocked.contains(songs[songToPlayIndexString!!.toInt()-1])) {
                        songsUnlocked.add(songs[songToPlayIndexString!!.toInt()-1])
                    }
                    progressBar.progress++
                    textViewProgress.text = "${songsUnlocked.size}/${songs.size} Songs Unlocked"
                    val i = getSongIndex()
                    if (i != null) {
                        songToPlayIndexString = if (i+1 < 10) {
                            "0${i+1}"
                        } else {
                            (i+1).toString()
                        }
                        println("song to play index is >>>> ${i+1}")
                        startMaps()
                    } else {
                        alert("Reset the game to play again?", "Nice one! You unlocked all of Songle's songs!") {
                            positiveButton("Reset Game") {
                                resetGame()
                            }
                            negativeButton("No Thanks") { }
                        }.show()
                    }
                }
            }
        }
    }

    private fun downloadSongs() {
        doAsync {
            try {
                loadXmlFromNetwork("http://www.inf.ed.ac.uk/teaching/courses/cslp/data/songs/songs.xml")
            } catch (e: IOException) {
                println("Unable to load content. Check your network connection")
            } catch (e: XmlPullParserException) {
                println("Error parsing XML")
            }
            activityUiThread {
                if (songs.isNotEmpty()) {
                    for (song in songs) {
                        if (titlesUnlockedLoad.contains(song.title)) {
                            songsUnlocked.add(song)
                        }
                        if (titlesFavLoad.contains(song.title)) {
                            favourites.add(song)
                        }
                        if (titlesSkippedLoad.contains(song.title)) {
                            songsSkipped.add(song)
                        }
                        println(song)
                    }
                    progressBar.max = songs.size
                    progressBar.progress = songsUnlocked.size
                    textViewProgress.text = "${songsUnlocked.size}/${songs.size} Songs Unlocked"

                    if (songToPlayIndexString == null) {
                        val songToPlayIndex = getSongIndex()
                        if (songToPlayIndex != null) {
                            songToPlayIndexString = if (songToPlayIndex+1 < 10) {
                                "0${songToPlayIndex+1}"
                            } else {
                                (songToPlayIndex+1).toString()
                            }
                        } else {
                            resetGame()
                        }
                    }
                }
            }
        }
    }

    private fun loadXmlFromNetwork(urlString: String): String {
        val result = StringBuilder()
        val stream = downloadUrl(urlString)

        // Do something with stream e.g. parse as XML, build result
        val songListXMLParser = SongListXMLParser()
        songs = songListXMLParser.parse(stream)
        return result.toString()
    }

    // Given a string representation of a URL, sets up a connection and gets an input stream.
    @Throws(IOException::class)
    private fun downloadUrl(urlString: String): InputStream {
        val url = URL(urlString)
        val conn = url.openConnection() as HttpURLConnection

        conn.readTimeout = 10000 // milliseconds
        conn.connectTimeout = 15000 // milliseconds
        conn.requestMethod = "GET"
        conn.doInput = true

        // Starts the query
        conn.connect()
        return conn.inputStream
    }

    private inner class NetworkReceiver: BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val connMgr = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val networkInfo = connMgr.activeNetworkInfo

            if (networkInfo != null) {
                // Network is available
                if (connectionLost) {
                    val snackbar : Snackbar = Snackbar.make(findViewById(android.R.id.content),
                            "Connected", Snackbar.LENGTH_SHORT)
                    snackbar.show()
                    connectionLost = false
                    if (songs.isEmpty()) {
                        downloadSongs()
                    }
                }
            } else {
                // No network connection
                val snackbar = Snackbar.make(findViewById(android.R.id.content),
                        "No internet connection available", Snackbar.LENGTH_INDEFINITE)
                snackbar.show()
                connectionLost = true
            }
        }
    }

    private fun getSongIndex(): Int? {

        return when {
            songs.size - songsUnlocked.size == 1 -> {
                val songsCopy = ArrayList<Song>(songs)
                songsCopy.removeAll(songsUnlocked)
                println(songsCopy[0].number.toInt())
                songsCopy[0].number.toInt() -1
            }
            songsUnlocked.size == songs.size -> {
                null
            }
            else -> {
                println("songs unlocked: $songsUnlocked")
                println("songs skipped: $songsSkipped")

                val lockedIndices = arrayListOf<Int>()
                val skippedIndices = arrayListOf<Int>()

                songs.indices.filterNotTo(lockedIndices) { songsUnlocked.contains(songs[it]) }
                songs.indices.filterTo(skippedIndices) { songsSkipped.contains(songs[it]) }

                return when {
                    songsSkipped.size == 0 -> {
                        // if no songs skipped then randomise locked songs
                        println("getting new song index >>>> no songs skipped, randomise locked songs")
                        println("locked indices: $lockedIndices")
                        lockedIndices[randomiser(0, lockedIndices.size - 1)]
                    }
                    else -> {
                        lockedIndices.removeAll(skippedIndices)
                        when {
                            lockedIndices.size == 0 -> skippedIndices[randomiser(0, skippedIndices.size - 1)]
                            lockedIndices.size == 1 -> lockedIndices[0]
                            else -> lockedIndices[randomiser(0, lockedIndices.size - 1)]
                        }
                    }
                }
            }
        }
    }

    private fun randomiser(from: Int, to: Int): Int {
        val random = Random()
        return random.nextInt(to - from) + from
    }

    private fun resetGame() {
        songsUnlocked.clear()
        favourites.clear()
        this@MainActivity.deleteSharedPreferences(com.example.glenmerry.songle.prefsFile)
        resetTriggered = true
        val intent = baseContext.packageManager.getLaunchIntentForPackage(baseContext.packageName)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        startActivity(intent)
    }
}