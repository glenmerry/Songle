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
import android.widget.TextView
import kotlinx.android.synthetic.main.content_main.*
import org.jetbrains.anko.*
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException
import java.io.InputStream
import java.math.BigDecimal
import java.net.HttpURLConnection
import java.net.URL
import java.util.*

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
    private var songsSkipped = arrayListOf<Song>()
    private val songsUnlocked: ArrayList<Song> = ArrayList()
    private var songToPlayIndexString = "01"

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
                    songsUnlocked.clear()
                    progressBar.progress = songsUnlocked.size
                    textViewProgress.text = "${songsUnlocked.size}/${songs.size} Songs Unlocked"
                    selectedDifficulty = null
                    textViewShowDiff.text = ""
                    distanceWalked = 0
                    distanceWalkedWithUnit = "0m"
                    walkingTarget = null
                    walkingTargetWithUnit = ""
                    textViewProgressWalkingTarget.text = "" +
                            ""
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
            songsUnlocked.add(songs[randomSongIndex(0, songs.size)])
            progressBar.progress = songsUnlocked.size
            textViewProgress.text = "${songsUnlocked.size}/${songs.size} Songs Unlocked"
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

        downloadSongs()

        buttonPlay.setOnClickListener {
            if (songs.isNotEmpty()) {
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
                alert("Please check your internet connection", "Song list has not yet downloaded") {
                    positiveButton("Ok") {downloadSongs()}
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
/*
        progressBarWalkingTarget.max = 1000
        progressBarWalkingTarget.progress = 420
        textViewProgressWalkingTarget.text = "420m walked of 1km target!\n2.5km total walked while playing Songle!"*/

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
                if (songs.isEmpty()) {
                    alert("Please check your internet connection", "Error downloading song list") {
                        positiveButton("Try Again") {downloadSongs()}
                    }.show()
                } else {
                    for (song in songs) {
                        if (titlesUnlockedLoad.contains(song.title)) {
                            songsUnlocked.add(song)
                        }
                        if (titlesFavLoad.contains(song.title)) {
                            favourites.add(song)
                        }
                        println(song)
                    }
                    progressBar.max = songs.size
                    progressBar.progress = songsUnlocked.size
                    textViewProgress.text = "${songsUnlocked.size}/${songs.size} Songs Unlocked"

                    var songToPlayIndex = randomSongIndex(0, songs.size)
                    while (songsUnlocked.contains(songs[songToPlayIndex])) {
                        songToPlayIndex = randomSongIndex(0, songs.size)
                    }

                    songToPlayIndexString = if (songToPlayIndex < 10) {
                        "0$songToPlayIndex"
                    } else {
                        songToPlayIndex.toString()
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
        intent.putExtra("distanceWalked", distanceWalked)
        intent.putExtra("walkingTarget", walkingTarget)
        intent.putExtra("walkingTargetProgress", walkingTargetProgress)
        intent.putExtra("targetMet", targetMet)
        startActivityForResult(intent, 1)
    }

    override fun onPause() {
        super.onPause()

        // All objects are from android.context.Context
        val settings = getSharedPreferences(prefsFile, Context.MODE_PRIVATE)

        // We need an Editor object to make preference changes.
        val editor = settings.edit()

        editor.putBoolean("storedDistanceWalkedHidden", distanceWakedHidden)
        editor.putInt("storedDistanceWalked", distanceWalked)
        if (walkingTarget != null) {
            editor.putInt("storedWalkingTarget", walkingTarget!!)
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

    private fun randomSongIndex(from: Int, to: Int): Int {
        val random = Random()
        return random.nextInt(to - from) + from
    }
}