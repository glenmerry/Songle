package com.example.glenmerry.songle

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.view.Menu
import android.view.MenuItem
import android.widget.*
import org.jetbrains.anko.toast

class SongsUnlockedActivity : AppCompatActivity() {

    private lateinit var songsUnlocked: ArrayList<Song>
    private var artistAndTitles = ArrayList<String>()
    private lateinit var indexInSongs: ArrayList<Int>
   // private var favourites = arrayListOf<Song>()
    private lateinit var adapter: ArrayAdapter<String>

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_songs_unlocked, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when {
        item.itemId == android.R.id.home -> {
            onBackPressed()
            true
        }
        item.itemId == R.id.action_show_favourites -> {
            if (supportActionBar!!.title == "Songs Unlocked") {
                supportActionBar!!.title = "Favourite Songs"
                item.icon = ContextCompat.getDrawable(this, R.drawable.ic_favorite_white_24px)
                val artistAndTitlesFav = arrayListOf<String>()
                val indexInSongsFav = ArrayList<Int>()
                for (fav in favourites) {
                    artistAndTitlesFav.add("${fav.artist} - ${fav.title}")
                    songs
                            .filter { it.title == fav.title }
                            .mapTo(indexInSongsFav) { songs.indexOf(it) }
                }
                showList(artistAndTitlesFav, indexInSongsFav)
            } else {
                supportActionBar!!.title = "Songs Unlocked"
                item.icon = ContextCompat.getDrawable(this, R.drawable.ic_favorite_border_white_24px)
                showList(artistAndTitles, indexInSongs)
            }
            true
        }
        else -> false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_songs_unlocked)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)

        songsUnlocked = intent.extras.getParcelableArrayList("songsUnlocked")
        indexInSongs = arrayListOf()

        for (i in songs.indices) {
            if (songsUnlocked.contains(songs[i])) {
                artistAndTitles.add("${songs[i].artist} - ${songs[i].title}")
                indexInSongs.add(i)
            } else {
                artistAndTitles.add("\uD83D\uDD12")
                indexInSongs.add(i)
            }
        }
        val sortedArray = Array(artistAndTitles.size) {""}
        for (i in artistAndTitles.indices) {
            var start = 0
            var end = artistAndTitles.size-1
            for (j in artistAndTitles.indices) {
                if (artistAndTitles[j] == "\uD83D\uDD12") {
                    sortedArray[end--] = artistAndTitles[j]
                } else {
                    sortedArray[start++] = artistAndTitles[j]
                }
            }
        }
        artistAndTitles = sortedArray.toCollection(ArrayList())
        for (i in artistAndTitles.indices) {
            songs.indices
                    .filter { "${songs[it].artist} - ${songs[it].title}" == artistAndTitles[i] }
                    .forEach { indexInSongs[i] = it }
        }
        showList(artistAndTitles, indexInSongs)
    }

    private fun showList(artistAndTitles: ArrayList<String>, indexInSongs: ArrayList<Int>) {
        val listView = findViewById(R.id.list) as ListView
        val adapter = ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, artistAndTitles)
        listView.adapter = adapter
        listView.onItemClickListener = AdapterView.OnItemClickListener { adapterView, view, i, l ->
            if (artistAndTitles[i] != "\uD83D\uDD12") {
                val intent = Intent(this, SongDetailActivity::class.java)
                intent.putExtra("song", songs[indexInSongs[i]])
                intent.putExtra("favourites", favourites)
                startActivityForResult(intent, 1)
            } else {
                toast("Song Locked!")
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // All objects are from android.context.Context
        val settings = getSharedPreferences(prefsFile, Context.MODE_PRIVATE)

        // We need an Editor object to make preference changes.
        val editor = settings.edit()

        val titlesFav = favourites
                .map { it.title }
                .toSet()
        editor.putStringSet("storedFavourites", titlesFav)
        editor.apply()

        editor.apply()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent) {
        if (requestCode == 1) {
            if (resultCode == Activity.RESULT_OK) {
                favourites = data.getParcelableArrayListExtra("returnFavourites")
                if (supportActionBar!!.title == "Favourite Songs") {
                    val artistAndTitlesFav = arrayListOf<String>()
                    val indexInSongsFav = ArrayList<Int>()
                    for (fav in favourites) {
                        artistAndTitlesFav.add("${fav.artist} - ${fav.title}")
                        songs
                                .filter { it.title == fav.title }
                                .mapTo(indexInSongsFav) { songs.indexOf(it) }
                    }
                    showList(artistAndTitlesFav, indexInSongsFav)
                }
            }
        }
    }
}