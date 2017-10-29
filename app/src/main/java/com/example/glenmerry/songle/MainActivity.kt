package com.example.glenmerry.songle

import android.content.*
import android.location.LocationManager
import android.net.ConnectivityManager
import android.os.AsyncTask
import android.os.Bundle
import android.os.Parcelable
import android.support.design.widget.Snackbar
import android.support.v7.app.AppCompatActivity
import android.util.Xml
import kotlinx.android.synthetic.main.content_main.*
import org.jetbrains.anko.selector
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import android.os.Parcel
import org.jetbrains.anko.alert
import java.util.*

data class Song(val number: String, val artist: String, val title: String, val link: String) : Parcelable {

    constructor(source: Parcel): this(source.readString(), source.readString(), source.readString(), source.readString())

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel?, flags: Int) {
        dest?.writeString(this.number)
        dest?.writeString(this.artist)
        dest?.writeString(this.title)
        dest?.writeString(this.link)
    }

    companion object {
        @JvmField final val CREATOR: Parcelable.Creator<Song> = object : Parcelable.Creator<Song> {
            override fun createFromParcel(source: Parcel): Song{
                return Song(source)
            }
            override fun newArray(size: Int): Array<Song?> {
                return arrayOfNulls(size)
            }
        }
    }
}

var songs = listOf<Song>()
val songsFound: ArrayList<Song> = ArrayList()
var songToPlayIndexString = "01"

class MainActivity : AppCompatActivity() {

    // The BroadcastReceiver that tracks network connectivity changes.
    private var receiver = NetworkReceiver()
    private var connectionLost = false
    private var selectedDifficulty: Int? = null
    private val difficulties = listOf("Beginner", "Easy", "Medium", "Hard", "Impossible")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val distWalked = 1.2
        val distWalkedUnit = "km"
        textViewDistance.text = "You have walked $distWalked$distWalkedUnit while playing Songle!"

        // Register BroadcastReceiver to track connection changes.
        val filter = IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)
        this.registerReceiver(receiver, filter)

        buttonPlay.setOnClickListener {

            val lm = this.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            var gpsEnabled = false
            try {
                gpsEnabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
            } catch (exception: Exception) {}

            if (!gpsEnabled) {
                alert("Location services are required for the Songle map, please turn them on!") {
                    positiveButton("OK") {}
                }.show()
            } else {
                if (selectedDifficulty != null) {
                    val intent = Intent(this, MapsActivity::class.java)
                    intent.putExtra("DIFFICULTY", selectedDifficulty!!)
                    intent.putParcelableArrayListExtra("SONGS", ArrayList(songs))
                    intent.putExtra("SONGTOPLAY", songToPlayIndexString)
                    startActivity(intent)
                } else {
                    selector("Please select a difficulty", difficulties, { _, i ->
                        selectedDifficulty = (5-i)
                        textViewShowDiff.text = "Current Difficulty: ${difficulties[i]}"
                        val intent = Intent(this, MapsActivity::class.java)
                        intent.putExtra("DIFFICULTY", selectedDifficulty!!)
                        intent.putParcelableArrayListExtra("SONGS", ArrayList(songs))
                        intent.putExtra("SONGTOPLAY", songToPlayIndexString)
                        startActivity(intent)
                    })
                }
            }
        }

        buttonSelectDifficulty.setOnClickListener {
            selector("Please select a difficulty", difficulties, { _, i ->
                selectedDifficulty = (5-i)
                textViewShowDiff.text = "Current Difficulty: ${difficulties[i]}"
            })
        }

        DownloadXmlTask().execute("http://www.inf.ed.ac.uk/teaching/courses/cslp/data/songs/songs.xml")

        buttonSongsFound.setOnClickListener {

            val intent = Intent(this, SongsFoundActivity::class.java)
            intent.putParcelableArrayListExtra("SONGS", ArrayList(songs))
            intent.putParcelableArrayListExtra("SONGSFOUND", songsFound)
            startActivity(intent)
        }
    }

    private inner class NetworkReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val connMgr = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val networkInfo = connMgr.activeNetworkInfo

            if (networkInfo != null) {
                // Network is available
                if (connectionLost == true) {
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
}

fun randomSongIndex(from: Int, to: Int): Int {
    val random = Random()
    return random.nextInt(to - from) + from
}

interface DownloadCompleteListener {
    fun downloadComplete()
}

class DownloadXmlTask : AsyncTask<String, Void, String>(){

    override fun doInBackground(vararg urls: String): String {
        return try {
            loadXmlFromNetwork(urls[0])
        } catch (e: IOException) {
            "Unable to load content. Check your network connection"
        } catch (e: XmlPullParserException) {
            "Error parsing XML"
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
        // Also available: HttpsURLConnection

        conn.readTimeout = 10000 // milliseconds
        conn.connectTimeout = 15000 // milliseconds
        conn.requestMethod = "GET"
        conn.doInput = true

        // Starts the query
        conn.connect()
        return conn.inputStream
    }

    override fun onPostExecute(result: String) {
        super.onPostExecute(result)
        for (song in songs) {
            println(song)
        }
        for (i in 0..8) {
            songsFound.add(songs[i])
        }

        var songToPlayIndex = randomSongIndex(0, songs.size)
        while (songsFound.contains(songs[songToPlayIndex])) {
            songToPlayIndex = randomSongIndex(0, songs.size)
        }

        if (songToPlayIndex < 10) {
            songToPlayIndexString = "0${songToPlayIndex}"
        } else {
            songToPlayIndexString = songToPlayIndex.toString()
        }

    }
}

class SongListXMLParser {
    // We don't use namespaces
    private val ns: String? = null

    @Throws(XmlPullParserException::class, IOException::class)
    fun parse(input: InputStream): List<Song> {
        input.use {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(input, null)
            parser.nextTag()
            return readFeed(parser)
        }
    }

    @Throws(XmlPullParserException::class, IOException::class)
    private fun readFeed(parser: XmlPullParser): List<Song> {
        val entries = ArrayList<Song>()
        parser.require(XmlPullParser.START_TAG, ns, "Songs")
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) {
                continue
            }
            // Starts by looking for the entry tag
            if (parser.name == "Song") {
                entries.add(readEntry(parser))
            } else {
                skip(parser)
            }
        }
        return entries
    }

    @Throws(XmlPullParserException::class, IOException::class)
    private fun readEntry(parser: XmlPullParser): Song {
        parser.require(XmlPullParser.START_TAG, ns, "Song")
        var number = ""
        var artist = ""
        var title = ""
        var link = ""
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG)
                continue
            when (parser.name) {
                "Number" -> number = readNumber(parser)
                "Artist" -> artist = readArtist(parser)
                "Title" -> title = readTitle(parser)
                "Link" -> link = readLink(parser)
                else -> skip(parser)
            }
        }
        return Song(number, artist, title, link)
    }

    @Throws(IOException::class, XmlPullParserException::class)
    private fun readNumber(parser: XmlPullParser): String {
        parser.require(XmlPullParser.START_TAG, ns, "Number")
        val number = readText(parser)
        parser.require(XmlPullParser.END_TAG, ns, "Number")
        return number
    }

    @Throws(IOException::class, XmlPullParserException::class)
    private fun readArtist(parser: XmlPullParser): String {
        parser.require(XmlPullParser.START_TAG, ns, "Artist")
        val artist = readText(parser)
        parser.require(XmlPullParser.END_TAG, ns, "Artist")
        return artist
    }

    @Throws(IOException::class, XmlPullParserException::class)
    private fun readTitle(parser: XmlPullParser): String {
        parser.require(XmlPullParser.START_TAG, ns, "Title")
        val title = readText(parser)
        parser.require(XmlPullParser.END_TAG, ns, "Title")
        return title
    }

    @Throws(IOException::class, XmlPullParserException::class)
    private fun readLink(parser: XmlPullParser): String {
        parser.require(XmlPullParser.START_TAG, ns, "Link")
        val link = readText(parser)
        parser.require(XmlPullParser.END_TAG, ns, "Link")
        return link
    }

    @Throws(IOException::class, XmlPullParserException::class)
    private fun readText(parser: XmlPullParser): String {
        var result = ""
        if (parser.next() == XmlPullParser.TEXT) {
            result = parser.text
            parser.nextTag()
        }
        return result
    }

    @Throws(XmlPullParserException::class, IOException::class)
    private fun skip(parser: XmlPullParser) {
        if (parser.eventType != XmlPullParser.START_TAG) {
            throw IllegalStateException()
        }
        var depth = 1
        while (depth != 0) {
            when (parser.next()) {
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.START_TAG -> depth++
            }
        }
    }
}