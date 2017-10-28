package com.example.glenmerry.songle

import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.Toolbar
import org.jetbrains.anko.toast

class SongsFoundActivity : AppCompatActivity() {

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressed()
            return true
        }
        return false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_songs_found)
    //    supportActionBar!!.setDisplayShowTitleEnabled(false)
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
        val adapter = ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, artistAndTitles)
        listView.adapter = adapter
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

