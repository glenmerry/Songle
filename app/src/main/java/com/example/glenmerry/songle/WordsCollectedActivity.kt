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

class WordsCollectedActivity : AppCompatActivity() {

    private val wordsFound = HashMap<String, String>()
    private var songToPlayIndexString = String()
    private var guessCount:Int = 0
    private var wordsCollected = arrayListOf<String>()
    private var wordsWithPos = HashMap<String, String>()
    private var unlocked = false
    private var incorrectGuess: String? = null
    private var lyricLines = arrayListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_words_found)

        // If song is already unlocked, return to Main Activity
        if (unlocked) {
            onBackPressed()
        }

        // Get values from intent extras
        lyricLines = intent.extras.getStringArrayList("lyricLines")
        songToPlayIndexString = intent.extras.getString("songToPlay")
        guessCount = intent.extras.getInt("guessCount")
        wordsCollected = intent.extras.getStringArrayList("wordsCollected")
        wordsWithPos = intent.extras.getSerializable("wordsWithPos") as HashMap<String, String>

        // Add words collected and their positions to wordsFound Hash Map
        wordsCollected
                .filter { wordsWithPos.containsKey(it) }
                .forEach { wordsFound.put(it, wordsWithPos[it]!!) }

        // Download and process song lyrics
        processLyrics()
    }

    private fun processLyrics() {
        // Replace all alphanumeric character in lines of lyrics with 'full block' character
        val regex = Regex("[a-zA-Z0-9]")
        val blockedOutLines = arrayListOf<String>()
        lyricLines.mapTo(blockedOutLines) { it.replace(regex, "█") }

        // Iterate through all collected words to replace block character with actual word
        for (word in wordsFound) {

            // Get line number of word using first part of word identifier
            val oldLine = blockedOutLines[word.key.substringBefore(':').toInt()-1]

            // wordStartIndices HashMap stores start indices of each word in the line
            val wordStartIndices = HashMap<Int, Int>()
            var wordCount = 1
            wordStartIndices.put(1, 0)

            for (i in 1 until oldLine.length-1) {
                if (oldLine[i] == ' ') {
                    // if space then word starts at next index
                    wordCount++
                    wordStartIndices.put(wordCount, i+1)
                }
            }

            // Find start index of intended word using seconds part of word identifier
            val wordAddStartIndex = wordStartIndices[word.key.substringAfter(':').toInt()]
            var wordAddEndIndex: Int = -1

            // Iterate from start of word to end of line
            for (i in wordAddStartIndex!!+1 until oldLine.length-1) {
                if (oldLine[i] == ' ') {
                    // If space then word ends at previous index
                    wordAddEndIndex = i-1
                    break
                }
            }

            // StringBuilder to hold line string after full block removed from word
            val newLine = StringBuilder()

            // Add characters from old line until start of word being added
            for (i in 0 until wordAddStartIndex) {
                newLine.append(oldLine[i])
            }

            // Then append the unlocked word with its full block removed
            newLine.append(word.value)

            // Then, if word is not at end of line, append characters from old line until end of line
            if (wordAddEndIndex != -1) {
                for (i in wordAddEndIndex+1 until oldLine.length) {
                    newLine.append(oldLine[i])
                }
            }

            // Change line in blockedOutLines to updated version
            blockedOutLines[word.key.substringBefore(':').toInt()-1] = newLine.toString()
        }

        // Use StringBuilder to build string of lines of lyrics after updates for collected words made
        val newLyrics = StringBuilder()
        for (line in blockedOutLines) {
            newLyrics.append("$line\n")
        }

        // Add processed lyrics string to text view
        textViewWords.text = newLyrics
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate options menu
        menuInflater.inflate(R.menu.menu_lyrics_found, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when {
        item.itemId == android.R.id.home -> {
            // If back option selected, return to Maps Activity
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
        editor.putInt("storedGuessCount", guessCount)
        editor.apply()
    }

    private fun makeGuess() {
        // Alert dialog for user to input guess into
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Make a guess")
        builder.setMessage("Please input the song title")
        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_TEXT

        if (!incorrectGuess.isNullOrEmpty()) {
            // If user has already guessed incorrectly, show their previous guess in the alert dialog
            input.text.append(incorrectGuess)
            input.setSelectAllOnFocus(true)
        }

        builder.setView(input)
        builder.setPositiveButton("Make Guess!") { _, _ ->
            // Compare input to song title, ignore case and punctuation
            if (input.text.toString().replace(Regex("[^A-Za-z ]"), "").toLowerCase() ==
                    songs[songToPlayIndexString.toInt() - 1].title.replace(Regex("[^A-Za-z ]"), "").toLowerCase()) {

                // Correct guess, add song to list of unlocked songs
                songsUnlocked.add(songs[songToPlayIndexString.toInt()-1])
                wordsCollected.clear()
                wordsWithPos.clear()
                unlocked = true
                guessCount = 0

                // Dialog for correct guess
                val builderCorrect = AlertDialog.Builder(this)
                builderCorrect.setTitle("Nice one, you guessed correctly!")
                builderCorrect.setMessage("View the full lyrics, share with your friends or move to the next song?")

                builderCorrect.setPositiveButton("Next Song") { _, _ ->
                    // Move onto next song to play, return to Maps Activity then Main Activity
                    // since unlocked variable is set, then new song index will be found and
                    // Maps activity will be re-launched
                    onBackPressed()
                }

                builderCorrect.setNegativeButton("View Lyrics") { _, _ ->
                    // Show lyrics in Song Details activity
                    val intent = Intent(this, SongDetailActivity::class.java)
                    intent.putExtra("song", songs[songToPlayIndexString.toInt()-1])
                    startActivity(intent)
                }

                builderCorrect.setNeutralButton("Share") { _, _ ->
                    // Start sharing intent
                    val sharingIntent = Intent(android.content.Intent.ACTION_SEND)
                    sharingIntent.type = "text/plain"
                    sharingIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, "Songle")
                    sharingIntent.putExtra(android.content.Intent.EXTRA_TEXT,
                            "I unlocked ${songs[songToPlayIndexString.toInt()-1].title} by " +
                                    "${songs[songToPlayIndexString.toInt()-1].artist} on Songle!")
                    startActivity(Intent.createChooser(sharingIntent, "Share via"))
                }

                // User cannot cancel dialog, or else song index would not be updated
                builderCorrect.setCancelable(false)
                builderCorrect.show()

            } else {
                // Incorrect guess, save input for showing on future guess dialog
                incorrectGuess = input.text.toString()

                // Increment guess counter
                guessCount++

                // Dialog for incorrect guess
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
        // On return to Maps Activity, send guess count and unlocked boolean in intent extras
        val intent = Intent()
        intent.putExtra("returnGuessCount", guessCount)
        intent.putExtra("returnUnlocked", unlocked)
        setResult(Activity.RESULT_OK, intent)
        finish()
    }
}
