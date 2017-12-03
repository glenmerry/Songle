package com.example.glenmerry.songle

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.text.Html
import android.view.MenuItem
import kotlinx.android.synthetic.main.activity_help.*

class HelpActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_help)

        textViewHelp.text = Html.fromHtml("<b>What is Songle?</b><br/><br /><small>" +
                "Songle is a location-based puzzle game where you can walk through your favourite songs! " +
                "Collect words from well-known lyrics in order to guess the song, while exploring your local area!<br/><br/>" +
                "<b></small>How do I play?<br/><br/><small></b>" +
                "Tap the 'Play' button to enter the map view. You will see placemarks which represent the words on the map, " +
                "once you're within 10 metres of one, collect it, by" +
                " tapping the 'Collect' button at the bottom of the screen.<br/><br/>" +
                "<b></small>What difficulty should I choose?<br/><br/><small></b>Well that's up to you, but here's what they mean:<br/><br/>" +
                "<b>Beginner:</b><br/>Words classified as boring, not boring, interesting or very interesting<br/>All words are displayed<br/><br/>" +
                "<b>Easy:</b><br/>Words classified as boring, not boring or interesting<br/>All words are displayed<br/><br/><b>" +
                "Medium:</b><br/>Words classified as boring, not boring or interesting<br/>Only 75% of words are displayed<br/><br/><b>" +
                "Hard:</b><br/>Words classified as boring or not boring<br/>Only 50% of words are displayed<br/><br/><b>" +
                "Impossible:</b><br/>Words unclassified<br/>Only 25% of words are displayed" +
                "<br/><br/><b></small>How do I see the words I've collected?<br/><br/><small></b>" +
                "Tap the 'Words Collected' button at the top of the map, to see how your words fit into the song.<br/><br/>" +
                "<b></small>How do I see what songs I've unlocked?<small></b>" +
                "<br/><br/>Once you've unlocked some songs, press 'Songs Unlocked' to see a list of them." +
                "<br/><br/><b></small>How do you know how far I've walked!?<small></b><br/><br/>" +
                "Songle tracks your GPS location as you walk around the map, so you can keep track." +
                "</small><br/><br/><b>I really can't work out what the song is, can you help?</b><br/><br/><small>" +
                "Yes! Once you've tried guessing three times, you'll see a 'hint' option above the map (it's a lightbulb...)" +
                " which will give you a free word of the most interesting type available. Once the hint feature appears, you " +
                "can use it as many times as you like for that song.<br/><br/>If you're still stuck you can press the " +
                "'skip' button, which will move onto the next song. You wont be given that song again until " +
                "there are no other un-skipped songs left."

        , Html.FROM_HTML_MODE_LEGACY)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when {
        item.itemId == android.R.id.home -> {
            // If back option selected, return to Main Activity
            onBackPressed()
            true
        }
        else -> false
    }
}
