package com.example.glenmerry.songle

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
import android.support.v4.view.MenuItemCompat
import android.widget.ShareActionProvider


class SongDetailActivity : AppCompatActivity() {

    lateinit var song: Song
    lateinit var mShareActionProvider: ShareActionProvider

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_song_detail, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when {
            item.itemId == android.R.id.home -> {
                onBackPressed()
                return true
            }
            item.itemId == R.id.action_share -> {
                toast("share song")
                val sharingIntent = Intent(android.content.Intent.ACTION_SEND)
                sharingIntent.type = "text/plain"
                sharingIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, "Songle")
                sharingIntent.putExtra(android.content.Intent.EXTRA_TEXT, "I unlocked Bohemian Rhapsody by Queen on Songle!")
                startActivity(Intent.createChooser(sharingIntent, "Share via"))
                return true
            }
            item.itemId == R.id.action_play -> {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(song.link)))
                return true
            }
        }
        return false
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_song_detail)

        song = intent.extras.getParcelable<Song>("SONG")

        title = song.artist
        supportActionBar!!.subtitle = song.title
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)

        textViewLyrics.movementMethod = ScrollingMovementMethod()

        val fab = findViewById(R.id.fav_fab) as FloatingActionButton
        fab.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_favorite_white_24px))
        fab.setOnClickListener {
            toast("marked as favourite")
        }

        doAsync {
            val url = URL(("http://www.inf.ed.ac.uk/teaching/courses/cslp/data/songs/${song.number}/lyrics.txt"))
            val lyrics = url.readText()

            activityUiThread {
                textViewLyrics.text = lyrics
            }
        }
    }
}
