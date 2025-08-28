package com.jossy.android.mobilebenchmarkappkotlin

import android.util.Log
import java.math.BigInteger
import java.util.Random

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
        val sqrt = Math.sqrt(n.toDouble()).toInt()
        for (i in 2..sqrt) {
            if (n % i == 0) return false
        }
        return true
    }

    private fun matrixMultiplication(size: Int): Array<DoubleArray> {
        val random = Random()
        val a = Array(size) { DoubleArray(size) }
        val b = Array(size) { DoubleArray(size) }
        val result = Array(size) { DoubleArray(size) }
        for (i in 0 until size) {
            for (j in 0 until size) {
                a[i][j] = random.nextDouble()
                b[i][j] = random.nextDouble()
            }
        }
        for (i in 0 until size) {
            for (j in 0 until size) {
                for (k in 0 until size) {
                    result[i][j] += a[i][k] * b[k][j]
                }
            }
        }
        return result
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
        val random = Random()
        var result = 0.0
        for (i in 1..iterations) {
            result += Math.sqrt(i.toDouble()) * Math.pow(i.toDouble(), 1.5) / (random.nextDouble() + 1)
        }
        return result
    }

    fun runBenchmark() {
        val primes = countPrimes()
        val matrix = matrixMultiplication(150)
        val fib = fibonacciBig(200)
        val math = heavyMathOps(500_000)
        val arr = DoubleArray(2_000_000)
        val random = Random()
        for (i in arr.indices) arr[i] = random.nextDouble()
        java.util.Arrays.sort(arr)
        var logSum = 0.0
        for (i in 1..2_000_000) logSum += Math.log(i.toDouble()) * Math.pow(i.toDouble(), 1.2)
        Log.i("CPUTest", "Benchmark completed: $RUNS runs, counted $primes primes, fib(200)=$fib, mathOps=$math, matrix[0][0]=${matrix[0][0]}, logSum=$logSum, arr[0]=${arr[0]}")
    }
}
