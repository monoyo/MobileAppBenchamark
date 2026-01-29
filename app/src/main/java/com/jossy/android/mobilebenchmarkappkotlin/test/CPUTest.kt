package com.jossy.android.mobilebenchmarkappkotlin.test

import com.jossy.android.mobilebenchmarkappkotlin.model.CpuResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object CPUTest {

    /**
     * Test CPU oparty na ITERACJACH (zalecany dla benchmarków).
     * Deterministyczne obciążenie - niezależne od throttlingu CPU.
     *
     * @param totalIterations Całkowita liczba iteracji do wykonania
     * @param threads Liczba wątków (domyślnie = liczba rdzeni)
     */
    suspend fun runBenchmarkIterations(
        totalIterations: Long,
        threads: Int = maxOf(1, Runtime.getRuntime().availableProcessors())
    ): CpuResult = withContext(Dispatchers.Default) {
        val startTime = System.nanoTime()
        val iterationsPerThread = totalIterations / threads
        val counters = LongArray(threads)
        val sums = DoubleArray(threads)

        val jobs = (0 until threads).map { idx ->
            async(Dispatchers.Default) {
                var acc = 0.0
                var x = (idx + 1).toDouble()
                
                for (i in 0 until iterationsPerThread) {
                    x = sin(x) * cos(x) + sqrt(x * x + 1.234567)
                    acc += x
                }
                
                counters[idx] = iterationsPerThread
                sums[idx] = acc
            }
        }

        jobs.awaitAll()
        val durationMs = (System.nanoTime() - startTime) / 1_000_000
        
        CpuResult(
            threads = threads,
            durationMs = durationMs,
            iterations = counters.sum(),
            checksum = sums.sum()
        )
    }

    /**
     * Test CPU oparty na CZASIE (zachowany dla kompatybilności).
     * Wykonuje obliczenia przez określony czas.
     */
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
