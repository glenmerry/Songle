package com.example.glenmerry.songle

import android.Manifest
import android.app.Activity
import android.app.ProgressDialog
import android.app.ProgressDialog.STYLE_HORIZONTAL
import android.app.ProgressDialog.STYLE_SPINNER
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.Location
import android.net.ConnectivityManager
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v4.content.ContextCompat.checkSelfPermission
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.text.Editable
import android.text.InputType
import android.view.Menu
import android.view.MenuItem
import android.view.WindowManager
import android.widget.EditText
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.location.LocationListener
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.maps.android.data.kml.KmlContainer
import com.google.maps.android.data.kml.KmlLayer
import com.google.maps.android.data.kml.KmlPoint
import kotlinx.android.synthetic.main.activity_maps.*
import kotlinx.android.synthetic.main.activity_song_detail.*
import org.jetbrains.anko.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.math.BigDecimal
import java.net.HttpURLConnection
import java.net.URL

class MapsActivity : AppCompatActivity(), OnMapReadyCallback, GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener, LocationListener, GoogleMap.OnMarkerClickListener {

    private lateinit var mMap: GoogleMap
    private lateinit var mGoogleApiClient: GoogleApiClient
    private val PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 1
    private var mLastLocation: Location? = null
    private val tag = "MapsActivity"
    private var songToPlayIndexString: String? = null
    private var distanceWalked = 0.toFloat()
    private var targetMet = false
    private var songsSkipped = arrayListOf<Song>()
    private var guessCount: Int = 0
    private var walkingTarget: Int? = null
    private var walkingTargetProgress = 0.toFloat()
    private var wordsWithPos = HashMap<String, String>()
    private var wordsCollected = arrayListOf<String>()
    private var skip = false
    private var unlocked = false
    private val markers = arrayListOf<Marker>()
    private var difficulty: Int = 1
    private lateinit var progressDialog: ProgressDialog
    private var receiver = NetworkReceiver()
    private var connectionLost = false
    private var connectionLostMapLoadFailed = false
    private var incorrectGuess: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maps)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)

        // Populate variables with values from intent extras
        difficulty = intent.extras.getInt("difficulty")
        songToPlayIndexString = intent.extras.getString("songToPlay")

        if (songToPlayIndexString != null) {
            toast("Playing song: ${songs[songToPlayIndexString!!.toInt() - 1].title}")
        } else {
            onBackPressed()
        }

        songsSkipped = intent.extras.getParcelableArrayList("songsSkipped")
        distanceWalked = intent.extras.getInt("distanceWalked").toFloat()
        walkingTarget = intent.extras.getInt("walkingTarget")
        if (walkingTarget == 0) {
            // No walking target has been set
            walkingTarget = null
        } else {
            // Walking target has been set, so get progress
            walkingTargetProgress = intent.extras.getInt("walkingTargetProgress").toFloat()
        }
        targetMet = intent.extras.getBoolean("targetMet")

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager
                .findFragmentById(R.id.map) as SupportMapFragment

        // Get notified when the map is ready to be used.
        // Long running activities are performed asynchronously in order to keep the user interface responsive
        mapFragment.getMapAsync(this)
        // Create an instance of GoogleAPIClient.
        mGoogleApiClient = GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build()

        // lastLoc contains the last known location, initially populated with zeros
        //lastLoc = Location("")
        //lastLoc.latitude = 0.0
        //lastLoc.longitude = 0.0
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate options menu
        menuInflater.inflate(R.menu.menu_activity_maps, menu)
        if (guessCount < 3) {
            // If user has made less than 3 guesses, hide hint feature
            menu.getItem(1).isVisible = false
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when {
        item.itemId == android.R.id.home -> {
            // On back button press, return to Main Activity
            onBackPressed()
            true
        }
        item.itemId == R.id.action_words_collected -> {
            // Launch Words Collected Activity
            val intent = Intent(this, WordsCollectedActivity::class.java)
            intent.putExtra("songToPlay", songToPlayIndexString)
            intent.putExtra("guessCount", guessCount)
            intent.putExtra("wordsCollected", wordsCollected)
            intent.putExtra("wordsWithPos", wordsWithPos)
            startActivityForResult(intent, 1)
            true
        }
        item.itemId == R.id.action_guess -> {
            // Guess feature
            makeGuess()
            true
        }
        item.itemId == R.id.action_skip -> {
            // Skip feature
            skipSong()
            true
        }
        item.itemId == R.id.action_hint -> {
            // Hint feature
            getHint()
            true
        }
        else -> false
    }

    private fun skipSong() {
        alert("Are you sure you want to skip this song?") {
            positiveButton("Yes please") {
                if (songToPlayIndexString == null) {
                    onBackPressed()
                } else {
                    songsSkipped.add(songs[songToPlayIndexString!!.toInt() - 1])
                    wordsCollected.clear()
                    wordsWithPos.clear()
                    guessCount = 0
                    skip = true
                    onBackPressed()
                    toast("Skipped song ${songs[songToPlayIndexString!!.toInt() - 1].title}")
                }
            }
            negativeButton("No thanks") {}
        }.show()
    }

    private fun getHint() {
        if (connectionLost) {
            // Cannot download hint word unless connected
            alert("Please check your network connection","Download failed")  {
                positiveButton("Ok") { }
            }.show()

        } else if (markers.isEmpty()) {
            // All words have already been collected/given as hints
            alert("Sorry, no more words available...") {
                positiveButton("Make guess") { makeGuess() }
                negativeButton("Skip song") { skipSong() }
            }.show()

        } else {
            // Make sure click wasn't accidental
            alert("Want a hint?") {
                positiveButton("Yes please!") {
                    var hintWord: String
                    var hintMarker: Marker = markers[0]
                    var hintTag: String? = null

                    // Get most interesting word possible in current difficulty
                    var maxInterest = when (difficulty) {
                        1 -> "unclassified"
                        2 -> "notboring"
                        3 -> "interesting"
                        4 -> "interesting"
                        5 -> "veryinteresting"
                        else -> "noDifficultyError"
                    }

                    // Finds a marker to use for hint
                    fun getHintWord() {
                        for (marker in markers) {
                            if (marker.title == maxInterest && !wordsCollected.contains(marker.tag)) {
                                hintMarker = marker
                                hintTag = marker.tag as String
                                break
                            }
                        }
                    }
                    getHintWord()

                    // If no markers were found at most interesting level, try next level of interest if available
                    if (hintTag == null) {
                        maxInterest = when (difficulty) {
                            2 -> "boring"
                            3 -> "notboring"
                            4 -> "notboring"
                            5 -> "interesting"
                            else -> "noChange"
                        }
                    }

                    if (maxInterest != "noChange") {
                        // Attempt to find marker at new interest level if interest level changed
                        getHintWord()
                    }

                    // If no markers were found at most interesting level, try next level of interest if available
                    if (hintTag == null) {
                        maxInterest = when (difficulty) {
                            3 -> "boring"
                            4 -> "boring"
                            5 -> "notboring"
                            else -> "noChange"
                        }
                    }

                    if (maxInterest != "noChange") {
                        // Attempt to find marker at new interest level if interest level changed
                        getHintWord()
                    }

                    // If on beginner difficulty level and still no marker found, try last possible interest level
                    if (hintTag == null && difficulty == 5) {
                        maxInterest = "boring"
                        getHintWord()
                    }

                    if (hintTag != null && maxInterest != "noDifficultyError") {
                        // Hint word has been found, download the song lyrics to get actual word
                        doAsync {
                            val urlWords = URL("http://www.inf.ed.ac.uk/teaching/courses/cslp/data/songs/$songToPlayIndexString/lyrics.txt")
                            val br = BufferedReader(InputStreamReader(urlWords.openStream()))
                            // Get lines of lyrics
                            val lines = arrayListOf<String>()
                            var line: String? = null
                            while ({ line = br.readLine(); line }() != null) {
                                if (line != null) {
                                    lines.add(line!!)
                                }
                            }

                            uiThread {
                                // Hide marker and add to collected words
                                hintMarker.isVisible = false
                                wordsCollected.add(hintMarker.tag as String)

                                // Get hint word from lyrics using line and word index from tag
                                hintWord = lines[hintTag!!.substringBefore(':').toInt() - 1].split(" ")[hintTag!!.substringAfter(':').toInt() - 1]

                                // Display hint word to user
                                alert("\n\"$hintWord\"\n\nThink you've got it now?", "Here's a word that might help...") {
                                    positiveButton("Yep!") { makeGuess() }
                                    negativeButton("Not yet - keep playing") { }
                                }.show()
                            }
                        }
                    }
                }
                negativeButton("No I'm fine thanks") { }
            }.show()
        }
    }


    override fun onStart() {
        super.onStart()
        mGoogleApiClient.connect()

        // Register BroadcastReceiver to track connection changes.
        val filter = IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)
        this.registerReceiver(receiver, filter)

        // Restore preferences
        val settings = getSharedPreferences(prefsFile, Context.MODE_PRIVATE)
        wordsCollected.addAll(settings.getStringSet("storedWordsCollected", setOf()))
        if (songToPlayIndexString != null) {
            if (songsUnlocked.contains(songs[songToPlayIndexString!!.toInt() - 1])) {
                unlocked = true
                onBackPressed()
            }
        } else {
            // If no song index, return to Main Activity
            onBackPressed()
        }
        guessCount = settings.getInt("storedGuessCount", guessCount)
        // If user has guessed at least 3 times, invalidate options menu so that hint option shows
        if (guessCount >= 3) {
            invalidateOptionsMenu()
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        // Centre map on George Square
        val georgeSq = LatLng(55.944009, -3.188438)
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(georgeSq, 15.7.toFloat()))

        try {
            // Visualise current position with a small blue circle
            mMap.isMyLocationEnabled = true
        } catch (se: SecurityException) {
            println("Security exception thrown [onMapReady]")
        }

        processMarkers()
    }

    // Downloads and processes word markers
    private fun processMarkers() {
        // Display progress dialog while downloading and processing markers
        progressDialog = progressDialog(message = "Please wait", title = "Setting up the Songle map…") {
            setProgressStyle(STYLE_SPINNER)
            setCancelable(false)
        }

        // Download KML file for current song and difficulty asynchronously
        doAsync {
            val url = URL("http://www.inf.ed.ac.uk/teaching/courses/cslp/data/songs/$songToPlayIndexString/map$difficulty.kml")
            val conn = url.openConnection() as HttpURLConnection
            conn.readTimeout = 10000 // milliseconds
            conn.connectTimeout = 15000 // milliseconds
            conn.requestMethod = "GET"
            conn.doInput = true
            conn.connect()

            // Create KML layer from downloaded file
            val layer = KmlLayer(mMap, conn.inputStream, applicationContext)

            // URLs of image files of markers, different for each level of word interest
            val urlVI = URL("http://maps.google.com/mapfiles/kml/paddle/red-stars.png")
            val urlI = URL("http://maps.google.com/mapfiles/kml/paddle/orange-diamond.png")
            val urlNB = URL("http://maps.google.com/mapfiles/kml/paddle/ylw-circle.png")
            val urlB = URL("http://maps.google.com/mapfiles/kml/paddle/ylw-blank.png")
            val urlU = URL("http://maps.google.com/mapfiles/kml/paddle/wht-blank.png")

            // Create bitmaps of each marker image
            val bmpVI = Bitmap.createScaledBitmap(BitmapFactory.decodeStream(urlVI.openConnection().getInputStream()), 80, 80, true)
            val bmpI = Bitmap.createScaledBitmap(BitmapFactory.decodeStream(urlI.openConnection().getInputStream()), 80, 80, true)
            val bmpNB = Bitmap.createScaledBitmap(BitmapFactory.decodeStream(urlNB.openConnection().getInputStream()), 80, 80, true)
            val bmpB = Bitmap.createScaledBitmap(BitmapFactory.decodeStream(urlB.openConnection().getInputStream()), 80, 80, true)
            val bmpU = Bitmap.createScaledBitmap(BitmapFactory.decodeStream(urlU.openConnection().getInputStream()), 80, 80, true)

            // URL of song lyrics file
            val urlWords = URL("http://www.inf.ed.ac.uk/teaching/courses/cslp/data/songs/$songToPlayIndexString/lyrics.txt")

            // Create list of lines of lyrics
            val br = BufferedReader(InputStreamReader(urlWords.openStream()))
            val lines = arrayListOf<String>()
            var line: String? = null
            while({ line = br.readLine(); line}() != null) {
                lines.add(line!!)
            }

            activityUiThread {
                // Add KML layer to map
                layer.addLayerToMap()

                // Get container from KML layer
                val container = layer.containers.first() as KmlContainer

                // Iterate through all placemarks in container
                for (placemark in container.placemarks) {
                    // Get word identifier from placemark name
                    val name = placemark.getProperty("name")
                    // Get words in line of placemark word
                    val wordsInLine = lines[name.substringBefore(':').toInt()-1].split(" ")
                    // Store actual word with position in lyrics in wordsWithPos HashMap
                    wordsWithPos.put(name, wordsInLine[name.substringAfter(':').toInt()-1])

                    // Check that placemark's word has not yet been collected
                    if (!wordsCollected.contains(name)) {
                        // If not collected, get data from placemark
                        val desc = placemark.getProperty("description")
                        val point = placemark.geometry as KmlPoint
                        val ll = LatLng(point.geometryObject.latitude, point.geometryObject.longitude)
                        // Get correct icon for marker depending on interest level
                        val icon: BitmapDescriptor = when (desc) {
                            "veryinteresting" -> BitmapDescriptorFactory.fromBitmap(bmpVI)
                            "interesting" -> BitmapDescriptorFactory.fromBitmap(bmpI)
                            "notboring" -> BitmapDescriptorFactory.fromBitmap(bmpNB)
                            "boring" -> BitmapDescriptorFactory.fromBitmap(bmpB)
                            "unclassified" -> BitmapDescriptorFactory.fromBitmap(bmpU)
                            else -> BitmapDescriptorFactory.fromBitmap(bmpU)
                        }

                        // Create marker with data of word
                        val marker = mMap.addMarker(MarkerOptions()
                                .position(ll)
                                .icon(icon)
                                .title(desc)
                        )
                        // Tag hold word position identifier
                        marker.tag = name
                        // Add marker to map
                        markers.add(marker)
                    }
                }
                // Remove KML layer from map
                layer.removeLayerFromMap()
                // Operation finished, dismiss progress dialog
                progressDialog.dismiss()
            }
        }
    }

    // BroadcastReceiver that tracks network connectivity changes.
    private inner class NetworkReceiver: BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val connMgr = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val networkInfo = connMgr.activeNetworkInfo

            if (networkInfo != null) {
                // Network is available
                if (connectionLostMapLoadFailed) {
                    // Downloading and processing markers failed, display "Connected" snackbar
                    val snackbar : Snackbar = Snackbar.make(findViewById(R.id.map),"Connected", Snackbar.LENGTH_SHORT)
                    snackbar.show()
                    // Try again to download and process markers
                    processMarkers()
                    connectionLostMapLoadFailed = false
                }
                if (connectionLost) {
                    // Connection was lost but map was fully processed, do not display snackbar
                    connectionLost = false
                }
            } else {
                // No network connection
                if (progressDialog.isShowing) {
                    // Downloading and processing map markers failed, hide progress download and display warning snackbar
                    progressDialog.dismiss()
                    val snackbar = Snackbar.make(findViewById(R.id.map),"No internet connection available", Snackbar.LENGTH_INDEFINITE)
                    snackbar.show()
                    connectionLostMapLoadFailed = true
                }
                // Connection lost but map processed fully, do not display warning
                connectionLost = true
            }
        }
    }

    override fun onMarkerClick(marker: Marker): Boolean = false

    override fun onLocationChanged(current: Location?) {
        if (current == null) {
            println("[onLocationChanged] Location unknown")
        }

        if (mLastLocation != null && current != null) {
            // A location has been saved from previously and a current location is available
            // Calculate distance between current and last locations and add this to distance walked
            val distToAdd = current.distanceTo(mLastLocation)
            distanceWalked += distToAdd
            if (walkingTarget != null) {
                // If a walking target is set also add to target progress
                walkingTargetProgress += distToAdd
            }
        }

        // Pair to hold nearest marker and its distance from current location
        var nearestWordAndDist: Pair<Marker, Int>? = null

        if (current != null) {
            // Update last location to current
            mLastLocation = current
            if (markers.size != 0) {
                // If there are words available to collect, find closest
                nearestWordAndDist = nearestMarker(current.longitude, current.latitude)
            }
        }

        if (walkingTarget != null && walkingTargetProgress >= walkingTarget!! && !targetMet) {
            targetMet = true
            val walkingTargetWithUnit = if (walkingTarget!! < 1000) {
                "${walkingTarget!!.toInt()}m"
            } else {
                "${BigDecimal(walkingTarget!!.toDouble() / 1000).setScale(2, BigDecimal.ROUND_HALF_UP)}km"
            }
            alert("You hit your walking target of $walkingTargetWithUnit", "Congratulations!") {
                positiveButton("Set new target") {
                    val alert = AlertDialog.Builder(this@MapsActivity)
                    alert.setTitle("Set a new walking target in metres")
                    val input = EditText(this@MapsActivity)
                    input.inputType = InputType.TYPE_CLASS_NUMBER
                    input.setRawInputType(Configuration.KEYBOARD_12KEY)
                    alert.setView(input)
                    alert.setPositiveButton("Set", { _, _ ->
                        if (input.text.isNotEmpty()) {
                            val newWalkingTarget = input.text.toString().toInt()
                            if (walkingTarget != null && walkingTarget!! > 0) {
                                targetMet = false
                                walkingTarget = newWalkingTarget
                                walkingTargetProgress = 0.toFloat()
                            }
                        }
                    })
                    alert.setNegativeButton("Cancel", { _, _ ->

                    })
                    alert.show()
                }
                negativeButton("Back to map") {}
            } .show()
        }


        if (nearestWordAndDist != null && nearestWordAndDist.second == 1000000) {
            // No marker found, return early
            return
        }

        if (nearestWordAndDist != null && nearestWordAndDist.second <= 10) {
            // Nearest marker is within 10m so is available to collect
            // Display nearest word snackbar
            Snackbar
                    .make(findViewById(R.id.map),"Nearest word is ${nearestWordAndDist.second}m away", Snackbar.LENGTH_INDEFINITE)
                    // Collect button becomes available on snackbar
                    .setAction("COLLECT") {
                        // Word is collected, make marker invisible and add to collected words
                        nearestWordAndDist!!.first.isVisible = false
                        wordsCollected.add(nearestWordAndDist!!.first.tag as String)

                        // Alert dialog presents collected word
                        alert("\"${wordsWithPos[nearestWordAndDist!!.first.tag]}\"","You collected a new word!") {
                            negativeButton("Collected words") {
                                // Launch Words Collected activity
                                val intent = Intent(applicationContext, WordsCollectedActivity::class.java)
                                startActivity(intent)
                            }
                            positiveButton("Make guess") {
                                // Let user guess song
                                makeGuess()
                            }
                        }.show()

                        // Call onLocationChanged again to update snackbar
                        onLocationChanged(current)

                    }.show()
        } else if (nearestWordAndDist != null) {
            // Nearest word is over 10m away display distance in snackbar but now collect button
            Snackbar.make(findViewById(R.id.map), "Nearest word is ${nearestWordAndDist.second}m away", Snackbar.LENGTH_INDEFINITE).show()
        }
    }

    // Finds nearest marker to user's location
    private fun nearestMarker(long: Double, lat: Double): Pair<Marker, Int> {
        var currentMin = 1000000F
        var currentResult = markers[0]
        val results = FloatArray(10)
        markers.forEach { marker ->
            // For each marker check if it is closer than the current minimum distance
            Location.distanceBetween(lat, long, marker.position.latitude, marker.position.longitude, results)
            if (results[0] < currentMin) {
                if (marker.isVisible) {
                    // Make sure marker is visible and so available to collect
                    currentResult = marker
                    currentMin = results[0]
                }
            }
        }
        // Return nearest marker and its distance from user
        return Pair(currentResult, currentMin.toInt())
    }

    override fun onConnected(connectionHint : Bundle?) {
        // Attempt to create location request
        try {
            createLocationRequest()
        } catch (ise : IllegalStateException) {
            println("[$tag] [onConnected] IllegalStateException thrown")
        }

        // Can we access the user’s current location?
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            val api = LocationServices.FusedLocationApi
            mLastLocation = api.getLastLocation(mGoogleApiClient)
            // Caution: getLastLocation can return null
            if (mLastLocation == null) {
                println("[$tag] Warning: mLastLocation is null")
            }
        } else {
            // Request location access permission
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION), PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION)
        }
    }

    private fun createLocationRequest() {
        // Set the parameters for the location request
        val mLocationRequest = LocationRequest()
        mLocationRequest.interval = 5000
        mLocationRequest.fastestInterval = 1000
        mLocationRequest.priority =
                LocationRequest.PRIORITY_HIGH_ACCURACY

        // Can we access the user’s current location?
        val permissionCheck = checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
            LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this)
        }
    }

    override fun onConnectionSuspended(flag: Int) {
        println(" >>>> onConnectionSuspended")
    }

    override fun onConnectionFailed(result: ConnectionResult) {
        // An unresolvable error has occurred and a connection to Google APIs
        // could not be established.
        println(" >>>> onConnectionFailed")
    }

    override fun onPause() {
        super.onPause()

        // If receiver is registered, unregister it
        try {
            unregisterReceiver(receiver)
        } catch(e: IllegalArgumentException) {
            println("Receiver not registered")
        }

        // Store values in shared preferences
        // All objects are from android.context.Context
        val settings = getSharedPreferences(prefsFile, Context.MODE_PRIVATE)

        // We need an Editor object to make preference changes.
        val editor = settings.edit()

        editor.putInt("storedDistanceWalked", distanceWalked.toInt())
        editor.putInt("storedWalkingTargetProgress", walkingTargetProgress.toInt())
        editor.putStringSet("storedWordsCollected", wordsCollected.toSet())

        // Store titles of skipped and unlocked songs in sets
        val titlesSkipped = songsSkipped
                .map { it.title }
                .toSet()
        editor.putStringSet("storedSongsSkipped", titlesSkipped)
        val titlesUnlocked = songsUnlocked
                .map { it.title }
                .toSet()
        editor.putStringSet("storedSongsUnlocked", titlesUnlocked)

        editor.putString("storedSongToPlayIndexString", songToPlayIndexString)
        editor.putInt("storedGuessCount", guessCount)

        // Apply changes
        editor.apply()
    }

    override fun onStop() {
        super.onStop()
        // Disconnect Google Api Client if connected
        if (mGoogleApiClient.isConnected) {
            mGoogleApiClient.disconnect()
        }
    }

    override fun onBackPressed() {
        // On back pressed, return values to Main Activity in intent extras
        val intent = Intent()
        intent.putExtra("returnDistance", distanceWalked.toInt())
        intent.putParcelableArrayListExtra("returnSongsSkipped", songsSkipped)
        if (walkingTarget != null) {
            intent.putExtra("returnWalkingTarget", walkingTarget!!)
        }
        intent.putExtra("returnWalkingTargetProgress", walkingTargetProgress.toInt())
        if (skip) {
            intent.putExtra("returnSkip", skip)
        }
        if (unlocked) {
            intent.putExtra("returnUnlocked", unlocked)
        }
        setResult(Activity.RESULT_OK, intent)
        finish()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent) {
        if (requestCode == 1) {
            // Returning from Words Collected Activity
            if (resultCode == Activity.RESULT_OK) {
                guessCount = data.getIntExtra("returnGuessCount", 0)
                if (guessCount >= 3) {
                    // If user has guessed at least 3 times, invalidate options menu so that hint
                    // option is displayed
                    invalidateOptionsMenu()
                }
                unlocked = data.getBooleanExtra("returnUnlocked", false)
                if (unlocked) {
                    // If song was unlocked from Words Collected activity set unlocked variable
                    // and return to Main Activity where next song index will be calculated before
                    // launching Maps Activity again
                    onBackPressed()
                }
            }
        } else if (requestCode == 2) {
            // Returning from sharing intent
            onBackPressed()
        }
    }

    private fun makeGuess() {
        if (songToPlayIndexString != null) {
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
                        songs[songToPlayIndexString!!.toInt() - 1].title.replace(Regex("[^A-Za-z ]"), "").toLowerCase()) {

                    // Correct guess, add song to list of unlocked songs
                    songsUnlocked.add(songs[songToPlayIndexString!!.toInt() - 1])
                    wordsCollected.clear()
                    wordsWithPos.clear()
                    unlocked = true
                    guessCount = 0

                    // Dialog for correct guess
                    val builderCorrect = AlertDialog.Builder(this)
                    builderCorrect.setTitle("Nice one, you guessed correctly!")
                    builderCorrect.setMessage("View the full lyrics, share with your friends or move to the next song?")

                    builderCorrect.setPositiveButton("Next Song") { _, _ ->
                        // Return to Main Activity, since unlocked variable is set and returned
                        // Main Activity will get new song index and re-launch Maps Activity
                        onBackPressed()
                    }

                    builderCorrect.setNegativeButton("View Lyrics") { _, _ ->
                        // Show lyrics in Song Details activity
                        val intent = Intent(this, SongDetailActivity::class.java)
                        intent.putExtra("song", songs[songToPlayIndexString!!.toInt() - 1])
                        startActivityForResult(intent, 2)
                    }

                    builderCorrect.setNeutralButton("Share") { _, _ ->
                        // Start sharing intent
                        val sharingIntent = Intent(android.content.Intent.ACTION_SEND)
                        sharingIntent.type = "text/plain"
                        sharingIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, "Songle")
                        sharingIntent.putExtra(android.content.Intent.EXTRA_TEXT, "I unlocked ${songs[songToPlayIndexString!!.toInt() - 1].title} " +
                                "by ${songs[songToPlayIndexString!!.toInt() - 1].artist} on Songle!")
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
                    if (guessCount == 3) {
                        // If guessCount reaches 3, the hint option should be shown to the user
                        // This is done by invalidating the options to force it to redraw
                        invalidateOptionsMenu()
                    }

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
            if (unlocked) {
                // Return to Main Activity, since unlocked variable is set and returned
                // Main Activity will get new song index and re-launch Maps Activity
                onBackPressed()
                onBackPressed()
            }
        } else {
            // No song index, return to Main Activity for index to be found
            onBackPressed()
        }
    }
}