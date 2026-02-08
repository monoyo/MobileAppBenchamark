package com.jossy.android.mobilebenchmarkappjava.utils

import kotlin.math.sqrt

/**
 * Utility class for computing statistical aggregates from benchmark results.
 * Used to generate summary.csv with meaningful statistics for research.
 */
object StatisticsUtils {

    data class AggregatedStats(
        val testName: String,
        val platform: String = "kotlin",
        val samples: Int,
        val minMs: Long,
        val maxMs: Long,
        val avgMs: Double,
        val medianMs: Double,
        val stdMs: Double,
        val p25Ms: Long,
        val p75Ms: Long,
        val p95Ms: Long,
        val p99Ms: Long,
        val successes: Int,
        val failures: Int
    ) {
        fun toCsvLine(): String {
            return "$platform,$testName,$samples,$minMs,$maxMs,${String.format("%.2f", avgMs)}," +
                    "${String.format("%.2f", medianMs)},${String.format("%.2f", stdMs)}," +
                    "$p25Ms,$p75Ms,$p95Ms,$p99Ms,$successes,$failures"
        }

        companion object {
            const val CSV_HEADER = "platform,test_name,samples,min_ms,max_ms,avg_ms,median_ms,std_ms,p25_ms,p75_ms,p95_ms,p99_ms,successes,failures"
        }
    }

    /**
     * Compute aggregated statistics from a list of execution times.
     */
    fun aggregate(
        testName: String,
        times: List<Long>,
        failures: Int = 0,
        platform: String = "kotlin"
    ): AggregatedStats {
        if (times.isEmpty()) {
            return AggregatedStats(
                testName = testName,
                platform = platform,
                samples = 0,
                minMs = 0,
                maxMs = 0,
                avgMs = 0.0,
                medianMs = 0.0,
                stdMs = 0.0,
                p25Ms = 0,
                p75Ms = 0,
                p95Ms = 0,
                p99Ms = 0,
                successes = 0,
                failures = failures
            )
        }

        val sorted = times.sorted()
        val n = sorted.size
        val sum = sorted.sum()
        val avg = sum.toDouble() / n

        // Standard deviation
        val variance = sorted.map { (it - avg) * (it - avg) }.sum() / n
        val std = sqrt(variance)

        return AggregatedStats(
            testName = testName,
            platform = platform,
            samples = n + failures,
            minMs = sorted.first(),
            maxMs = sorted.last(),
            avgMs = avg,
            medianMs = median(sorted),
            stdMs = std,
            p25Ms = percentile(sorted, 0.25),
            p75Ms = percentile(sorted, 0.75),
            p95Ms = percentile(sorted, 0.95),
            p99Ms = percentile(sorted, 0.99),
            successes = n,
            failures = failures
        )
    }

    /**
     * Compute median of a sorted list.
     */
    private fun median(sorted: List<Long>): Double {
        if (sorted.isEmpty()) return 0.0
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 0) {
            (sorted[mid - 1] + sorted[mid]) / 2.0
        } else {
            sorted[mid].toDouble()
        }
    }

    /**
     * Compute percentile using nearest-rank method.
     * @param p Percentile value between 0 and 1 (e.g., 0.95 for P95)
     */
    private fun percentile(sorted: List<Long>, p: Double): Long {
        if (sorted.isEmpty()) return 0
        val rank = (p * sorted.size).toInt().coerceIn(0, sorted.size - 1)
        return sorted[rank]
    }
}
