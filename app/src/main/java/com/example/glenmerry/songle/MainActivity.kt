package com.example.glenmerry.songle

import android.app.Activity
import android.content.*
import android.content.res.Configuration
import android.net.ConnectivityManager
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.text.InputType
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
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

var songs = listOf<Song>()
val songsUnlocked: ArrayList<Song> = ArrayList()
var songToPlayIndexString = "01"

class MainActivity : AppCompatActivity() {

    // The BroadcastReceiver that tracks network connectivity changes.
    private var receiver = NetworkReceiver()
    private var connectionLost = false
    private var selectedDifficulty: Int? = null
    private val difficulties = listOf("Beginner", "Easy", "Medium", "Hard", "Impossible")
    private var walkingTarget: Int = 200
    private var walkingTargetWithUnit = String()
    private var distanceWalked = 15
    private var distanceWalkedWithUnit = String()
    private lateinit var progBarDist: ProgressBar
    private lateinit var progTextDist: TextView

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_activity_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_reset -> {
                alert("All progress will be lost!", "Are you sure you want to reset the game?") {
                    positiveButton("Yes, I'm sure") {}
                    negativeButton("No, abort") {}
                }.show()
                true
            }
            R.id.action_collect_distance_pref -> {
                val buttonWalkTarget = findViewById(R.id.buttonSetTarget) as Button
                val progBarWalking = findViewById(R.id.progressBarWalkingTarget) as ProgressBar
                val textWalkTarget = findViewById(R.id.textViewProgressWalkingTarget) as TextView
                item.isChecked = !item.isChecked
                if (item.isChecked) {
                    buttonWalkTarget.visibility = View.VISIBLE
                    progBarWalking.visibility = View.VISIBLE
                    textWalkTarget.visibility = View.VISIBLE
                } else {
                    buttonWalkTarget.visibility = View.GONE
                    progBarWalking.visibility = View.GONE
                    textWalkTarget.visibility = View.GONE
                }
                true
            }
            R.id.action_collect_distance_reset -> {
                alert("Distance walked data and target will be lost!", "Are you sure you want to reset distance walked?") {
                    positiveButton("Yes, I'm sure") {}
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
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Register BroadcastReceiver to track connection changes.
        val filter = IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)
        this.registerReceiver(receiver, filter)

        progBarDist = findViewById(R.id.progressBarWalkingTarget) as ProgressBar
        progTextDist = findViewById(R.id.textViewProgressWalkingTarget) as TextView

        doAsync {
            try {
                loadXmlFromNetwork("http://www.inf.ed.ac.uk/teaching/courses/cslp/data/songs/songs.xml")
            } catch (e: IOException) {
                "Unable to load content. Check your network connection"
            } catch (e: XmlPullParserException) {
                "Error parsing XML"
            }
            activityUiThread {
                for (song in songs) {
                    println(song)
                }
                for (i in 10..17) {
                    songsUnlocked.add(songs[i])
                }

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

        buttonPlay.setOnClickListener {

            /*val lm = this.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            var gpsEnabled = false
            try {
                gpsEnabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
            } catch (exception: Exception) {}

            if (!gpsEnabled) {
                alert("Location services are required for the Songle map, please turn them on!") {
                    positiveButton("OK") {}
                }.show()
            } else {*/

                if (selectedDifficulty != null) {
                    val intent = Intent(this, MapsActivity::class.java)
                    intent.putExtra("DIFFICULTY", selectedDifficulty!!)
                    intent.putParcelableArrayListExtra("SONGS", ArrayList(songs))
                    intent.putExtra("SONGTOPLAY", songToPlayIndexString)
                    startActivityForResult(intent, 1)
                } else {
                    selector("Please select a difficulty", difficulties, { _, i ->
                        selectedDifficulty = (5-i)
                        textViewShowDiff.text = "Current Difficulty: ${difficulties[i]}"
                        val intent = Intent(this, MapsActivity::class.java)
                        intent.putExtra("DIFFICULTY", selectedDifficulty!!)
                        intent.putParcelableArrayListExtra("SONGS", ArrayList(songs))
                        intent.putExtra("SONGTOPLAY", songToPlayIndexString)
                        startActivityForResult(intent, 1)
                    })
                }
            //}
        }

        buttonSelectDifficulty.setOnClickListener {
            selector("Please select a difficulty", difficulties, { _, i ->
                selectedDifficulty = (5-i)
                textViewShowDiff.text = "Current Difficulty: ${difficulties[i]}"
            })
        }

        val progBarSongs = findViewById(R.id.progressBar) as ProgressBar
        val progTextSongs = findViewById(R.id.textViewProgress) as TextView
        progBarSongs.max = 18
        progBarSongs.progress = 7
        progTextSongs.text = "7/18 Songs Unlocked"

        if (walkingTarget != 0) {
            progBarDist.max = walkingTarget
            if (distanceWalked < walkingTarget) {
                progBarDist.progress = distanceWalked
                distanceWalkedWithUnit = if (distanceWalked < 1000) {
                    "${distanceWalked}m"
                } else {
                    "${distanceWalked / 1000}km"
                }
                progTextDist.text = "You have walked $distanceWalkedWithUnit of your $walkingTargetWithUnit target!"
            } else {
                progBarDist.progress = walkingTarget
                progTextDist.text = "Congratulations you've reached your walking target of $walkingTarget, set a new one?"
            }
        }

        progBarDist.max = 1000
        progBarDist.progress = 420
        progTextDist.text = "420m walked of 1km target!\n2.5km total walked while playing Songle!"

        buttonSetTarget.setOnClickListener {
            val alert = AlertDialog.Builder(this)
            alert.setTitle("Set a new walking target in metres")
            val input = EditText(this)
            input.inputType = InputType.TYPE_CLASS_NUMBER
            input.setRawInputType(Configuration.KEYBOARD_12KEY)
            alert.setView(input)
            alert.setPositiveButton("Set", { dialog, whichButton ->
                walkingTarget = input.text.toString().toInt()
                walkingTargetWithUnit = if (walkingTarget < 1000) {
                    "${walkingTarget}m"
                } else {
                    "${BigDecimal(walkingTarget.toDouble()/1000).setScale(2, BigDecimal.ROUND_HALF_UP)}km"
                }
                progBarDist.max = walkingTarget
                progTextDist.text = "You have walked $distanceWalkedWithUnit of your $walkingTargetWithUnit target!"
            })
            alert.setNegativeButton("Cancel", { dialog, whichButton ->

            })
            alert.show()
        }


        buttonSongsUnlocked.setOnClickListener {
            val intent = Intent(this, SongsUnlockedActivity::class.java)
            intent.putParcelableArrayListExtra("SONGS", ArrayList(songs))
            intent.putParcelableArrayListExtra("SONGSUNLOCKED", songsUnlocked)
            startActivity(intent)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent) {
        if (requestCode == 1) {
            if (resultCode == Activity.RESULT_OK) {
                val distToAdd: Int = data.getIntExtra("RETURNDIST", 0)
                distanceWalked += distToAdd
                if (distanceWalked < walkingTarget) {
                    progBarDist.progress = distanceWalked
                    distanceWalkedWithUnit = if (distanceWalked < 1000) {
                        "${distanceWalked}m"
                    } else {
                        "${distanceWalked / 1000}km"
                    }
                    progTextDist.text = "You have walked $distanceWalkedWithUnit of your $walkingTargetWithUnit target!"
                }
                toast("Distance walked updated")
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