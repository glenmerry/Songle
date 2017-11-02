package com.example.glenmerry.songle

import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.Menu
import android.view.MenuItem
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import kotlinx.android.synthetic.main.activity_songs_found.*
import org.jetbrains.anko.toast

class SongsFoundActivity : AppCompatActivity() {

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_songs_found, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressed()
            return true
        } else if (item.itemId == R.id.action_show_favourites) {
            toast("show favourites")
            return true
        }
        return false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_songs_found)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)

        val songs: ArrayList<Song> = intent.extras.getParcelableArrayList("SONGS")
        val songsFound: ArrayList<Song> = intent.extras.getParcelableArrayList("SONGSFOUND")

        val artistAndTitles = arrayListOf<String>()
        for (song in songs) {
            if (songsFound.contains(song)) {
                artistAndTitles.add("${song.artist} - ${song.title}")
            } else {
                artistAndTitles.add("\uD83D\uDD12")
            }
        }

        val listView = findViewById(R.id.list) as ListView
        val adapter = ArrayAdapter<String>(this, R.layout.songs_found_list_item, artistAndTitles)
        listView.adapter = adapter
       // listView.adapter.getChildAt(1).setCompoundDrawablesWithIntrinsicBounds(null, null, null, null)
        val image: Drawable = this.getResources().getDrawable( R.drawable.ic_lock_open_white_24px)
        val textView = adapter.getView(1, null, listView) as TextView
        textView.setCompoundDrawablesWithIntrinsicBounds(null, null, image, null)
        listView.onItemClickListener = AdapterView.OnItemClickListener { adapterView, view, i, l ->
            if (artistAndTitles[i] != "\uD83D\uDD12") {
                val intent = Intent(this, SongDetailActivity::class.java)
                intent.putExtra("SONG", songs[i])
                startActivity(intent)
            } else {
                toast("Song Locked!")
            }
        }

    }
}

