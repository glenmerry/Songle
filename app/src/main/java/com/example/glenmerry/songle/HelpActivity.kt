package com.example.glenmerry.songle

import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.text.Html
import android.view.MenuItem
import kotlinx.android.synthetic.main.activity_help.*

class HelpActivity : AppCompatActivity() {

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when {
            item.itemId == android.R.id.home -> {
                onBackPressed()
                true
            }
            else -> false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_help)

        textViewHelp.text = Html.fromHtml("<b>What is Songle?</b><br /><br /><small>Songle is location based game where you can collect words from your favourite songs and use" +
                " them to guess what it is, while exploring your area! <br /><br />" + "<b></small>What difficulty should I choose?<br/><br/><small></b>" +
                "<b>Beginner:</b><br/>Words classified as boring, not boring, interesting or very interesting<br/>All words are displayed<br/><br/>" +
                "<b>Easy:</b><br/>Words classified as boring, not boring or interesting<br/>All words are displayed<br/><br/><b>" +
                "Medium:</b><br/>Words classified as boring, not boring or interesting<br/>Only 75% of words are displayed<br/><br/><b>" +
                "Hard:</b><br/>Words classified as boring or not boring<br/>Only 50% of words are displayed<br/><br/><b>" +
                "Impossible:</b><br/>Words unclassified<br/>Only 25% of words are displayed" +
                "<br/><br/><b></small>How do I see what songs I've unlocked?<small></b>" +
                "<br/><br/>Once you've unlocked some songs, press 'Songs Unlocked' to see them."



        )

    }
}
