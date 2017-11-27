package com.example.glenmerry.songle

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.view.Menu
import android.view.MenuItem
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ListView
import kotlinx.android.synthetic.main.activity_songs_unlocked.*
import org.jetbrains.anko.toast

class SongsUnlockedActivity : AppCompatActivity() {

    private var artistAndTitles = ArrayList<String>()
    private var indexInSongs = ArrayList<Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_songs_unlocked)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)

        // Iterate through songs, if song is unlocked, add its artist and title to artistAndTitles,
        // if locked, add lock character
        for (i in songs.indices) {
            if (songsUnlocked.contains(songs[i])) {
                artistAndTitles.add("${songs[i].artist} - ${songs[i].title}")
            } else {
                artistAndTitles.add("\uD83D\uDD12")
            }
        }

        // Sort list of songs so that unlocked songs always appear above locked songs
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

        // After sorting, add index in original songs list to indexInSongs list so that data for
        // songs in sorted array can be easily found
        for (i in artistAndTitles.indices) {
            songs.indices
                    .filter { "${songs[it].artist} - ${songs[it].title}" == artistAndTitles[i] }
                    .forEach { indexInSongs.add(it) }
        }

        // Show artists and titles in list view
        showList(artistAndTitles, indexInSongs)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate options menu
        menuInflater.inflate(R.menu.menu_songs_unlocked, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when {
        item.itemId == android.R.id.home -> {
            // If back option selected, return to Main Activity
            onBackPressed()
            true
        }
        item.itemId == R.id.action_show_favourites -> {
            if (supportActionBar!!.title == "Unlocked Songs") {
                // Not currently showing favourites, so we switch to showing favourites
                // Set action bar title to Favorite Songs
                supportActionBar!!.title = "Favourite Songs"
                // Favourite icon changed to fully coloured version
                item.icon = ContextCompat.getDrawable(this, R.drawable.ic_favorite_white_24px)

                // Build list of artist and titles of favourites songs, and keep track of index in songs
                val artistAndTitlesFav = arrayListOf<String>()
                val indexInSongsFav = ArrayList<Int>()
                for (fav in favourites) {
                    if (!artistAndTitlesFav.contains("${fav.artist} - ${fav.title}")) {
                        artistAndTitlesFav.add("${fav.artist} - ${fav.title}")
                        songs
                                .filter { it.title == fav.title }
                                .mapTo(indexInSongsFav) { songs.indexOf(it) }
                    }
                }
                // Show favourites in list view
                showList(artistAndTitlesFav, indexInSongsFav)
            } else {
                // If currently showing favourites, switch to all songs
                // Set action bar title back to "Unlocked Songs"
                supportActionBar!!.title = "Unlocked Songs"
                // Switch favorite icon back to only outline
                item.icon = ContextCompat.getDrawable(this, R.drawable.ic_favorite_border_white_24px)

                // Show all songs in list view
                showList(artistAndTitles, indexInSongs)
            }
            true
        }
        else -> false
    }

    private fun showList(artistAndTitles: ArrayList<String>, indexInSongs: ArrayList<Int>) {
        // Create array adapter for list view
        val adapter = ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, artistAndTitles)
        list.adapter = adapter
        list.onItemClickListener = AdapterView.OnItemClickListener { _, _, i, _ ->
            if (artistAndTitles[i] != "\uD83D\uDD12") {
                // If song not locked, open song in Song Detail Activity
                val intent = Intent(this, SongDetailActivity::class.java)
                intent.putExtra("song", songs[indexInSongs[i]])
                intent.putExtra("favourites", favourites)
                startActivityForResult(intent, 1)
            } else {
                // If song locked, present toast message
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

        // Store titles of favourites in shared preferences
        val titlesFav = favourites
                .map { it.title }
                .toSet()
        editor.putStringSet("storedFavourites", titlesFav)
        editor.apply()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent) {
        // Returning from Song Detail Activity
        if (requestCode == 1) {
            if (resultCode == Activity.RESULT_OK) {
                // Get favourites from intent extras
                favourites = data.getParcelableArrayListExtra("returnFavourites")

                // If we are currently showing favourites, update in case of change
                if (supportActionBar!!.title == "Favourite Songs") {
                    val artistAndTitlesFav = arrayListOf<String>()
                    val indexInSongsFav = ArrayList<Int>()
                    for (fav in favourites) {
                        if (!artistAndTitlesFav.contains("${fav.artist} - ${fav.title}")) {
                            artistAndTitlesFav.add("${fav.artist} - ${fav.title}")
                            songs
                                    .filter { it.title == fav.title }
                                    .mapTo(indexInSongsFav) { songs.indexOf(it) }
                        }
                    }
                    showList(artistAndTitlesFav, indexInSongsFav)
                }
            }
        }
    }
}