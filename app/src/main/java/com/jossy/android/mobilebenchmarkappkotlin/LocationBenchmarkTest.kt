package com.jossy.android.mobilebenchmarkappkotlin

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
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
    val bearing: Float? = null,
)

class LocationBenchmarkTest(
    private val context: Context,
) {
    private val fusedLocationClient: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)

    companion object {
        private const val TAG = "LocationBenchmark"
    }

    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        return fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED
    }

    suspend fun getCurrentLocation(): LocationTestResult =
        suspendCancellableCoroutine { continuation ->
            if (!hasLocationPermission()) {
                continuation.resumeWithException(SecurityException("Brak uprawnień do lokalizacji!"))
                return@suspendCancellableCoroutine
            }

            val startTime = SystemClock.elapsedRealtime()

            try {
                fusedLocationClient
                    .getCurrentLocation(
                        Priority.PRIORITY_HIGH_ACCURACY,
                        null,
                    ).addOnSuccessListener { location ->
                        if (location != null) {
                            continuation.resume(createLocationResult(location, startTime))
                        } else {
                            // fallback: lastLocation
                            fusedLocationClient.lastLocation.addOnSuccessListener { lastLoc ->
                                if (lastLoc != null) {
                                    continuation.resume(createLocationResult(lastLoc, startTime))
                                } else {
                                    continuation.resumeWithException(Exception("Nie można uzyskać lokalizacji"))
                                }
                            }
                        }
                    }.addOnFailureListener { e ->
                        continuation.resumeWithException(e)
                    }
            } catch (e: SecurityException) {
                continuation.resumeWithException(e)
            }
        }

    private fun createLocationResult(
        location: Location,
        startTime: Long,
    ): LocationTestResult {
        val elapsedTime = SystemClock.elapsedRealtime() - startTime

        val result =
            LocationTestResult(
                elapsedTimeMs = elapsedTime,
                latitude = location.latitude,
                longitude = location.longitude,
                accuracy = location.accuracy,
                provider =
                    when {
                        location.provider?.contains("gps", ignoreCase = true) == true -> "GPS"
                        location.provider?.contains("network", ignoreCase = true) == true -> "Network"
                        else -> location.provider ?: "Unknown"
                    },
                altitude = if (location.hasAltitude()) location.altitude else null,
                speed = if (location.hasSpeed()) location.speed else null,
                bearing = if (location.hasBearing()) location.bearing else null,
            )

        // Log wyników
        with(result) {
            Log.i(
                TAG,
                """
                Czas uzyskania lokalizacji: $elapsedTimeMs ms
                Szerokość: $latitude
                Długość: $longitude
                Dokładność: $accuracy m
                Dostawca: $provider
                ${altitude?.let { "Wysokość: $it m" } ?: ""}
                ${speed?.let { "Prędkość: $it m/s" } ?: ""}
                ${bearing?.let { "Kierunek: $it stopni" } ?: ""}
                """.trimIndent(),
            )
        }

        return result
    }
}
