package com.jossy.android.mobilebenchmarkappjava.data

data class TestEntry(
    val iteration: Int,
    val result: TestResult,
    val intervalStartMs: Long = System.currentTimeMillis(),
    val intervalDurationMs: Long = result.executionTime,
    val cumulativeTimeMs: Long = result.executionTime
) {
    override fun toString(): String =
        "TestEntry(iteration=$iteration, executionTime=${result.executionTime}, " +
        "intervalDuration=$intervalDurationMs, cumulative=$cumulativeTimeMs)"
}
