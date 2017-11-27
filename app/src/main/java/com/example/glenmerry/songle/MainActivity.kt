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
                        // User has walked some distance before setting target so show target progress
                        // and total distance separately

                        distanceWalkedWithUnit = distToString(distanceWalked)

                        textViewProgressWalkingTarget.text =
                                "0m walked of $walkingTargetWithUnit target!\n" +
                                    "$distanceWalkedWithUnit total walked while playing Songle!"

                    } else {
                        // User has not walked any distance before setting target, so only display target progress

                        textViewProgressWalkingTarget.text = "0m walked of $walkingTargetWithUnit target\n"
                    }

                } else {
                    // If no input entered, display toast notification
                    toast("Please enter a number!")
                }
            })
            alert.setNegativeButton("Cancel", { _, _ -> })
            alert.show()
        }

        buttonSongsUnlocked.setOnClickListener {
            // Start Songs Unlocked activity
            val intent = Intent(this, SongsUnlockedActivity::class.java)
            intent.putParcelableArrayListExtra("songs", ArrayList(songs))
            startActivity(intent)
        }

        // Restore preferences
        val settings = getSharedPreferences(prefsFile, Context.MODE_PRIVATE)

        selectedDifficulty = settings.getInt("storedSelectedDifficulty", 6)
        if (selectedDifficulty == 6) {
            // If value is 6, no difficulty value has been stored, so set null
            selectedDifficulty = null
        } else {
            // Otherwise use stored difficulty to create difficulty string to show in text view
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
            // If user previously selected option to hide distance tracking feature,
            // hide text view, button and progress bar
            buttonSetTarget.visibility = View.GONE
            progressBarWalkingTarget.visibility = View.GONE
            textViewProgressWalkingTarget.visibility = View.GONE
        }

        // Get titles of unlocked songs and favourites from shared preferences,
        // these will be turned into array lists of song objects once song list is downloaded
        titlesUnlockedLoad = settings.getStringSet("storedSongsUnlocked", setOf(""))
        titlesFavLoad = settings.getStringSet("storedFavourites", setOf(""))

        distanceWalked = settings.getInt("storedDistanceWalked", 0)
        distanceWalkedWithUnit = distToString(distanceWalked)

        walkingTarget = settings.getInt("storedWalkingTarget", -1)
        walkingTargetProgress = settings.getInt("storedWalkingTargetProgress", 0)

        if (walkingTarget != -1) {
            // Walking target has been restored, set up variables, text view and progress bar

            progressBarWalkingTarget.max = walkingTarget!!
            progressBarWalkingTarget.progress = walkingTargetProgress

            walkingTargetWithUnit = distToString(walkingTarget!!)
            walkingTargetProgressWithUnit = distToString(walkingTargetProgress)

            if (walkingTargetProgress < walkingTarget!!) {
                // User has not yet reached target

                if (walkingTargetProgress != distanceWalked) {
                    // User walked some distance before setting target, show target progress and
                    // total distance separately
                    textViewProgressWalkingTarget.text = "${walkingTargetProgressWithUnit} walked of $walkingTargetWithUnit target!\n" +
                            "$distanceWalkedWithUnit total walked while playing Songle!"
                } else {
                    // User has only walked whilst target has been set, only show distance towards target
                    textViewProgressWalkingTarget.text = "${walkingTargetProgressWithUnit} walked of $walkingTargetWithUnit target!"
                }
            } else {
                // User has met their target, display target reached message
                if (walkingTargetProgress != distanceWalked) {
                    targetMet = true
                }
                textViewProgressWalkingTarget.text = "$walkingTargetWithUnit target reached!\n" +
                        "$distanceWalkedWithUnit total walked while playing Songle!"
            }

        } else {
            // No walking target saved to shared preferences, hide progress bar and only display total distance walked

            walkingTarget = null
            progressBarWalkingTarget.progress = 0
            progressBarWalkingTarget.visibility = View.GONE
            textViewProgressWalkingTarget.text = "\n$distanceWalkedWithUnit walked while playing Songle!"
        }
    }

    private fun indexToString(i: Int): String {
        // Returns string of index suitable for inserting into urls etc
        return if (i < 10) {
            "0$i"
        } else {
            i.toString()
        }
    }

    private fun distToString(d: Int): String {
        // Returns string of distance, converting to km if greater than 1000 or else keeping as metres
        return if (d < 1000) {
            "${d}m"
        } else {
            "${BigDecimal(d.toDouble() / 1000).setScale(2, BigDecimal.ROUND_HALF_UP)}km"
        }
    }

    private fun startMaps() {
        if (songs.isEmpty()) {
            // Do not start Maps Activity if songs have not yet been downloaded, KML download would fail
            alert("Songle song list not yet downloaded", "Please wait") {
                positiveButton("Ok") { }
            }.show()
        } else {
            // Song list has been downloaded, safe to start Maps Activity

            val intent = Intent(this, MapsActivity::class.java)
            if (songToPlayIndexString == null || songsUnlocked.contains(songs[songToPlayIndexString!!.toInt()-1])) {
                // If song index has not been created or song to play has already been unlocked, get new index
                val i = getSongIndex()
                if (i != null) {
                    songToPlayIndexString = indexToString(i+1)
                    startMaps()
                } else {
                    // If getSongIndex returns null, all songs have been unlocked already, display dialog
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
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate options menu
        menuInflater.inflate(R.menu.menu_activity_main, menu)
        if (distanceWakedHidden) {
            // Uncheck 'monitor walking distance' checkbox if feature hidden
            menu.getItem(0).isChecked = false
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_reset -> {
            // Reset game option
            alert("All progress will be lost!", "Are you sure you want to reset the game?") {
                positiveButton("Yes, I'm sure") {
                    resetGame()
                }
                negativeButton("No, abort") {}
            }.show()
            true
        }
        R.id.action_collect_distance_pref -> {
            // Option to disable distance tracking
            item.isChecked = !item.isChecked // Invert checkbox
            distanceWakedHidden = !distanceWakedHidden // Invert value of option boolean
            if (item.isChecked) {
                // If distance feature now enabled, show button, progress bar, textview
                buttonSetTarget.visibility = View.VISIBLE
                progressBarWalkingTarget.visibility = View.VISIBLE
                textViewProgressWalkingTarget.visibility = View.VISIBLE
            } else {
                // If feature now hidden, hide elements
                buttonSetTarget.visibility = View.GONE
                progressBarWalkingTarget.visibility = View.GONE
                textViewProgressWalkingTarget.visibility = View.GONE
            }
            true
        }
        R.id.action_collect_distance_reset -> {
            // Option to reset distance data
            alert("Distance walked data and target will be lost!", "Are you sure you want to reset distance walked?") {
                positiveButton("Yes, I'm sure") {
                    // Reset all distance variables
                    distanceWalked = 0
                    distanceWalkedWithUnit = "0m"
                    walkingTarget = null
                    walkingTargetWithUnit = ""
                    progressBarWalkingTarget.progress = 0
                    textViewProgressWalkingTarget.text = ""
                }
                negativeButton("No, abort") { }
            }.show()
            true
        }
        R.id.action_help -> {
            // Launch help page
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
            // No song index found in shared preferences, set to null
            songToPlayIndexString = null
        }

        // Get titles of unlocked songs and favourites from shared preferences
        titlesSkippedLoad = settings.getStringSet("storedSongsSkipped", setOf(""))
        titlesUnlockedLoad = settings.getStringSet("storedSongsUnlocked", setOf(""))

        if (songs.isNotEmpty()) {
            // If songs list has been downloaded, populate unlocked and favourites array lists
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

        // If receiver registered, unregister it
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
            // If no walking target, save as -1, will be set to null when restored from shared preferences
            editor.putInt("storedWalkingTarget", -1)
        }
        editor.putInt("storedWalkingTargetProgress", walkingTargetProgress)

        if (selectedDifficulty != null) {
            editor.putInt("storedSelectedDifficulty", selectedDifficulty!!)
        }

        // Convert unlocked and favourites array lists into set of titles for storage
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
        // Apply changes
        editor.apply()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent) {
        if (requestCode == 1) {
            // Returning from Maps Activity
            if (resultCode == Activity.RESULT_OK) {
                // Get updated value of distance walked
                distanceWalked = data.getIntExtra("returnDistance", distanceWalked)
                distanceWalkedWithUnit = distToString(distanceWalked)

                val returnWalkingTarget = data.getIntExtra("returnWalkingTarget", -1)

                if (returnWalkingTarget != -1) {
                    // Walking target returned from Maps Activity, so update target and progress variables

                    walkingTarget = returnWalkingTarget
                    walkingTargetWithUnit = distToString(walkingTarget!!)

                    walkingTargetProgress = data.getIntExtra("returnWalkingTargetProgress", 0)
                    walkingTargetProgressWithUnit = distToString(walkingTargetProgress)

                    progressBarWalkingTarget.max = walkingTarget!!
                    progressBarWalkingTarget.progress = walkingTargetProgress

                    if (walkingTargetProgress < walkingTarget!!) {
                        // User not yet reached target
                        if (walkingTargetProgress != distanceWalked) {
                            // Total distance walked different from target progress, so show separately
                            textViewProgressWalkingTarget.text = "${walkingTargetProgressWithUnit} walked of $walkingTargetWithUnit target!\n" +
                                    "$distanceWalkedWithUnit total walked while playing Songle!"
                        } else {
                            // Total distance and target progress are the same, so only show target progress
                            textViewProgressWalkingTarget.text = "${walkingTargetProgressWithUnit} walked of $walkingTargetWithUnit target!"
                        }
                    } else {
                        // User has reached target so show target reached message
                        targetMet = true
                        textViewProgressWalkingTarget.text = "$walkingTargetWithUnit target reached!\n" +
                                "$distanceWalkedWithUnit total walked while playing Songle!"
                    }
                } else {
                    // No target, so only show total distance walked
                    textViewProgressWalkingTarget.text = "$distanceWalkedWithUnit walked while playing Songle!"
                }

                val skip = data.getBooleanExtra("returnSkip", false)
                if (skip) {
                    // Song has been skipped

                    if (songToPlayIndexString != null && !songsSkipped.contains(songs[songToPlayIndexString!!.toInt()-1])) {
                        // If song not yet added to list of skipped songs, add it
                        songsSkipped.add(songs[songToPlayIndexString!!.toInt()-1])
                    }

                    // Attempt to get index of next song
                    val i = getSongIndex()
                    if (i != null) {
                        // if song available to play, launch Maps Activity again
                        songToPlayIndexString = indexToString(i+1)
                        startMaps()
                    } else {
                        // No more songs available to play, display all songs unlocked dialog
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
                    // Song has been unlocked

                    if (songToPlayIndexString != null && !songsUnlocked.contains(songs[songToPlayIndexString!!.toInt()-1])) {
                        // If song not yet added to list of unlocked songs, add it
                        songsUnlocked.add(songs[songToPlayIndexString!!.toInt()-1])
                    }

                    // Increment songs unlocked progress bar and text view
                    progressBar.progress++
                    textViewProgress.text = "${songsUnlocked.size}/${songs.size} Songs Unlocked"

                    // Attempt to get index of next song
                    val i = getSongIndex()
                    if (i != null) {
                        // if song available to play, launch Maps Activity again
                        songToPlayIndexString = indexToString(i+1)
                        startMaps()
                    } else {
                        // No more songs available to play, display all songs unlocked dialog
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
        // Download song list XML document asynchronously
        doAsync {
            try {
                loadXmlFromNetwork("http://www.inf.ed.ac.uk/teaching/courses/cslp/data/songs/songs.xml")
            } catch (e: IOException) {
                println("Unable to load content. Check your network connection")
            } catch (e: XmlPullParserException) {
                println("Error parsing XML")
            }
            activityUiThread {
                // In activity UI thread, if song list successfully downloaded,
                // populate unlocked, favourites and skipped songs lists
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

                    // Populate songs unlocked progress bar and textview
                    progressBar.max = songs.size
                    progressBar.progress = songsUnlocked.size
                    textViewProgress.text = "${songsUnlocked.size}/${songs.size} Songs Unlocked"

                    // If there is no song assigned to play yet call getSongIndex
                    if (songToPlayIndexString == null) {
                        val songToPlayIndex = getSongIndex()
                        if (songToPlayIndex != null) {
                            songToPlayIndexString = indexToString(songToPlayIndex+1)
                        } else {
                            // No more songs available to play to offer reset option
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
    }

    private fun loadXmlFromNetwork(urlString: String) {
        val stream = downloadUrl(urlString)

        // Parse XML from input stream to produce song list
        val songListXMLParser = SongListXMLParser()
        songs = songListXMLParser.parse(stream)
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

    // BroadcastReceiver that tracks network connectivity changes.
    private inner class NetworkReceiver: BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val connMgr = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val networkInfo = connMgr.activeNetworkInfo

            if (networkInfo != null) {
                // Network is available
                if (connectionLost) {
                    // Connection was previously lost, so display "Connected" snackbar message
                    val snackbar : Snackbar = Snackbar.make(findViewById(android.R.id.content),
                            "Connected", Snackbar.LENGTH_SHORT)
                    snackbar.show()
                    connectionLost = false
                    if (songs.isEmpty()) {
                        // If failed to download song list due to connection loss, attempt re-download
                        downloadSongs()
                    }
                }
            } else {
                // No network connection so display snackbar
                val snackbar = Snackbar.make(findViewById(android.R.id.content),
                        "No internet connection available", Snackbar.LENGTH_INDEFINITE)
                snackbar.show()
                connectionLost = true
            }
        }
    }

    // Returns a index of a song to be played, if one is available
    private fun getSongIndex(): Int? {
        return when {
            songs.size - songsUnlocked.size == 1 -> {
                // One song is still unlocked so return its index
                val songsCopy = ArrayList<Song>(songs)
                songsCopy.removeAll(songsUnlocked)
                println(songsCopy[0].number.toInt())
                songsCopy[0].number.toInt() -1
            }
            songsUnlocked.size == songs.size -> {
                // All songs are unlocked so no more available to play
                null
            }
            else -> {
                // At least 2 songs still unlocked, return unskipped song is available

                val lockedIndices = arrayListOf<Int>()
                val skippedIndices = arrayListOf<Int>()
                // Create lists of indices of locked and skipped songs
                songs.indices.filterNotTo(lockedIndices) { songsUnlocked.contains(songs[it]) }
                songs.indices.filterTo(skippedIndices) { songsSkipped.contains(songs[it]) }

                return when {
                    songsSkipped.size == 0 -> {
                        // if no songs skipped then randomise all locked songs
                        lockedIndices[randomiser(0, lockedIndices.size - 1)]
                    }
                    else -> {
                        // some songs skipped, remove skipped songs from list of locked indices
                        lockedIndices.removeAll(skippedIndices)
                        when {
                            // if all locked songs skipped, randomise all locked (and skipped) songs
                            lockedIndices.size == 0 -> skippedIndices[randomiser(0, skippedIndices.size - 1)]
                            // if only one song unskipped, return it
                            lockedIndices.size == 1 -> lockedIndices[0]
                            // otherwise randomise locked and unskipped songs
                            else -> lockedIndices[randomiser(0, lockedIndices.size - 1)]
                        }
                    }
                }
            }
        }
    }

    // Returns a random index in range given
    private fun randomiser(from: Int, to: Int): Int {
        val random = Random()
        return random.nextInt(to - from) + from
    }

    // Completely resets game
    private fun resetGame() {
        songsUnlocked.clear()
        favourites.clear()
        // Delete all stored values in shared preferences
        this@MainActivity.deleteSharedPreferences(com.example.glenmerry.songle.prefsFile)
        // Reset triggered variable true means that no values will be saved to shared preferences onPause
        resetTriggered = true
        val intent = baseContext.packageManager.getLaunchIntentForPackage(baseContext.packageName)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        startActivity(intent)
    }
}