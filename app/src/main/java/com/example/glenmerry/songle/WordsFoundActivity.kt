package com.example.glenmerry.songle

import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.text.InputType
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import kotlinx.android.synthetic.main.activity_words_found.*
import org.jetbrains.anko.activityUiThread
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.toast
import java.net.URL

class WordsFoundActivity : AppCompatActivity() {

    private val wordsFound = HashMap<String, String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_words_found)

        /*wordsFound.put("13:0", "Mama")
        wordsFound.put("28:8", "truth")
        wordsFound.put("3:4", "landslide")*/


        doAsync {
            val url = URL(("http://www.inf.ed.ac.uk/teaching/courses/cslp/data/songs/01/lyrics.txt"))
            val lyrics = url.readText()

            activityUiThread {
                val lines = lyrics.split('\n')
                val lyricsLines = HashMap<Int, String>()
                for (i in lines.subList(0, lines.size-1).indices) {
                    lyricsLines.put(i, lines[i])
                }

                val regex = Regex("[a-zA-Z0-9]")
                val blockedOut = lyrics.replace(regex, "\u2588")

                var blockedOutLines = blockedOut.split("\n").toCollection(ArrayList())

                for (word in wordsFound) {
                    val oldLine = blockedOutLines[word.key.substringBefore(':').toInt()-1]
                    var wordArray = oldLine.split(Regex("[^a-zA-Z0-9█]")).toCollection(ArrayList())
                    println(wordArray)
                    wordArray[word.key.substringAfter(':').toInt()] = word.value
                    val newLine = StringBuilder()
                    println(wordArray.indices)
                    for (i in 0..wordArray.size-2) {
                        if (i == word.key.substringAfter(':').toInt()-1) {
                            println("i $i word index: ${word.key.substringAfter(':').toInt()-1}")
                            newLine.append("${word.value} ")
                            println("append real word")
                            println(newLine.toString())
                        } else {
                            newLine.append("${wordArray[i]} ")
                            println("append blocks")
                            println(newLine.toString())
                        }
                    }
                    newLine.append(lines[word.key.substringBefore(':').toInt()-1].last())
                    blockedOutLines[word.key.substringBefore(':').toInt()-1] = newLine.toString()
                }

                val newLyrics = StringBuilder()
                for (line in blockedOutLines) {
                    newLyrics.append("$line\n")
                }

                textViewWords.text = blockedOut

            }
        }
    }

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
                    val sharingIntent = Intent(Intent.ACTION_SEND)
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
}
