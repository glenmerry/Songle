package com.example.glenmerry.songle

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.Location
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v4.content.ContextCompat.checkSelfPermission
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.text.InputType
import android.view.Menu
import android.view.MenuItem
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
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.maps.android.data.kml.KmlLayer
import org.jetbrains.anko.activityUiThread
import org.jetbrains.anko.alert
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.toast
import java.math.BigDecimal
import java.net.HttpURLConnection
import java.net.URL
import java.util.*

class MapsActivity : AppCompatActivity(), OnMapReadyCallback, GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener, LocationListener, GoogleMap.OnMarkerClickListener {

    private lateinit var mMap: GoogleMap
    private lateinit var mGoogleApiClient: GoogleApiClient
    private val PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 1
    var mLocationPermissionGranted = false
    private var mLastLocation: Location? = null
    private val tag = "MapsActivity"
    private var songToPlayIndexString = "01"
    private var distanceWalked = 0.toFloat()
    private lateinit var lastLoc: Location
    private var targetMet = false // Probably dont need this!
    private var songsSkipped = arrayListOf<Song>()
    private var guessCount: Int = 0
    private var walkingTarget: Int? = null
    private var walkingTargetProgress = 0.toFloat()

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
        item.itemId == R.id.action_lyrics_list -> {
            val intent = Intent(this, WordsCollectedActivity::class.java)
            intent.putExtra("songToPlay", songToPlayIndexString)
            intent.putExtra("guessCount", guessCount)
            startActivityForResult(intent, 1)
            true
        }
        item.itemId == R.id.action_guess -> {
            makeGuess()
            true
        }
        item.itemId == R.id.action_skip -> {
            alert("Are you sure you want to skip this song?") {
                positiveButton("Yes please") {
                    songsSkipped.add(songs[songToPlayIndexString.toInt()])
                    toast("Skipped song ${songs[songToPlayIndexString.toInt()].title}")
                }
                negativeButton("No thanks") {}
            }.show()
            true
        }
        item.itemId == R.id.action_hint -> {
            alert("Want a hint?") {
                positiveButton("Yes please!") {
                    alert("\n\"Magnifico\"\n\nThink you've got it now?","Here's a word that might help...") {
                        positiveButton("Yep!") {makeGuess()}
                        negativeButton("Not yet - keep playing") {}
                    }.show()
                }
                negativeButton("No I'm fine thanks") {}
            }.show()
            true
        }
        else -> false
    }

    private var difficulty: Int = 1

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maps)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)

        difficulty = intent.extras.getInt("difficulty")
        songToPlayIndexString = intent.extras.getString("songToPlay")
        songsSkipped = intent.extras.getParcelableArrayList("songsSkipped")
        distanceWalked = intent.extras.getInt("distanceWalked").toFloat()
        walkingTarget = intent.extras.getInt("walkingTarget")
        if (walkingTarget == 0) {
            walkingTarget = null
        } else {
            walkingTargetProgress = intent.extras.getInt("walkingTargetProgress").toFloat()
        }
        targetMet = intent.extras.getBoolean("targetMet")

        toast("Walking target is $walkingTarget")
        toast("Playing song: ${songs[songToPlayIndexString.toInt()].title}")

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

    private lateinit var bmp: Bitmap

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

        doAsync {
            val url = URL("http://www.inf.ed.ac.uk/teaching/courses/cslp/data/songs/$songToPlayIndexString/map$difficulty.kml")
            val conn = url.openConnection() as HttpURLConnection
            conn.readTimeout = 10000 // milliseconds
            conn.connectTimeout = 15000 // milliseconds
            conn.requestMethod = "GET"
            conn.doInput = true
            conn.connect()

            val layer = KmlLayer(mMap, conn.inputStream, applicationContext)

            val url2 = URL("http://maps.google.com/mapfiles/kml/paddle/ylw-circle.png")
            bmp = BitmapFactory.decodeStream(url2.openConnection().getInputStream())
            val bmpResized = Bitmap.createScaledBitmap(bmp, 120, 120, false)

            activityUiThread {
                layer.addLayerToMap()

                /*layer.setOnFeatureClickListener(object: Layer.OnFeatureClickListener {
                    override fun onFeatureClick(feature: Feature) {
                        feature.getProperty("name")
                        println(feature.properties)

                        val coordinates = feature.getProperty("point")

                        toast("${feature.id} ${feature.getProperty("name")} clicked")
                    }
                })*/

                val mGeorgeSq = mMap.addMarker(MarkerOptions()
                        .position(LatLng(55.9436125635442, -3.18878173828125))
                        .icon(BitmapDescriptorFactory.fromBitmap(bmpResized))
                        .title("Not Boring"))
                mGeorgeSq.tag = 0
               // mMap.setOnMarkerClickListener(this)
            }
        }
    }

    override fun onMarkerClick(marker: Marker): Boolean {

        Snackbar.make(findViewById(R.id.map), "You've unlocked a new word! - Galileo", Snackbar.LENGTH_INDEFINITE).show()
        return false
    }

    override fun onStart() {
        super.onStart()
        mGoogleApiClient.connect()
    }

    override fun onStop() {
        super.onStop()
        if (mGoogleApiClient.isConnected) {
            mGoogleApiClient.disconnect()
        }

        // All objects are from android.context.Context
        val settings = getSharedPreferences(prefsFile, Context.MODE_PRIVATE)

        // We need an Editor object to make preference changes.
        val editor = settings.edit()

        editor.putInt("storedDistanceWalked", distanceWalked.toInt())
        editor.putInt("storedWalkingTargetProgress", walkingTargetProgress.toInt())

        editor.apply()
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

    override fun onLocationChanged(current : Location?) {
        if (current == null) {
            println("[onLocationChanged] Location unknown")
        } else {
           // println("[onLocationChanged] Lat/long now (${current.latitude}, ${current.longitude})" )
        }

        if (lastLoc.latitude != 0.0) {
            if (current != null) {
                val distToAdd = current.distanceTo(lastLoc)
                distanceWalked += distToAdd
                if (walkingTarget != null) {
                    walkingTargetProgress += distToAdd
                }
            }
        }

        if (current != null) {
            lastLoc = current
        }

        toast("distance changed to $distanceWalked\nWalked $walkingTargetProgress  of target $walkingTarget\n")

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
                        val newWalkingTarget = input.text.toString().toInt()
                        if (walkingTarget != null && walkingTarget!! > 0) {
                            targetMet = false
                            walkingTarget = newWalkingTarget
                            walkingTargetProgress = 0.toFloat()
                        }
                    })
                    alert.setNegativeButton("Cancel", { _, _ ->

                    })
                    alert.show()
                }
                negativeButton("Back to map") {}
            } .show()
        }

        Snackbar
                .make(findViewById(R.id.map), "Nearest word is 5m away", Snackbar.LENGTH_INDEFINITE)
                .setAction("COLLECT") {

                    alert("Galileo","You collected a new word!") {
                        negativeButton("Collected words") {
                            val intent = Intent(applicationContext, WordsCollectedActivity::class.java)
                            startActivity(intent)
                        }
                        positiveButton("Make guess") {
                            makeGuess()
                        }
                    }.show()

                }.show()
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

    override fun onBackPressed() {
        val intent = Intent()
        intent.putExtra("returnDistance", distanceWalked.toInt())
        intent.putParcelableArrayListExtra("returnSongsSkipped", songsSkipped)
        if (walkingTarget != null) {
            intent.putExtra("returnWalkingTarget", walkingTarget!!)
        }

        intent.putExtra("returnWalkingTargetProgress", walkingTargetProgress.toInt())
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
            }
        }
    }

    private fun makeGuess() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Make a guess")
        builder.setMessage("Please input the song title")
        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_TEXT
        builder.setView(input)
        builder.setPositiveButton("Make Guess!") { _, _ ->
            if (input.text.toString().toLowerCase() == songs[songToPlayIndexString.toInt()].title.toLowerCase()) {
                val builderCorrect = AlertDialog.Builder(this)
                builderCorrect.setTitle("Nice one, you guessed correctly!")
                builderCorrect.setMessage("View the full lyrics, share with your friends or move to the next song?")
                builderCorrect.setPositiveButton("Next Song") { _, _ ->
                    val intent = Intent(this, MapsActivity::class.java)
                    startActivity(intent)
                }
                builderCorrect.setNegativeButton("View Lyrics") { _, _ ->
                    val intent = Intent(this, SongDetailActivity::class.java)
                    intent.putExtra("song", songs[songToPlayIndexString.toInt()])
                    startActivity(intent)
                }
                builderCorrect.setNeutralButton("Share") { _, _ ->
                    val sharingIntent = Intent(android.content.Intent.ACTION_SEND)
                    sharingIntent.type = "text/plain"
                    sharingIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, "Songle")
                    sharingIntent.putExtra(android.content.Intent.EXTRA_TEXT, "I unlocked Bohemian Rhapsody by Queen on Songle!")
                    startActivity(Intent.createChooser(sharingIntent, "Share via"))
                }
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
}