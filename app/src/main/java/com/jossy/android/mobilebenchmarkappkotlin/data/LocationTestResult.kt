package com.jossy.android.mobilebenchmarkappkotlin.data

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