package com.example.glenmerry.songle

import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import kotlinx.android.synthetic.main.activity_song_detail.*
import android.content.Intent
import android.net.Uri
import android.text.Html
import android.text.method.ScrollingMovementMethod
import android.view.Menu
import android.view.MenuItem
import java.net.URL
import org.jetbrains.anko.activityUiThread
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.toast

class SongDetailActivity : AppCompatActivity() {

    lateinit var song: Song

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_song_detail, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressed()
            return true
        } else if (item.itemId == R.id.action_favorite) {
            toast("Marked as favourite")
            return true
        } else if (item.itemId == R.id.action_play)
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(song.link)))
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

        doAsync {
            val url = URL(("http://www.inf.ed.ac.uk/teaching/courses/cslp/data/songs/${song.number}/lyrics.txt"))
            val lyrics = url.readText()

            activityUiThread {
                textViewLyrics.text = lyrics
            }
        }
    }
}
