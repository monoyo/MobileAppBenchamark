package com.jossy.android.mobilebenchmarkappkotlin.model

/**
 * Wpis wyniku testu z rozszerzonymi danymi czasowymi.
 * Umożliwia analizę wydajności poszczególnych interwałów.
 */
data class TestEntry(
    val iteration: Int,
    val result: TestResult,
    val intervalStartMs: Long = 0,
    val intervalDurationMs: Long = 0,
    val cumulativeTimeMs: Long = 0
)
