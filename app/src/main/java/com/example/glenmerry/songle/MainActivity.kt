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
import kotlinx.android.synthetic.main.content_main.*
import org.jetbrains.anko.activityUiThread
import org.jetbrains.anko.alert
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.selector
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException
import java.io.InputStream
import java.math.BigDecimal
import java.net.HttpURLConnection
import java.net.URL
import java.util.*
import kotlin.collections.ArrayList

var favourites = arrayListOf<Song>()
var songs = listOf<Song>()
val prefsFile = "MyPrefsFile" // for storing preferences

class MainActivity : AppCompatActivity() {

    // The BroadcastReceiver that tracks network connectivity changes.
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
    private val songsUnlocked: ArrayList<Song> = ArrayList()
    private var songToPlayIndexString: String? = null
    private var resetTriggered = false

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
                    this@MainActivity.deleteSharedPreferences(com.example.glenmerry.songle.prefsFile)
                    resetTriggered = true
                    val intent = baseContext.packageManager.getLaunchIntentForPackage(baseContext.packageName)
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    startActivity(intent)
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
            /*val intent = Intent(this, HelpActivity::class.java)
            startActivity(intent)
            true*/
            songsUnlocked.add(songs[randomiser(0, songs.size)])
            println("songs unlocked size  now ${songsUnlocked.size}")
            progressBar.progress = songsUnlocked.size
            textViewProgress.text = "${songsUnlocked.size}/${songs.size} Songs Unlocked"
            downloadSongs()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Register BroadcastReceiver to track connection changes.
        val filter = IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)
        this.registerReceiver(receiver, filter)

        textViewProgress.text = ""

        downloadSongs()

        buttonPlay.setOnClickListener {
            if (!connectionLost) {
                if (selectedDifficulty != null) {
                    startMaps()
                } else {
                    selector("Please select a difficulty", difficulties, { _, i ->
                        selectedDifficulty = (5 - i)
                        textViewShowDiff.text = "Current Difficulty: ${difficulties[i]}"
                        startMaps()
                    })
                }
            } else {
                alert("Please check your internet connection", "Songle is unable to connect") {
                    positiveButton("Ok") { }
                }.show()
            }
        }

        buttonSelectDifficulty.setOnClickListener {
            selector("Please select a difficulty", difficulties, { _, i ->
                selectedDifficulty = (5-i)
                textViewShowDiff.text = "Current Difficulty: ${difficulties[i]}"
            })
        }

        if (walkingTarget != null) {
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
        }

        buttonSetTarget.setOnClickListener {
            val alert = AlertDialog.Builder(this)
            alert.setTitle("Set a new walking target in metres")
            val input = EditText(this)
            input.inputType = InputType.TYPE_CLASS_NUMBER
            input.setRawInputType(Configuration.KEYBOARD_12KEY)
            alert.setView(input)
            alert.setPositiveButton("Set", { _, _ ->
                targetMet = false
                walkingTarget = input.text.toString().toInt()
                walkingTargetProgress = 0
                walkingTargetProgressWithUnit = "0m"
                walkingTargetWithUnit = if (walkingTarget!! < 1000) {
                    "${walkingTarget}m"
                } else {
                    "${BigDecimal(walkingTarget!!.toDouble()/1000).setScale(2, BigDecimal.ROUND_HALF_UP)}km"
                }
                progressBarWalkingTarget.max = walkingTarget!!
                if (distanceWalked > 0) {
                    distanceWalkedWithUnit = if (distanceWalked < 1000) {
                        "${distanceWalked}m"
                    } else {
                        "${BigDecimal(distanceWalked.toDouble()/1000).setScale(2, BigDecimal.ROUND_HALF_UP)}km"
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
            })
            alert.setNegativeButton("Cancel", { _, _ -> })
            alert.show()
        }

        buttonSongsUnlocked.setOnClickListener {
            val intent = Intent(this, SongsUnlockedActivity::class.java)
            intent.putParcelableArrayListExtra("songs", ArrayList(songs))
            intent.putParcelableArrayListExtra("songsUnlocked", songsUnlocked)
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

        if (walkingTarget != -1) {
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
            textViewProgressWalkingTarget.text = "$distanceWalkedWithUnit total walked while playing Songle!"
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

                        songToPlayIndexString = if (songToPlayIndex < 10) {
                            "0$songToPlayIndex"
                        } else {
                            songToPlayIndex.toString()
                        }
                    }
                }
            }
        }
    }

    private fun startMaps() {
        val intent = Intent(this, MapsActivity::class.java)
        intent.putExtra("difficulty", selectedDifficulty!!)
        intent.putExtra("songToPlay", songToPlayIndexString)
        intent.putExtra("songsSkipped", songsSkipped)
        intent.putExtra("songsUnlocked", songsUnlocked)
        intent.putExtra("distanceWalked", distanceWalked)
        intent.putExtra("walkingTarget", walkingTarget)
        intent.putExtra("walkingTargetProgress", walkingTargetProgress)
        intent.putExtra("targetMet", targetMet)
        startActivityForResult(intent, 1)
    }

    override fun onStart() {
        super.onStart()
        // Restore preferences
        val settings = getSharedPreferences(prefsFile, Context.MODE_PRIVATE)

        songToPlayIndexString = settings.getString("storedSongToPlayIndexString", "?")
        if (songToPlayIndexString.equals("?")) {
            println("Main Activity >>>> Song to play index string set to null")
            songToPlayIndexString = null
        }

        titlesSkippedLoad = settings.getStringSet("storedSongsSkipped", setOf(""))

        if (songs.isNotEmpty()) {
            songs
                    .filter { titlesSkippedLoad.contains(it.title) }
                    .forEach { songsSkipped.add(it) }
        }
    }

    override fun onPause() {
        super.onPause()

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
        Log.i("Main activity","onActivityResult")

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
                    textViewProgressWalkingTarget.text = "$distanceWalkedWithUnit total walked while playing Songle!"
                }

                val skip = data.getBooleanExtra("returnSkip", false)
                if (skip) {
                    if (songToPlayIndexString != null && !songsSkipped.contains(songs[songToPlayIndexString!!.toInt()-1])) {
                        songsSkipped.add(songs[songToPlayIndexString!!.toInt()-1])
                    }
                    val songToPlayIndex = getSongIndex() + 1
                    songToPlayIndexString = if (songToPlayIndex < 10) {
                        "0$songToPlayIndex"
                    } else {
                        songToPlayIndex.toString()
                    }
                    println("song to play index is >>>> $songToPlayIndex")
                    startMaps()
                }

                val unlocked = data.getBooleanExtra("returnUnlocked", false)
                if (unlocked) {
                    if (songToPlayIndexString != null && !songsUnlocked.contains(songs[songToPlayIndexString!!.toInt()-1])) {
                        songsUnlocked.add(songs[songToPlayIndexString!!.toInt()-1])
                    }
                    progressBar.progress++
                    textViewProgress.text = "${songsUnlocked.size}/${songs.size} Songs Unlocked"
                    val songToPlayIndex = getSongIndex() + 1
                    songToPlayIndexString = if (songToPlayIndex < 10) {
                        "0$songToPlayIndex"
                    } else {
                        songToPlayIndex.toString()
                    }
                    println("song to play index is >>>> $songToPlayIndex")
                    startMaps()
                }
            }
        }
    }

    private inner class NetworkReceiver : BroadcastReceiver() {
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
                val snackbar : Snackbar = Snackbar.make(findViewById(android.R.id.content),
                        "No internet connection available", Snackbar.LENGTH_INDEFINITE)
                snackbar.show()
                connectionLost = true
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

    fun randomiser(from: Int, to: Int): Int {
        val random = Random()
        return random.nextInt(to - from) + from
    }

    fun getSongIndex(): Int {

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
                lockedIndices[randomiser(0, lockedIndices.size-1)]
            }
            lockedIndices == skippedIndices -> {
                // if all locked songs skipped then randomise skipped songs
                println("getting new song index >>>> all locked songs skipped, randomise skipped songs")
                skippedIndices[randomiser(0, skippedIndices.size-1)]
            }
            else -> {
                // if only some locked songs skipped then randomise unskipped locked songs
                println("getting new song index >>>> some locked songs skipped, randomise unskipped locked songs")
                println("locked indices >>>>>> $lockedIndices")
                println("skipped indices >>>>>>> $skippedIndices")
                lockedIndices.removeAll(skippedIndices)
                println(">>> locked songs after removing skips: $lockedIndices")
                lockedIndices[randomiser(0, lockedIndices.size-1)]
            }
        }
    }
}