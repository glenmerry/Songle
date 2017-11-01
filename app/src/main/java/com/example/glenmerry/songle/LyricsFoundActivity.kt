package com.example.glenmerry.songle

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.view.*
import android.widget.BaseExpandableListAdapter
import android.widget.ExpandableListView
import android.widget.TextView
import org.jetbrains.anko.toast
import android.text.InputType
import android.support.v7.app.AlertDialog
import android.widget.EditText

class LyricsFoundActivity : AppCompatActivity() {

    //lateinit var song: Song

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_lyrics_found, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressed()
            return true
        } else if (item.itemId == R.id.action_guess) {
            makeGuess()
            return true
        }
        return false
    }

    private fun makeGuess() {
        //toast(song.title.toLowerCase())
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Make a guess")
        builder.setMessage("Please input the song title")
        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_TEXT
        builder.setView(input)
        builder.setPositiveButton("Make Guess!") { dialog, which ->
            toast(songs[songToPlayIndexString.toInt()].title.toLowerCase())
            if (input.text.toString().toLowerCase() == songs[songToPlayIndexString.toInt()].title.toLowerCase()) {
                val builder = AlertDialog.Builder(this)
                builder.setTitle("Nice one, you guessed correctly!")
                builder.setMessage("View the full lyrics, share with your friends or move to the next song?")
                builder.setPositiveButton("Next Song") { dialog, which ->
                    val intent = Intent(this, MapsActivity::class.java)
                    startActivity(intent)
                }
                builder.setNegativeButton("View Lyrics") { dialog, which ->
                    val intent = Intent(this, SongDetailActivity::class.java)
                    intent.putExtra("SONG", songs[songToPlayIndexString.toInt()])
                    startActivity(intent)
                }
                builder.setNeutralButton("Share") { dialog, which ->
                    val sharingIntent = Intent(android.content.Intent.ACTION_SEND)
                    sharingIntent.type = "text/plain"
                    sharingIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, "Songle")
                    sharingIntent.putExtra(android.content.Intent.EXTRA_TEXT, "I unlocked Bohemian Rhapsody by Queen on Songle!")
                    startActivity(Intent.createChooser(sharingIntent, "Share via"))
                }
                builder.show()
            } else {
                val builder = AlertDialog.Builder(this)
                builder.setTitle("Sorry, that's not quite right")
                builder.setMessage("Guess again?")
                builder.setPositiveButton("Guess again") { dialog, which ->
                    makeGuess()
                }
                builder.setNegativeButton("Back to found lyrics") { dialog, which ->
                    dialog.cancel()
                }
                builder.show()
            }
        }
        builder.setNegativeButton("Cancel") { dialog, which -> dialog.cancel() }
        builder.show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lyrics_found)
        //song = intent.extras.getParcelable<Song>("SONG")

        val lyrics: HashMap<String, List<String>> = hashMapOf("very interesting" to listOf("Galileo", "Magnifico", "Bismillah"),
                "interesting" to listOf("poor", "silhoetto", "wind", "trigger"), "boring" to listOf("the", "and", "a"))

        println(lyrics.keys.toList())

        val listView = findViewById(R.id.expListView) as ExpandableListView
        val listAdapter = ExpandableListAdapter(this, lyrics.keys.toList(), lyrics)
        listView.setAdapter(listAdapter)
    }
}

class ExpandableListAdapter(private val context: Context, private val listDataHeader: List<String>, private val listHashMap: HashMap<String, List<String>>): BaseExpandableListAdapter() {

    override fun getGroupCount(): Int {
        return listDataHeader.size
    }

    override fun getChildrenCount(i: Int): Int {
        return listHashMap[listDataHeader[i]]!!.size
    }

    override fun getGroup(p0: Int): Any {
        return listDataHeader[p0]
    }

    override fun getChild(i: Int, j: Int): Any {
        return listHashMap[listDataHeader[i]]!![j]
    }

    override fun getGroupId(i: Int): Long {
        return i.toLong()
    }

    override fun getChildId(i: Int, j: Int): Long {
        return j.toLong()
    }

    override fun hasStableIds(): Boolean {
        return false
    }

    override fun getGroupView(i: Int, b: Boolean, viewIn: View?, viewGroup: ViewGroup?): View {
        val headerTitle = getGroup(i).toString()
        var view = viewIn
        if (view == null) {
            val inflator = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
            view = inflator.inflate(R.layout.list_group, null)
        }
        val expListGroup = view!!.findViewById(R.id.expListGroup) as TextView
        expListGroup.setTypeface(null, Typeface.BOLD)
        expListGroup.text = headerTitle
        return view
    }

    override fun getChildView(i: Int, j: Int, b: Boolean, viewIn: View?, viewGroup: ViewGroup?): View {
        val childText: String = getChild(i, j).toString()
        var view = viewIn
        if (view == null) {
            val inflator = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
            view = inflator.inflate(R.layout.list_item, null)
        }
        val expListItem = view!!.findViewById(R.id.expListItem) as TextView
        expListItem.text = childText
        return view
    }

    override fun isChildSelectable(p0: Int, p1: Int): Boolean {
        return true
    }
}