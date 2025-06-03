package com.jossy.android.mobilebenchmarkappkotlin

import kotlin.math.sqrt

object CPUTest {
    private const val RUNS = 1000

    private fun countPrimes(limit: Int): Int {
        var count = 0
        for (i in 2..limit) {
            if (isPrime(i)) count++
        }
        return count
    }

    private fun isPrime(n: Int): Boolean {
        if (n < 2) return false
        for (i in 2..sqrt(n.toDouble()).toInt()) {
            if (n % i == 0) return false
        }
        return true
    }

    fun runBenchmark() {
        for (i in 0 until RUNS) {
            countPrimes(300_000)
        }
    }
}
