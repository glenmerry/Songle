package com.example.glenmerry.songle

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat.checkSelfPermission
import android.support.v7.app.AlertDialog
import android.text.InputType
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.location.LocationListener
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.data.Feature
import com.google.maps.android.data.Layer
import com.google.maps.android.data.kml.KmlContainer
import com.google.maps.android.data.kml.KmlLayer
import com.google.maps.android.data.kml.KmlPlacemark
import org.jetbrains.anko.activityUiThread
import org.jetbrains.anko.alert
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.toast
import java.net.HttpURLConnection
import java.net.URL
import java.util.*
import kotlin.collections.HashMap

class MapsActivity : AppCompatActivity(), OnMapReadyCallback, GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener, LocationListener {

    private lateinit var mMap: GoogleMap
    private lateinit var mGoogleApiClient: GoogleApiClient
    val PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 1
    var mLocationPermissionGranted = false
    private lateinit var mLastLocation: Location
    val TAG = "MapsActivity"
    var songToPlayIndexString = "01"

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
                toast("give hint")
                return true
            }
            else -> return false
        }
    }

    fun makeGuess() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Make a guess")
        builder.setMessage("Please input the song name")
        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_TEXT
        builder.setView(input)
        builder.setPositiveButton("Make Guess!") { dialog, which ->
            if (input.text.toString().toLowerCase() == songs[songToPlayIndexString.toInt()].title.toLowerCase()) {
                val builder = AlertDialog.Builder(this)
                builder.setTitle("Nice one, you guessed correctly!")
                builder.setMessage("View the full lyrics, or move to the next song?")
                builder.setPositiveButton("Next Song") { dialog, which ->
                    dialog.cancel()
                }
                builder.setNegativeButton("View Lyrics"){ dialog, which ->
                    val intent = Intent(this, SongDetailActivity::class.java)
                    intent.putExtra("SONG", songs[songToPlayIndexString.toInt()])
                    startActivity(intent)
                }
                builder.show()
            } else {
                //toast("Guess incorrect, please try again")
                val builder = AlertDialog.Builder(this)
                builder.setTitle("Sorry, that's not quite right")
                builder.setMessage("Try again?")
                builder.setPositiveButton("Try Again") { dialog, which ->
                    makeGuess()
                }
                builder.setNegativeButton("Back to map") { dialog, which ->
                    dialog.cancel()
                }
                builder.show()
            }
        }
        builder.setNegativeButton("Cancel") { dialog, which -> dialog.cancel() }
        builder.show()
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
            // Also available: HttpsURLConnection

            conn.readTimeout = 10000 // milliseconds
            conn.connectTimeout = 15000 // milliseconds
            conn.requestMethod = "GET"
            conn.doInput = true
            conn.connect()

            val layer = KmlLayer(mMap, conn.inputStream, applicationContext)

            activityUiThread {
                 /*
                    for (container: KmlContainer in containers ) {
                        if (container.hasContainers()) {
                        accessContainers(container.getContainers());
                    }
                }
                }*/

                layer.setOnFeatureClickListener { feature ->
                    feature.getProperty("name")

                    toast("${feature.id} ${feature.getProperty("name")} clicked")

                }
                layer.addLayerToMap()

            }
        }

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

    fun createLocationRequest() {
    // Set the parameters for the location request
        val mLocationRequest = LocationRequest()
        mLocationRequest.interval = 5000
        mLocationRequest.fastestInterval = 1000
        mLocationRequest.priority =
            LocationRequest.PRIORITY_HIGH_ACCURACY

        // Can we access the user’s current location?
        val permissionCheck = checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION);
        if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
            LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this)
        }
    }

    override fun onConnected(connectionHint : Bundle?) {
        try { createLocationRequest()
        } catch (ise : IllegalStateException) {
            println("IllegalStateException thrown [onConnected]")
        }
        // Can we access the user’s current location?
        if (checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mLastLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient)
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION),
                    PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION)
        }
    }

    override fun onLocationChanged(current : Location?) {
        if (current == null) {
            println("[onLocationChanged] Location unknown")
        } else {
        println("[onLocationChanged] Lat/long now (${current.latitude}, ${current.longitude})" )
        }
        // Do something with current location
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
}
