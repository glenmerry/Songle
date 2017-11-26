package com.example.glenmerry.songle

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.text.InputType
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import kotlinx.android.synthetic.main.activity_song_detail.*
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
    private var connectionLost = false
    private var receiver = NetworkReceiver()

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

        processLyrics()

    }

    override fun onStart() {
        super.onStart()

        // Register BroadcastReceiver to track connection changes.
        val filter = IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)
        this.registerReceiver(receiver, filter)
    }

    private fun processLyrics() {
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

                    for (i in wordAddStartIndex!!+1 until oldLine.length-1) {
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

                    newLine.append(" ${word.value}")

                    if (wordAddEndIndex != -1) {
                        for (i in wordAddEndIndex+1 until oldLine.length) {
                            newLine.append(oldLine[i])
                        }
                    }

                    println("New line >>>>> $newLine")


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

    private inner class NetworkReceiver: BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val connMgr = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val networkInfo = connMgr.activeNetworkInfo

            if (networkInfo != null) {
                // Network is available
                if (connectionLost) {
                    val snackbar : Snackbar = Snackbar.make(textViewWords,"Connected", Snackbar.LENGTH_SHORT)
                    snackbar.show()
                    connectionLost = false
                    if (textViewWords.text.isEmpty()) {
                        processLyrics()
                    }
                }
            } else {
                // No network connection
                if (textViewWords.text.isEmpty()) {
                    val snackbar = Snackbar.make(textViewWords, "No internet connection available", Snackbar.LENGTH_INDEFINITE)
                    snackbar.show()
                    connectionLost = true
                }
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

        unregisterReceiver(receiver)

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
        editor.putInt("storedGuessCount", guessCount)
        editor.apply()
    }

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
                    guessCount = 0
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
