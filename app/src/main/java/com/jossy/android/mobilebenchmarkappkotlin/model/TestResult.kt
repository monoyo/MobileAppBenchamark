package com.jossy.android.mobilebenchmarkappkotlin.model

import java.io.Serializable

data class TestResult(
    val testName: String,
    val executionTime: Long,
    val details: String,
    val isSuccessful: Boolean
) : Serializable
