package com.example.glenmerry.songle

import android.app.Activity
import android.content.Context
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
import java.net.URL

class WordsCollectedActivity : AppCompatActivity() {

    private val wordsFound = HashMap<String, String>()
    private var songToPlayIndexString = "01"
    private var guessCount:Int = 0
    private var wordsCollected = arrayListOf<String>()
    private var wordsWithPos = HashMap<String, String>()
    private var unlocked = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_words_found)
        if (unlocked) {
            onBackPressed()
        }

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

                    for (i in wordAddStartIndex!! until oldLine.length-1) {
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

    override fun onPause() {
        super.onPause()
        // All objects are from android.context.Context
        val settings = getSharedPreferences(prefsFile, Context.MODE_PRIVATE)

        // We need an Editor object to make preference changes.
        val editor = settings.edit()
        val titlesUnlocked = songsUnlocked
                .map { it.title }
                .toSet()
        editor.putStringSet("storedSongsUnlocked", titlesUnlocked)
        editor.putString("storedSongToPlayIndexString", songToPlayIndexString)
        editor.putStringSet("storedWordsCollected", wordsCollected.toSet())
        editor.apply()
    }

    /*private fun makeGuess() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Make a guess")
        builder.setMessage("Please input the song title")
        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_TEXT
        builder.setView(input)
        builder.setPositiveButton("Make Guess!") { dialog, which ->
            toast(songs[songToPlayIndexString.toInt()-1].title.toLowerCase())
            if (input.text.toString().toLowerCase() == songs[songToPlayIndexString.toInt()-1].title.toLowerCase()) {
                val builder2 = AlertDialog.Builder(this)
                builder2.setTitle("Nice one, you guessed correctly!")
                builder2.setMessage("View the full lyrics, share with your friends or move to the next song?")
                builder2.setPositiveButton("Next Song") { dialog, which ->
                    val intent = Intent(this, MapsActivity::class.java)
                    startActivity(intent)
                }
                builder2.setNegativeButton("View Lyrics") { dialog, which ->
                    val intent = Intent(this, SongDetailActivity::class.java)
                    intent.putExtra("song", songs[songToPlayIndexString.toInt()])
                    startActivity(intent)
                }
                builder2.setNeutralButton("Share") { dialog, which ->
                    val sharingIntent = Intent(Intent.ACTION_SEND)
                    sharingIntent.type = "text/plain"
                    sharingIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, "Songle")
                    sharingIntent.putExtra(android.content.Intent.EXTRA_TEXT, "I unlocked Bohemian Rhapsody by Queen on Songle!")
                    startActivity(Intent.createChooser(sharingIntent, "Share via"))
                }
                builder2.show()
            } else {
                val builder2 = AlertDialog.Builder(this)
                guessCount++
                builder2.setTitle("Sorry, that's not quite right")
                builder2.setMessage("Guess again?")
                builder2.setPositiveButton("Guess again") { dialog, which ->
                    makeGuess()
                }
                builder2.setNegativeButton("Back to found lyrics") { dialog, which ->
                    dialog.cancel()
                }
                builder2.show()
            }
        }
        builder.setNegativeButton("Cancel") { dialog, which -> dialog.cancel() }
        builder.show()
    }*/

    private fun makeGuess() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Make a guess")
        builder.setMessage("Please input the song title")
        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_TEXT
        builder.setView(input)
        builder.setPositiveButton("Make Guess!") { _, _ ->
            if (input.text.toString().toLowerCase() == songs[songToPlayIndexString.toInt()-1].title.toLowerCase()) {
                songsUnlocked.add(songs[songToPlayIndexString.toInt()-1])
                wordsCollected.clear()
                wordsWithPos.clear()
                val builderCorrect = AlertDialog.Builder(this)
                builderCorrect.setTitle("Nice one, you guessed correctly!")
                builderCorrect.setMessage("View the full lyrics, share with your friends or move to the next song?")
                builderCorrect.setPositiveButton("Next Song") { _, _ ->
                    unlocked = true
                    onBackPressed()
                }
                builderCorrect.setNegativeButton("View Lyrics") { _, _ ->
                    val intent = Intent(this, SongDetailActivity::class.java)
                    intent.putExtra("song", songs[songToPlayIndexString.toInt()-1])
                    startActivity(intent)
                }
                builderCorrect.setNeutralButton("Share") { _, _ ->
                    val sharingIntent = Intent(android.content.Intent.ACTION_SEND)
                    sharingIntent.type = "text/plain"
                    sharingIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, "Songle")
                    sharingIntent.putExtra(android.content.Intent.EXTRA_TEXT, "I unlocked ${songs[songToPlayIndexString.toInt()-1].title} by ${songs[songToPlayIndexString.toInt()-1].artist} on Songle!")
                    startActivity(Intent.createChooser(sharingIntent, "Share via"))
                }
                builderCorrect.setCancelable(false)
                builderCorrect.show()
            } else {
                guessCount++
                if (guessCount == 3) {
                    invalidateOptionsMenu()
                }
                val builderIncorrect = AlertDialog.Builder(this)
                builderIncorrect.setTitle("Sorry, that's not quite right")
                builderIncorrect.setMessage("Guess again?")
                builderIncorrect.setPositiveButton("Guess again") { _, _ ->
                    makeGuess()
                }
                builderIncorrect.setNegativeButton("Back to map") { dialog, _ ->
                    dialog.cancel()
                }
                builderIncorrect.show()
            }
        }
        builder.setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }
        builder.show()
    }

    override fun onBackPressed() {
        val intent = Intent()
        intent.putExtra("returnGuessCount", guessCount)
        intent.putExtra("returnUnlocked", unlocked)
        setResult(Activity.RESULT_OK, intent)
        finish()
    }
}
