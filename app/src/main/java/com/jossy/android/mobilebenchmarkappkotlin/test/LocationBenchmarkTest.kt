package com.jossy.android.mobilebenchmarkappkotlin.test

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.jossy.android.mobilebenchmarkappkotlin.model.TestResult
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

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

    suspend fun getCurrentLocation(): TestResult =
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
    ): TestResult {
        val elapsedTime = SystemClock.elapsedRealtime() - startTime

        val result =
            TestResult(
                testName = "Location Test",
                executionTime = elapsedTime,
                details = "Accuracy: ${location.accuracy}m, Provider: ${location.provider}",
                isSuccessful = true,
            )
        return result
    }
}