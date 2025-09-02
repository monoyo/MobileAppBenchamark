package com.jossy.android.mobilebenchmarkappkotlin.data

import java.io.Serializable

data class TestResult(
    val testName: String,
    val executionTime: Long,
    val details: String,
    val isSuccessful: Boolean
) : Serializable
