package com.jossy.android.mobilebenchmarkappkotlin.data

data class TestEntry(
    val iteration: Int,
    val result: TestResult,
    val intervalStartMs: Long = System.currentTimeMillis(),
    val intervalDurationMs: Long = result.executionTimeMs,
    val cumulativeTimeMs: Long = result.executionTimeMs
) {
    override fun toString(): String =
        "TestEntry(iteration=$iteration, executionTime=${result.executionTimeMs}, " +
        "intervalDuration=$intervalDurationMs, cumulative=$cumulativeTimeMs)"
}
