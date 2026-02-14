package com.jossy.android.mobilebenchmarkappkotlin.data

import java.io.Serializable

data class TestResult(
    val testName: String,
    val executionTimeMs: Long,
    val details: String,
    val success: Boolean
) : Serializable
