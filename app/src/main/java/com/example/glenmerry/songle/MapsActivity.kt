package com.example.glenmerry.songle

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat.checkSelfPermission
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.location.LocationListener
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.LatLng

class MapsActivity : AppCompatActivity(), OnMapReadyCallback, GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener, LocationListener {

    private lateinit var mMap: GoogleMap
    private lateinit var mGoogleApiClient: GoogleApiClient
    val PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 1
    var mLocationPermissionGranted = false
    private lateinit var mLastLocation : Location
    val TAG = "MapsActivity"


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maps)
        var difficulty : String = "initial difficulty"

        var extras : Bundle? = intent.extras
        if (extras != null) {
            difficulty = extras.getString("DIFFICULTY")
        }

        var snackbar : Snackbar = Snackbar.make(findViewById(android.R.id.content), difficulty, Snackbar.LENGTH_LONG)
        snackbar.show()

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
        } catch (se : SecurityException) {
            println("Security exception thrown [onMapReady]")
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