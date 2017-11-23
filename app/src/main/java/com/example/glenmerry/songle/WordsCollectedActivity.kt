package com.example.glenmerry.songle

import android.app.Activity
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
import java.io.Serializable
import java.net.URL

class WordsCollectedActivity : AppCompatActivity() {

    private val wordsFound = HashMap<String, String>()
    private var songToPlayIndexString = "01"
    private var guessCount:Int = 0
    private var wordsCollected = arrayListOf<String>()
    private var wordsWithPos = HashMap<String, String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_words_found)

        songToPlayIndexString = intent.extras.getString("songToPlay")
        println("Song name >>>>> ${songs[songToPlayIndexString.toInt()-1].title}")
        guessCount = intent.extras.getInt("guessCount")
        println("Guesses made >>>>> $guessCount ")
        wordsCollected = intent.extras.getStringArrayList("wordsCollected")
        println("Words collected >>>>> $wordsCollected")
        wordsWithPos = intent.extras.getSerializable("wordsWithPos") as HashMap<String, String>
        println("Words with positions >>>>> $wordsWithPos")

        wordsCollected
                .filter { wordsWithPos.containsKey(it) }
                .forEach { wordsFound.put(it, wordsWithPos[it]!!) }
        println("Words Found >>>>> $wordsFound")

        doAsync {
            val url = URL(("http://www.inf.ed.ac.uk/teaching/courses/cslp/data/songs/$songToPlayIndexString/lyrics.txt"))
            val lyrics = url.readText()

            activityUiThread {
                val lines = lyrics.split('\n')
                val lyricsLines = HashMap<Int, String>()
                for (i in lines.subList(0, lines.size-1).indices) {
                    lyricsLines.put(i, lines[i])
                }

                println("Lyrics lines >>>>> $lyricsLines")

                val regex = Regex("[a-zA-Z0-9]")
                val blockedOut = lyrics.replace(regex, "█")

                val blockedOutLines = blockedOut.split("\n").toCollection(ArrayList())

                println("Blocked Out Lines >>>>> $blockedOutLines")

                for (word in wordsFound) {
                    val oldLine = blockedOutLines[word.key.substringBefore(':').toInt()-1]
                    println("Old line >>>>> $oldLine")

                    val wordStartIndices = HashMap<Int, Int>()
                    var wordCount = 1
                    wordStartIndices.put(1, 0)

                    for (i in 1 until oldLine.length-1) {
                        if (oldLine[i] == ' ') {
                            wordCount++
                            wordStartIndices.put(wordCount, i)
                        }
                    }

                    val wordAddStartIndex = wordStartIndices[word.key.substringAfter(':').toInt()]
                    var wordAddEndIndex: Int = -1

                    for (i in wordAddStartIndex!!+1 until oldLine.length) {
                        if (oldLine[i] == ' ') {
                            println("space at index $i")
                            wordAddEndIndex = i-1
                            break
                        }
                    }

                    println("Word start index is $wordAddStartIndex and word end index $wordAddEndIndex")

                    val newLine = StringBuilder()

                    for (i in 0 until wordAddStartIndex) {
                        newLine.append(oldLine[i])
                    }

                    newLine.append("${word.value}")

                    if (wordAddEndIndex != -1) {
                        for (i in wordAddEndIndex+1 until oldLine.length) {
                            newLine.append(oldLine[i])
                        }
                    }


                    blockedOutLines[word.key.substringBefore(':').toInt()-1] = newLine.toString()
                }

                val newLyrics = StringBuilder()
                for (line in blockedOutLines) {
                    newLyrics.append("$line\n")
                }

                textViewWords.text = newLyrics

            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_lyrics_found, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when {
        item.itemId == android.R.id.home -> {
            onBackPressed()
            true
        }
        item.itemId == R.id.action_guess -> {
            makeGuess()
            true
        }
        else -> false
    }

    override fun onBackPressed() {
        val intent = Intent()
        intent.putExtra("returnGuessCount", guessCount)
        setResult(Activity.RESULT_OK, intent)
        finish()
    }

    private fun makeGuess() {
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
                    intent.putExtra("song", songs[songToPlayIndexString.toInt()])
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
                guessCount++
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
