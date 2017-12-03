package com.example.glenmerry.songle

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
import kotlinx.android.synthetic.main.activity_song_detail.*
import kotlinx.android.synthetic.main.titlebar.view.*
import org.jetbrains.anko.activityUiThread
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.toast
import java.net.URL

class SongDetailActivity : AppCompatActivity() {

    private lateinit var song: Song
    private var receiver = NetworkReceiver()
    private var connectionLost = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_song_detail)

        // Get song to display from intent extras
        song = intent.extras.getParcelable("song")

        // Add back button to action bar
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)

        // If song title short enough, show in action bar subtitle, with artist as title
        if (song.title.length <= 20) {
            title = song.artist
            supportActionBar!!.subtitle = song.title
        } else {
            // If song title too long, show artist and title as marquee text
            title = "${song.artist} - ${song.title}"
            supportActionBar!!.setDisplayShowCustomEnabled(true)
            supportActionBar!!.setDisplayShowTitleEnabled(false)
            val inflater = LayoutInflater.from(this)
            val v = inflater.inflate(R.layout.titlebar, null)
            (v.title as TextView).text = this.title
            supportActionBar!!.customView = v
        }

        // If song is a favourite, set fab icon set to full image, otherwise just the outline icon
        if (favourites.contains(song)) {
            fav_fab.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_favorite_white_24px))
        } else {
            fav_fab.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_favorite_border_white_24px))
        }

        // When fab pressed, add to or remove from favourites depending on whether it is favourite or not, and invert icon
        fav_fab.setOnClickListener {
            if (favourites.contains(song)) {
                toast("${song.title} unmarked as favourite")
                favourites.remove(song)
                fav_fab.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_favorite_border_white_24px))
            } else {
                toast("${song.title} marked as favourite")
                favourites.add(song)
                fav_fab.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_favorite_white_24px))
            }
        }

        downloadLyrics()
    }

    override fun onStart() {
        super.onStart()

        // Register BroadcastReceiver to track connection changes.
        val filter = IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)
        this.registerReceiver(receiver, filter)
    }

    private fun downloadLyrics() {
        // Download song lyrics asynchronously
        doAsync {
            val url = URL(("http://www.inf.ed.ac.uk/teaching/courses/cslp/data/songs/${song.number}/lyrics.txt"))
            val lyrics = url.readText()

            activityUiThread {
                // Add song lyrics to text view
                textViewLyrics.text = lyrics
            }
        }
    }

    private inner class NetworkReceiver: BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val connMgr = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val networkInfo = connMgr.activeNetworkInfo

            if (networkInfo != null) {
                // Network is available - if connection was previously lost, show "Connected" snackbar
                if (connectionLost) {
                    Snackbar.make(fav_fab,"Connected", Snackbar.LENGTH_SHORT).show()
                    connectionLost = false

                    // If lyrics did not download because of connection loss, attempt re-download
                    if (textViewLyrics.text.isEmpty()) {
                        downloadLyrics()
                    }
                }
            } else {
                // No network connection
                if (textViewLyrics.text.isEmpty()) {
                    val snackbar = Snackbar.make(fav_fab,"No internet connection available", Snackbar.LENGTH_INDEFINITE)
                    snackbar.show()
                }
                connectionLost = true
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate options menu
        menuInflater.inflate(R.menu.menu_song_detail, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when {
        item.itemId == android.R.id.home -> {
            // If back option selected, return to previous activity
            onBackPressed()
            true
        }
        item.itemId == R.id.action_share -> {
            // If share option selected, create and launch sharing intent
            val sharingIntent = Intent(android.content.Intent.ACTION_SEND)
            sharingIntent.type = "text/plain"
            sharingIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, "Songle")
            sharingIntent.putExtra(android.content.Intent.EXTRA_TEXT, "I unlocked ${song.title} by ${song.artist} on Songle!")
            startActivity(Intent.createChooser(sharingIntent, "Share via"))
            true
        }
        item.itemId == R.id.action_play -> {
            // If play option selected, create intent to view music video
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(song.link)))
            true
        }
        else -> false
    }

    override fun onBackPressed() {
        // On back pressed, return favourites back to Songs Unlocked Activity
        val intent = Intent()
        intent.putParcelableArrayListExtra("returnFavourites", favourites)
        setResult(Activity.RESULT_OK, intent)
        finish()
    }

    override fun onPause() {
        super.onPause()

        // If receiver is registered, unregister it
        try {
            unregisterReceiver(receiver)
        } catch(e: IllegalArgumentException) {
            println("Receiver not registered")
        }

        // All objects are from android.context.Context
        val settings = getSharedPreferences(prefsFile, Context.MODE_PRIVATE)

        // We need an Editor object to make preference changes.
        val editor = settings.edit()

        // Save song titles of favourites to shared preferences
        val titlesFav = favourites
                .map { it.title }
                .toSet()
        editor.putStringSet("storedFavourites", titlesFav)
        editor.apply()
    }
}
