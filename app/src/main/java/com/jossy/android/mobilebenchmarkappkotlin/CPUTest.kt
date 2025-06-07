package com.jossy.android.mobilebenchmarkappkotlin

import android.util.Log
import java.lang.Math.pow
import java.math.BigInteger
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.random.Random

object CPUTest {
    private const val RUNS = 1000000

    private fun countPrimes(): Int {
        var count = 0
        for (i in 2..RUNS) {
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

    private fun matrixMultiplication(size: Int): Array<DoubleArray> {
        val a = Array(size) { DoubleArray(size) { Random.nextDouble() } }
        val b = Array(size) { DoubleArray(size) { Random.nextDouble() } }
        val result = Array(size) { DoubleArray(size) }
        for (i in 0 until size) {
            for (j in 0 until size) {
                for (k in 0 until size) {
                    result[i][j] += a[i][k] * b[k][j]
                }
            }
        }
        return result
    }

    private fun fibonacci(n: Int): Long {
        if (n <= 1) return n.toLong()
        var a = 0L
        var b = 1L
        for (i in 2..n) {
            val temp = a + b
            a = b
            b = temp
        }
        return b
    }

    private fun fibonacciBig(n: Int): BigInteger {
        if (n <= 1) return BigInteger.valueOf(n.toLong())
        var a = BigInteger.ZERO
        var b = BigInteger.ONE
        for (i in 2..n) {
            val temp = a.add(b)
            a = b
            b = temp
        }
        return b
    }

    private fun heavyMathOps(iterations: Int): Double {
        var result = 0.0
        for (i in 1..iterations) {
            result += sqrt(i.toDouble()) * i.toDouble().pow(1.5) / (Random.nextDouble() + 1)
        }
        return result
    }

    fun runBenchmark() {
        val primes = countPrimes()
        val matrix = matrixMultiplication(150)
        val fib = fibonacciBig(200)
        val math = heavyMathOps(500_000)
        val arr = DoubleArray(2_000_000) { Random.nextDouble() }
        arr.sort()
        var logSum = 0.0
        for (i in 1..2_000_000) {
            logSum += kotlin.math.ln(i.toDouble()) * pow(i.toDouble(), 1.2)
        }
        Log.i(
            "CPUTest",
            "Benchmark completed: $RUNS runs, counted $primes primes, fib(200)=$fib, mathOps=$math, matrix[0][0]=${matrix[0][0]}, logSum=$logSum, arr[0]=${arr[0]}",
        )
    }
}
