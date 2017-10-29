package com.example.glenmerry.songle

import android.content.Context
import android.graphics.Typeface
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.BaseExpandableListAdapter
import android.widget.ExpandableListView
import android.widget.TextView

class LyricsFoundActivity : AppCompatActivity() {

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressed()
            return true
        }
        return false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lyrics_found)

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
        return listDataHeader
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