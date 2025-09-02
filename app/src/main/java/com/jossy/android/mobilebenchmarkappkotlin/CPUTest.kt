package com.jossy.android.mobilebenchmarkappkotlin

import android.util.Log
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.random.Random

object CPUTest {
    private const val RUNS = 1_000_000

    private fun countPrimes(): Int {
        var count = 0
        for (i in 2..RUNS) {
            if (isPrime(i)) count++
        }
        return count
    }

    private fun isPrime(n: Int): Boolean {
        if (n < 2) return false
        val sqrtN = sqrt(n.toDouble()).toInt()
        for (i in 2..sqrtN) {
            if (n % i == 0) return false
        }
        return true
    }

    // Mnożenie macierzy
    private fun matrixMultiplication(size: Int): Array<DoubleArray> {
        val a = Array(size) { DoubleArray(size) { Random.nextDouble() } }
        val b = Array(size) { DoubleArray(size) { Random.nextDouble() } }
        val result = Array(size) { DoubleArray(size) }

        for (i in 0 until size) {
            for (j in 0 until size) {
                var sum = 0.0
                for (k in 0 until size) {
                    sum += a[i][k] * b[k][j]
                }
                result[i][j] = sum
            }
        }
        return result
    }

    private fun fibonacciBig(n: Int): String {
        if (n <= 1) return n.toString()
        val digits = mutableListOf(0, 1)
        for (i in 2..n) {
            var carry = 0
            for (j in digits.indices) {
                val prod = digits[j] * 1 + carry
                digits[j] = prod % 10
                carry = prod / 10
            }
            while (carry > 0) {
                digits.add(carry % 10)
                carry /= 10
            }
        }
        return digits.reversed().joinToString("")
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
            logSum += ln(i.toDouble()) * i.toDouble().pow(1.2)
        }

        Log.i(
            "CPUTest",
            "Benchmark completed: $RUNS runs, counted $primes primes, " +
                "fib(200)=$fib, mathOps=$math, " +
                "matrix[0][0]=${matrix[0][0]}, logSum=$logSum, arr[0]=${arr[0]}",
        )
    }
}
