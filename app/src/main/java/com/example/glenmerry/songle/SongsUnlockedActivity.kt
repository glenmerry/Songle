package com.example.glenmerry.songle

import android.app.Activity
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

    private lateinit var songs: ArrayList<Song>
    private lateinit var songsUnlocked: ArrayList<Song>
    private var artistAndTitles = ArrayList<String>()
    private lateinit var indexInSongs: ArrayList<Int>
    private var favourites = arrayListOf<Song>()

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_songs_unlocked, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressed()
            return true
        } else if (item.itemId == R.id.action_show_favourites) {
            if (supportActionBar!!.title == "Songs Unlocked") {
                supportActionBar!!.title = "Favourite Songs"
                item.icon = ContextCompat.getDrawable(this, R.drawable.ic_favorite_white_24px)
                val artistAndTitlesFav = arrayListOf<String>()
                val indexInSongsFav = ArrayList<Int>()
                for (fav in favourites) {
                    artistAndTitlesFav.add("${fav.artist} - ${fav.title}")
                    for (song in songs) {
                        if (song.title == fav.title) {
                            indexInSongsFav.add(songs.indexOf(song))
                        }
                    }
                }
                showList(artistAndTitlesFav, indexInSongsFav)
            } else {
                supportActionBar!!.title = "Songs Unlocked"
                item.icon = ContextCompat.getDrawable(this, R.drawable.ic_favorite_border_white_24px)
                showList(artistAndTitles, indexInSongs)
            }
            return true
        }
        return false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_songs_unlocked)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)

        songs = intent.extras.getParcelableArrayList("SONGS")
        songsUnlocked = intent.extras.getParcelableArrayList("SONGSUNLOCKED")
        indexInSongs = arrayListOf()

        for (i in 6..9) {
            favourites.add(songs[i])
        }

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
        val adapter = ArrayAdapter<String>(this, R.layout.songs_unlocked_list_item, artistAndTitles)
        listView.adapter = adapter
        val image: Drawable = this.resources.getDrawable( R.drawable.ic_lock_open_white_24px)
        val textView = adapter.getView(1, null, listView) as TextView
        textView.setCompoundDrawablesWithIntrinsicBounds(null, null, image, null)
        listView.onItemClickListener = AdapterView.OnItemClickListener { adapterView, view, i, l ->
            if (artistAndTitles[i] != "\uD83D\uDD12") {
                val intent = Intent(this, SongDetailActivity::class.java)
                intent.putExtra("SONG", songs[indexInSongs[i]])
                intent.putExtra("FAVOURITES", favourites)
                startActivityForResult(intent, 1)
            } else {
                toast("Song Locked!")
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent) {
        if (requestCode == 1) {
            if (resultCode == Activity.RESULT_OK) {
                val newFavourites: ArrayList<Song> = data.getParcelableArrayListExtra("RETURNFAV")
                if (newFavourites != favourites) {
                    favourites = newFavourites
                    toast("favourites updated!")
                    supportActionBar!!.title = "Songs Unlocked"
                    showList(artistAndTitles, indexInSongs)
                }
            }
        }
    }
}