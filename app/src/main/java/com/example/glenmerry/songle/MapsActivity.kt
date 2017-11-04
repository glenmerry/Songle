package com.example.glenmerry.songle

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
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
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.maps.android.data.kml.KmlLayer
import org.jetbrains.anko.activityUiThread
import org.jetbrains.anko.alert
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.toast
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

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_activity_maps, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when {
            item.itemId == android.R.id.home -> {
                onBackPressed()
                return true
            }
            item.itemId == R.id.action_lyrics_list -> {
                val intent = Intent(this, LyricsFoundActivity::class.java)
                //intent.putExtra("SONG", songs[songToPlayIndexString.toInt()])
                startActivity(intent)
                return true
            }
            item.itemId == R.id.action_guess -> {
                makeGuess()
                return true
            }
            item.itemId == R.id.action_skip -> {
                toast("Skip this song")
                return true
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
                return true
            }
            else -> return false
        }
    }

    private var difficulty: Int = 1

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maps)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)

        difficulty = intent.extras.getInt("DIFFICULTY")
        val songs: ArrayList<Song> = intent.extras.getParcelableArrayList("SONGS")
        val songToPlayIndexString = intent.extras.getString("SONGTOPLAY")

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
        lastLoc.latitude = (55.944009)
        lastLoc.longitude = -3.188438
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        val georgeSq = LatLng(55.944009, -3.188438)
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(georgeSq, 15.toFloat()))

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

            activityUiThread {
                layer.addLayerToMap()
            }
        }
        val mGeorgeSq = mMap.addMarker(MarkerOptions()
                .position(LatLng(55.9436125635442, -3.18878173828125))
                .title("New word!")
                .snippet("Galileo"))
        mGeorgeSq.tag = 0
        mMap.setOnMarkerClickListener(this)
    }

    override fun onMarkerClick(marker: Marker): Boolean {
        toast("marker clicked")
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
            } } else {
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION), PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION)
        } }

    override fun onLocationChanged(current : Location?) {
        if (current == null) {
            println("[onLocationChanged] Location unknown")
        } else {
        println("[onLocationChanged] Lat/long now (${current.latitude}, ${current.longitude})" )
        }
        // Do something with current location
        distanceWalked += current!!.distanceTo(lastLoc)
        lastLoc = current
        toast("distance changed to $distanceWalked")
        if (distanceWalked > 1000) {
            alert("You hit your walking target of 1km", "Congratulations!") {
                positiveButton("Set new target") {
                    /*val alert = AlertDialog.Builder(Context(MapsActivity))
                    alert.setTitle("Set a new walking target in metres")
                    val input = EditText(MapsActivity)
                    input.inputType = InputType.TYPE_CLASS_NUMBER
                    input.setRawInputType(Configuration.KEYBOARD_12KEY)
                    alert.setView(input)
                    alert.setPositiveButton("Set", DialogInterface.OnClickListener { dialog, whichButton ->

                    })
                    alert.setNegativeButton("Cancel", DialogInterface.OnClickListener { dialog, whichButton ->

                    })
                    alert.show()*/
                }
                negativeButton("Back to map") {}
            } .show()
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

    private fun makeGuess() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Make a guess")
        builder.setMessage("Please input the song title")
        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_TEXT
        builder.setView(input)
        builder.setPositiveButton("Make Guess!") { _, _ ->
            if (input.text.toString().toLowerCase() == songs[songToPlayIndexString.toInt()].title.toLowerCase()) {
                val builder = AlertDialog.Builder(this)
                builder.setTitle("Nice one, you guessed correctly!")
                builder.setMessage("View the full lyrics, share with your friends or move to the next song?")
                builder.setPositiveButton("Next Song") { _, _ ->
                    val intent = Intent(this, MapsActivity::class.java)
                    startActivity(intent)
                }
                builder.setNegativeButton("View Lyrics") { _, _ ->
                    val intent = Intent(this, SongDetailActivity::class.java)
                    intent.putExtra("SONG", songs[com.example.glenmerry.songle.songToPlayIndexString.toInt()])
                    startActivity(intent)
                }
                builder.setNeutralButton("Share") { _, _ ->
                    val sharingIntent = Intent(android.content.Intent.ACTION_SEND)
                    sharingIntent.type = "text/plain"
                    sharingIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, "Songle")
                    sharingIntent.putExtra(android.content.Intent.EXTRA_TEXT, "I unlocked Bohemian Rhapsody by Queen on Songle!")
                    startActivity(Intent.createChooser(sharingIntent, "Share via"))
                }
                builder.show()
            } else {
                //toast("Guess incorrect, please try again")
                val builder = AlertDialog.Builder(this)
                builder.setTitle("Sorry, that's not quite right")
                builder.setMessage("Guess again?")
                builder.setPositiveButton("Guess again") { _, _ ->
                    makeGuess()
                }
                builder.setNegativeButton("Back to map") { dialog, _ ->
                    dialog.cancel()
                }
                builder.show()
            }
        }
        builder.setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }
        builder.show()
    }
}