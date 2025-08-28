package com.jossy.android.mobilebenchmarkappkotlin

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.widget.ImageView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.bumptech.glide.Glide
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class LocationTestResult(
    val elapsedTimeMs: Long,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val provider: String,
    val altitude: Double? = null,
    val speed: Float? = null,
    val bearing: Float? = null
)

class LocationBenchmarkTest(private val context: Context) {
    private val fusedLocationClient: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)
    private val TAG = "LocationBenchmark"

    companion object {
        private const val UPDATE_INTERVAL = 1000L
        private const val FASTEST_UPDATE_INTERVAL = 500L
        private const val ACCURACY_THRESHOLD = 20f // meters
    }

    fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    // Load a static map image into an ImageView using Google Static Maps API (no API key)
    // This is a simple implementation for demonstration; for production, use an API key.
    fun loadStaticMap(imageView: ImageView, result: LocationTestResult) {
        try {
            val lat = result.latitude
            val lng = result.longitude
            val url = "https://maps.googleapis.com/maps/api/staticmap?center=$lat,$lng&zoom=15&size=600x300&markers=color:red%7C$lat,$lng"
            Glide.with(context).load(url).into(imageView)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load static map: ${e.message}")
        }
    }

    // Funkcja do jednorazowego pobrania lokalizacji
    suspend fun getCurrentLocation(): LocationTestResult = suspendCancellableCoroutine { continuation ->
        if (!hasLocationPermission()) {
            continuation.resumeWithException(SecurityException("Brak uprawnień do lokalizacji!"))
            return@suspendCancellableCoroutine
        }

        val startTime = SystemClock.elapsedRealtime()

        try {
            fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                null
            ).addOnSuccessListener { location ->
                location?.let {
                    val result = createLocationResult(it, startTime)
                    continuation.resume(result)
                } ?: continuation.resumeWithException(Exception("Nie można uzyskać lokalizacji"))
            }.addOnFailureListener { e ->
                continuation.resumeWithException(e)
            }
        } catch (e: SecurityException) {
            continuation.resumeWithException(e)
        }
    }

    // Funkcja do ciągłego monitorowania lokalizacji
    fun getLocationUpdates(): Flow<LocationTestResult> = callbackFlow {
        if (!hasLocationPermission()) {
            throw SecurityException("Brak uprawnień do lokalizacji!")
        }

        val startTime = SystemClock.elapsedRealtime()
        val locationRequest = LocationRequest.Builder(UPDATE_INTERVAL)
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .setMinUpdateIntervalMillis(FASTEST_UPDATE_INTERVAL)
            .build()

        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    if (location.accuracy <= ACCURACY_THRESHOLD) {
                        val result = createLocationResult(location, startTime)
                        trySend(result)
                    }
                }
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            ).addOnFailureListener { e ->
                close(e)
            }
        } catch (e: SecurityException) {
            close(e)
        }

        awaitClose {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
    }

    private fun createLocationResult(location: Location, startTime: Long): LocationTestResult {
        val elapsedTime = SystemClock.elapsedRealtime() - startTime

        val result = LocationTestResult(
            elapsedTimeMs = elapsedTime,
            latitude = location.latitude,
            longitude = location.longitude,
            accuracy = location.accuracy,
            provider = when {
                location.provider?.contains("gps", ignoreCase = true) == true -> "GPS"
                location.provider?.contains("network", ignoreCase = true) == true -> "Network"
                else -> location.provider ?: "Unknown"
            },
            altitude = if (location.hasAltitude()) location.altitude else null,
            speed = if (location.hasSpeed()) location.speed else null,
            bearing = if (location.hasBearing()) location.bearing else null
        )

        // Log wyników
        with(result) {
            Log.i(TAG, """
                Czas uzyskania lokalizacji: $elapsedTimeMs ms
                Szerokość: $latitude
                Długość: $longitude
                Dokładność: $accuracy m
                Dostawca: $provider
                ${altitude?.let { "Wysokość: $it m" } ?: ""}
                ${speed?.let { "Prędkość: $it m/s" } ?: ""}
                ${bearing?.let { "Kierunek: $it stopni" } ?: ""}
            """.trimIndent())
        }

        return result
    }
}
