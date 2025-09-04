package com.jossy.android.mobilebenchmarkappkotlin

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object CPUTest {
    data class CpuResult(
        val threads: Int,
        val durationMs: Long,
        val iterations: Long,
        val checksum: Double,
    )

    suspend fun runBenchmarkParallel(
        durationMs: Long = 2500L,
        threads: Int = maxOf(1, Runtime.getRuntime().availableProcessors()),
    ): CpuResult = withContext(Dispatchers.Default) {
        val deadline = System.nanoTime() + durationMs * 1_000_000
        val counters = LongArray(threads)
        val sums = DoubleArray(threads)

        val jobs = (0 until threads).map { idx ->
            async(Dispatchers.Default) {
                var iter = 0L
                var acc = 0.0
                var x = (idx + 1).toDouble()
                while (System.nanoTime() < deadline) {
                    x = sin(x) * cos(x) + sqrt(x * x + 1.234567)
                    acc += x
                    iter++
                }
                counters[idx] = iter
                sums[idx] = acc
            }
        }

        jobs.awaitAll()
        CpuResult(
            threads = threads,
            durationMs = durationMs,
            iterations = counters.sum(),
            checksum = sums.sum(),
        )
    }
}
