package com.example.glenmerry.songle

import android.app.Activity
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import kotlinx.android.synthetic.main.activity_song_detail.*
import android.content.Intent
import android.net.Uri
import android.text.method.ScrollingMovementMethod
import android.view.Menu
import android.view.MenuItem
import java.net.URL
import org.jetbrains.anko.activityUiThread
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.toast
import android.support.design.widget.FloatingActionButton
import android.support.v4.content.ContextCompat
import android.widget.ShareActionProvider
import android.widget.TextView
import android.view.LayoutInflater

class SongDetailActivity : AppCompatActivity() {

    private lateinit var song: Song

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_song_detail, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when {
        item.itemId == android.R.id.home -> {
            onBackPressed()
            true
        }
        item.itemId == R.id.action_share -> {
            val sharingIntent = Intent(android.content.Intent.ACTION_SEND)
            sharingIntent.type = "text/plain"
            sharingIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, "Songle")
            sharingIntent.putExtra(android.content.Intent.EXTRA_TEXT, "I unlocked ${song.title} by ${song.artist} on Songle!")
            startActivity(Intent.createChooser(sharingIntent, "Share via"))
            true
        }
        item.itemId == R.id.action_play -> {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(song.link)))
            true
        }
        else -> false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_song_detail)

        song = intent.extras.getParcelable("song")

        supportActionBar!!.setDisplayHomeAsUpEnabled(true)

        if (song.title.length <= 20) {
            title = song.artist
            supportActionBar!!.subtitle = song.title
        } else {
            title = "${song.artist} - ${song.title}"
            supportActionBar!!.setDisplayShowCustomEnabled(true)
            supportActionBar!!.setDisplayShowTitleEnabled(false)
            val inflator = LayoutInflater.from(this)
            val v = inflator.inflate(R.layout.titlebar, null)
            (v.findViewById(R.id.title) as TextView).text = this.title
            supportActionBar!!.customView = v

        }

        val fab = findViewById(R.id.fav_fab) as FloatingActionButton
        if (favourites.contains(song)) {
            fab.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_favorite_white_24px))
        } else {
            fab.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_favorite_border_white_24px))
        }

        fab.setOnClickListener {
            if (favourites.contains(song)) {
                toast("${song.title} unmarked as favourite")
                favourites.remove(song)
                fab.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_favorite_border_white_24px))
            } else {
                toast("${song.title} marked as favourite")
                favourites.add(song)
                fab.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_favorite_white_24px))
            }
        }

        doAsync {
            val url = URL(("http://www.inf.ed.ac.uk/teaching/courses/cslp/data/songs/${song.number}/lyrics.txt"))
            val lyrics = url.readText()

            activityUiThread {
                textViewLyrics.text = lyrics
            }
        }
    }

    override fun onBackPressed() {
        val intent = Intent()
        intent.putParcelableArrayListExtra("returnFavourites", favourites)
        setResult(Activity.RESULT_OK, intent)
        finish()
    }
}
