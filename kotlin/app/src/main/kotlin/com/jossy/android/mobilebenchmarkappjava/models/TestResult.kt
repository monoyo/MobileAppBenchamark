package com.jossy.android.mobilebenchmarkappjava.data

import java.io.Serializable

data class TestResult(
    val testName: String,
    val executionTime: Long,
    val details: String,
    val success: Boolean
) : Serializable
