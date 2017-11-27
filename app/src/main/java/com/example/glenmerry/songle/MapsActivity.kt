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
    //var mLocationPermissionGranted = false
    private var mLastLocation: Location? = null
    private val tag = "MapsActivity"
    private var songToPlayIndexString: String? = null
    private var distanceWalked = 0.toFloat()
    private lateinit var lastLoc: Location
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
            walkingTarget = null
        } else {
            walkingTargetProgress = intent.extras.getInt("walkingTargetProgress").toFloat()
        }
        targetMet = intent.extras.getBoolean("targetMet")

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager
                .findFragmentById(R.id.map) as SupportMapFragment

        // Get notified when the map is ready to be used.
        // Long ́running activities are performed asynchronously in order to keep the user interface responsive
        mapFragment.getMapAsync(this)
        // Create an instance of GoogleAPIClient.
        mGoogleApiClient = GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build()

        lastLoc = Location("")
        lastLoc.latitude = 0.0
        lastLoc.longitude = 0.0
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_activity_maps, menu)
        if (guessCount < 3) {
            menu.getItem(1).isVisible = false
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when {
        item.itemId == android.R.id.home -> {
            onBackPressed()
            true
        }
        item.itemId == R.id.action_words_collected -> {
            val intent = Intent(this, WordsCollectedActivity::class.java)
            intent.putExtra("songToPlay", songToPlayIndexString)
            intent.putExtra("guessCount", guessCount)
            intent.putExtra("wordsCollected", wordsCollected)
            intent.putExtra("wordsWithPos", wordsWithPos)
            startActivityForResult(intent, 1)
            true
        }
        item.itemId == R.id.action_guess -> {
            makeGuess()
            true
        }
        item.itemId == R.id.action_skip -> {
            skipSong()
            true
        }
        item.itemId == R.id.action_hint -> {

            println("Markers size: ${markers.size}")
            if (connectionLost) {
                alert("Please check your network connection","Download failed")  {
                    positiveButton("Ok") { }
                }.show()
            } else if (markers.isEmpty()) {
                alert("Sorry, no more words available...") {
                    positiveButton("Make guess") { makeGuess() }
                    negativeButton("Skip song") { skipSong() }
                }.show()
            } else {
                alert("Want a hint?") {

                    positiveButton("Yes please!") {
                        var hintWord: String

                        var maxInterest = when (difficulty) {
                            1 -> "unclassified"
                            2 -> "notboring"
                            3 -> "interesting"
                            4 -> "interesting"
                            5 -> "veryinteresting"
                            else -> "noDifficultyError"
                        }

                        var hintMarker: Marker = markers[0]
                        var hintTag: String? = null

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
                            getHintWord()
                        }

                        if (hintTag == null) {
                            maxInterest = when (difficulty) {
                                3 -> "boring"
                                4 -> "boring"
                                5 -> "notboring"
                                else -> "noChange"
                            }
                        }

                        if (maxInterest != "noChange") {
                            getHintWord()
                        }

                        if (hintTag == null && difficulty == 5) {
                            maxInterest = "boring"
                            getHintWord()
                        }

                        println(hintTag)
                        println(maxInterest)

                        if (hintTag != null && maxInterest != "noDifficultyError") {

                            doAsync {

                                val urlWords = URL("http://www.inf.ed.ac.uk/teaching/courses/cslp/data/songs/$songToPlayIndexString/lyrics.txt")

                                val br = BufferedReader(InputStreamReader(urlWords.openStream()))
                                val lines = arrayListOf<String>()
                                var line: String? = null

                                while ({ line = br.readLine(); line }() != null) {
                                    if (line != null) {
                                        lines.add(line!!)
                                    }
                                }

                                uiThread {
                                    hintMarker.isVisible = false
                                    wordsCollected.add(hintMarker.tag as String)
                                    println(wordsCollected)
                                    hintWord = lines[hintTag!!.substringBefore(':').toInt() - 1].split(" ")[hintTag!!.substringAfter(':').toInt() - 1]
                                    alert("\n\"$hintWord\"\n\nThink you've got it now?", "Here's a word that might help...") {
                                        positiveButton("Yep!") { makeGuess() }
                                        negativeButton("Not yet - keep playing") {}
                                    }.show()
                                }
                            }
                        }
                    }
                    negativeButton("No I'm fine thanks") {}
                }.show()
            }

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
            onBackPressed()
        }
        guessCount = settings.getInt("storedGuessCount", guessCount)
        invalidateOptionsMenu()
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
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

    private fun processMarkers() {

        progressDialog = progressDialog(message = "Please wait", title = "Setting up the Songle map…") {
            setProgressStyle(STYLE_SPINNER)
            setCancelable(false)
        }
        doAsync {
            val url = URL("http://www.inf.ed.ac.uk/teaching/courses/cslp/data/songs/$songToPlayIndexString/map$difficulty.kml")
            val conn = url.openConnection() as HttpURLConnection
            conn.readTimeout = 10000 // milliseconds
            conn.connectTimeout = 15000 // milliseconds
            conn.requestMethod = "GET"
            conn.doInput = true
            conn.connect()

            val layer = KmlLayer(mMap, conn.inputStream, applicationContext)

            val urlVI = URL("http://maps.google.com/mapfiles/kml/paddle/red-stars.png")
            val urlI = URL("http://maps.google.com/mapfiles/kml/paddle/orange-diamond.png")
            val urlNB = URL("http://maps.google.com/mapfiles/kml/paddle/ylw-circle.png")
            val urlB = URL("http://maps.google.com/mapfiles/kml/paddle/ylw-blank.png")
            val urlU = URL("http://maps.google.com/mapfiles/kml/paddle/wht-blank.png")

            val bmpVI = Bitmap.createScaledBitmap(BitmapFactory.decodeStream(urlVI.openConnection().getInputStream()), 80, 80, true)
            val bmpI = Bitmap.createScaledBitmap(BitmapFactory.decodeStream(urlI.openConnection().getInputStream()), 80, 80, true)
            val bmpNB = Bitmap.createScaledBitmap(BitmapFactory.decodeStream(urlNB.openConnection().getInputStream()), 80, 80, true)
            val bmpB = Bitmap.createScaledBitmap(BitmapFactory.decodeStream(urlB.openConnection().getInputStream()), 80, 80, true)
            val bmpU = Bitmap.createScaledBitmap(BitmapFactory.decodeStream(urlU.openConnection().getInputStream()), 80, 80, true)

            val urlWords = URL("http://www.inf.ed.ac.uk/teaching/courses/cslp/data/songs/$songToPlayIndexString/lyrics.txt")

            val br = BufferedReader(InputStreamReader(urlWords.openStream()))
            val lines = arrayListOf<String>()
            var line: String? = null

            while({ line = br.readLine(); line}() != null) {
                lines.add(line!!)
            }

            activityUiThread {
                layer.addLayerToMap()

                val container = layer.containers.first() as KmlContainer

                for (placemark in container.placemarks) {
                    val name = placemark.getProperty("name")
                    val wordsInLine = lines[name.substringBefore(':').toInt()-1].split(" ")
                    wordsWithPos.put(name, wordsInLine[name.substringAfter(':').toInt()-1])
                    if (!wordsCollected.contains(name)) {
                        val desc = placemark.getProperty("description")
                        val point = placemark.geometry as KmlPoint
                        val ll = LatLng(point.geometryObject.latitude, point.geometryObject.longitude)
                        val icon: BitmapDescriptor = when (desc) {
                            "veryinteresting" -> BitmapDescriptorFactory.fromBitmap(bmpVI)
                            "interesting" -> BitmapDescriptorFactory.fromBitmap(bmpI)
                            "notboring" -> BitmapDescriptorFactory.fromBitmap(bmpNB)
                            "boring" -> BitmapDescriptorFactory.fromBitmap(bmpB)
                            "unclassified" -> BitmapDescriptorFactory.fromBitmap(bmpU)
                            else -> BitmapDescriptorFactory.fromBitmap(bmpU)
                        }

                        val marker = mMap.addMarker(MarkerOptions()
                                .position(ll)
                                .icon(icon)
                                .title(desc)
                        )
                        marker.tag = name
                        markers.add(marker)
                    }
                }
                layer.removeLayerFromMap()
                progressDialog.dismiss()
            }
        }
    }

    private inner class NetworkReceiver: BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val connMgr = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val networkInfo = connMgr.activeNetworkInfo

            if (networkInfo != null) {
                // Network is available
                if (connectionLostMapLoadFailed) {
                    val snackbar : Snackbar = Snackbar.make(findViewById(R.id.map),"Connected", Snackbar.LENGTH_SHORT)
                    snackbar.show()
                    processMarkers()
                    connectionLostMapLoadFailed = false
                }
                if (connectionLost) {
                    connectionLost = false
                }
            } else {
                // No network connection
                if (progressDialog.isShowing) {
                    progressDialog.dismiss()
                    val snackbar = Snackbar.make(findViewById(R.id.map),"No internet connection available", Snackbar.LENGTH_INDEFINITE)
                    snackbar.show()
                    connectionLostMapLoadFailed = true
                }
                connectionLost = true
            }
        }
    }

    override fun onMarkerClick(marker: Marker): Boolean = false

    override fun onLocationChanged(current : Location?) {
        if (current == null) {
            println("[onLocationChanged] Location unknown")
        } else {
            // println("[onLocationChanged] Lat/long now (${current.latitude}, ${current.longitude})" )
        }

        if (lastLoc.latitude != 0.0 && current != null) {
            val distToAdd = current.distanceTo(lastLoc)
            distanceWalked += distToAdd
            if (walkingTarget != null) {
                walkingTargetProgress += distToAdd
            }
        }

        var nearestWordAndDist: Pair<Marker, Int>? = null

        if (current != null) {
            lastLoc = current
            if (markers.size != 0) {
                nearestWordAndDist = nearestMarker(current.longitude, current.latitude)
            }
        }

        //toast("distance changed to $distanceWalked\nWalked $walkingTargetProgress  of target $walkingTarget\n")

        if (nearestWordAndDist != null && nearestWordAndDist.second == 1000000) {
            return
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

        if (nearestWordAndDist != null && nearestWordAndDist.second <= 30) {
            Snackbar
                    .make(findViewById(R.id.map),"Nearest word is ${nearestWordAndDist.second}m away", Snackbar.LENGTH_INDEFINITE)
                    .setAction("COLLECT") {
                        nearestWordAndDist!!.first.isVisible = false
                        wordsCollected.add(nearestWordAndDist!!.first.tag as String)
                        println(">>>collected word ${nearestWordAndDist!!.first.tag as String}")
                        alert("\"${wordsWithPos[nearestWordAndDist!!.first.tag]}\"","You collected a new word!") {
                            negativeButton("Collected words") {
                                val intent = Intent(applicationContext, WordsCollectedActivity::class.java)
                                startActivity(intent)
                            }
                            positiveButton("Make guess") {
                                makeGuess()
                            }
                        }.show()
                        onLocationChanged(current)
                    }.show()
        } else if (nearestWordAndDist != null) {
            Snackbar.make(findViewById(R.id.map), "Nearest word is ${nearestWordAndDist.second}m away", Snackbar.LENGTH_INDEFINITE).show()
        }
    }

    private fun nearestMarker(long: Double, lat: Double): Pair<Marker, Int> {
        var currentMin = 1000000F
        var currentResult = markers[0]
        val results = FloatArray(10)
        markers.forEach { marker ->
            Location.distanceBetween(lat, long, marker.position.latitude, marker.position.longitude, results)
            if (results[0] < currentMin) {
                if (marker.isVisible) {
                    currentResult = marker
                    currentMin = results[0]
                } else {
                    println("found invisible marker")
                }
            }
        }
        return Pair(currentResult, currentMin.toInt())
    }

    override fun onConnected(connectionHint : Bundle?) {
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

    override fun onConnectionSuspended(flag : Int) {
        println(" >>>> onConnectionSuspended")
    }

    override fun onConnectionFailed(result : ConnectionResult) {
        // An unresolvable error has occurred and a connection to Google APIs
        // could not be established. Display an error message, or handle
        // the failure silently
        println(" >>>> onConnectionFailed")
    }

    override fun onPause() {
        super.onPause()

        try {
            unregisterReceiver(receiver)
        } catch(e: IllegalArgumentException) {
            println("Receiver not registered")
        }

        // All objects are from android.context.Context
        val settings = getSharedPreferences(prefsFile, Context.MODE_PRIVATE)

        // We need an Editor object to make preference changes.
        val editor = settings.edit()

        editor.putInt("storedDistanceWalked", distanceWalked.toInt())
        editor.putInt("storedWalkingTargetProgress", walkingTargetProgress.toInt())
        editor.putStringSet("storedWordsCollected", wordsCollected.toSet())
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
        editor.apply()
    }

    override fun onStop() {
        super.onStop()
        if (mGoogleApiClient.isConnected) {
            mGoogleApiClient.disconnect()
        }
    }

    override fun onBackPressed() {
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
            if (resultCode == Activity.RESULT_OK) {
                guessCount = data.getIntExtra("returnGuessCount", 0)
                if (guessCount >= 3) {
                    invalidateOptionsMenu()
                }
                unlocked = data.getBooleanExtra("returnUnlocked", false)
                if (unlocked) {
                    onBackPressed()
                }
            }
        } else if (requestCode == 2) {
            println(">>>>> Return from share..")
            onBackPressed()
        }
    }

    private fun makeGuess() {
        if (songToPlayIndexString != null) {
            val builder = AlertDialog.Builder(this)
            builder.setTitle("Make a guess")
            builder.setMessage("Please input the song title")
            val input = EditText(this)
            input.inputType = InputType.TYPE_CLASS_TEXT

            if (!incorrectGuess.isNullOrEmpty()) {
                input.text.append(incorrectGuess)
                input.setSelectAllOnFocus(true)
            }

            builder.setView(input)
            builder.setPositiveButton("Make Guess!") { _, _ ->

                // Compare input to song title, ignore case and punctuation
                if (input.text.toString().replace(Regex("[^A-Za-z ]"), "").toLowerCase() ==
                        songs[songToPlayIndexString!!.toInt() - 1].title.replace(Regex("[^A-Za-z ]"), "").toLowerCase()) {

                    songsUnlocked.add(songs[songToPlayIndexString!!.toInt() - 1])
                    wordsCollected.clear()
                    wordsWithPos.clear()
                    unlocked = true
                    guessCount = 0

                    val builderCorrect = AlertDialog.Builder(this)
                    builderCorrect.setTitle("Nice one, you guessed correctly!")
                    builderCorrect.setMessage("View the full lyrics, share with your friends or move to the next song?")
                    builderCorrect.setPositiveButton("Next Song") { _, _ ->
                        onBackPressed()
                    }
                    builderCorrect.setNegativeButton("View Lyrics") { _, _ ->
                        val intent = Intent(this, SongDetailActivity::class.java)
                        intent.putExtra("song", songs[songToPlayIndexString!!.toInt() - 1])
                        startActivityForResult(intent, 2)
                    }
                    builderCorrect.setNeutralButton("Share") { _, _ ->
                        val sharingIntent = Intent(android.content.Intent.ACTION_SEND)
                        sharingIntent.type = "text/plain"
                        sharingIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, "Songle")
                        sharingIntent.putExtra(android.content.Intent.EXTRA_TEXT, "I unlocked ${songs[songToPlayIndexString!!.toInt() - 1].title} by ${songs[songToPlayIndexString!!.toInt() - 1].artist} on Songle!")
                        startActivity(Intent.createChooser(sharingIntent, "Share via"))
                    }
                    builderCorrect.setCancelable(false)
                    builderCorrect.show()

                } else {
                    incorrectGuess = input.text.toString()

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
            if (unlocked) {
                onBackPressed()
            }
        } else {
            onBackPressed()
        }
    }
}