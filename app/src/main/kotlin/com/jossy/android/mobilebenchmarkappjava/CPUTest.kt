package com.jossy.android.mobilebenchmarkappjava

import com.jossy.android.mobilebenchmarkappjava.data.CpuResult
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object CPUTest {
    private const val THREAD_PRIORITY = 5

    fun runBenchmarkIterations(totalIterations: Long, threadsOpt: Int? = null): CpuResult {
        val threads = getThreadCount(threadsOpt)
        val iterationsPerThread = totalIterations / threads
        val startTime = System.nanoTime()

        val counters = LongArray(threads)
        val sums = DoubleArray(threads)

        (0 until threads).map { threadIndex ->
            Thread {
                runIterationBased(threadIndex, iterationsPerThread, counters, sums)
            }.apply {
                priority = THREAD_PRIORITY
                name = "cpu-worker-$threadIndex"
            }
        }.forEach { thread ->
            thread.start()
            thread.join()
        }

        val endTime = System.nanoTime()
        val durationMs = (endTime - startTime) / 1_000_000L
        val actualIterations = counters.sum()
        val checksum = sums.sum()

        return CpuResult(threads, durationMs, actualIterations, checksum)
    }

    fun runBenchmarkParallel(durationMs: Long, threadsOpt: Int? = null): CpuResult {
        val threads = getThreadCount(threadsOpt)
        val deadline = System.nanoTime() + durationMs * 1_000_000L

        val counters = LongArray(threads)
        val sums = DoubleArray(threads)

        (0 until threads).map { threadIndex ->
            Thread {
                runTimeBased(threadIndex, deadline, counters, sums)
            }.apply {
                priority = THREAD_PRIORITY
                name = "cpu-worker-$threadIndex"
            }
        }.forEach { thread ->
            thread.start()
            thread.join()
        }

        val totalIterations = counters.sum()
        val checksum = sums.sum()

        return CpuResult(threads, durationMs, totalIterations, checksum)
    }

    private fun getThreadCount(threadsOpt: Int?): Int =
        if (threadsOpt != null && threadsOpt > 0) threadsOpt
        else maxOf(1, Runtime.getRuntime().availableProcessors())

    private fun runIterationBased(
        threadIndex: Int,
        iterations: Long,
        counters: LongArray,
        sums: DoubleArray
    ) {
        var iter = 0L
        var acc = 0.0
        var x = (threadIndex + 1).toDouble()

        repeat(iterations.toInt()) {
            x = sin(x) * cos(x) + sqrt(x * x + 1.234567)
            acc += x
            iter++
        }

        counters[threadIndex] = iter
        sums[threadIndex] = acc
    }

    private fun runTimeBased(
        threadIndex: Int,
        deadline: Long,
        counters: LongArray,
        sums: DoubleArray
    ) {
        var iter = 0L
        var acc = 0.0
        var x = (threadIndex + 1).toDouble()

        while (System.nanoTime() < deadline) {
            x = sin(x) * cos(x) + sqrt(x * x + 1.234567)
            acc += x
            iter++
        }

        counters[threadIndex] = iter
        sums[threadIndex] = acc
    }
}
