package com.jossy.android.mobilebenchmarkappjava;

import android.util.Log;
import java.math.BigInteger;
import java.util.Random;

public class CPUTest {
    private static final int RUNS = 1_000_000;

    private static int countPrimes() {
        int count = 0;
        for (int i = 2; i <= RUNS; i++) {
            if (isPrime(i)) count++;
        }
        return count;
    }

    private static boolean isPrime(int n) {
        if (n < 2) return false;
        int sqrt = (int) Math.sqrt(n);
        for (int i = 2; i <= sqrt; i++) {
            if (n % i == 0) return false;
        }
        return true;
    }

    private static double[][] matrixMultiplication(int size) {
        Random random = new Random();
        double[][] a = new double[size][size];
        double[][] b = new double[size][size];
        double[][] result = new double[size][size];
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                a[i][j] = random.nextDouble();
                b[i][j] = random.nextDouble();
            }
        }
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                for (int k = 0; k < size; k++) {
                    result[i][j] += a[i][k] * b[k][j];
                }
            }
        }
        return result;
    }

    private static BigInteger fibonacciBig(int n) {
        if (n <= 1) return BigInteger.valueOf(n);
        BigInteger a = BigInteger.ZERO;
        BigInteger b = BigInteger.ONE;
        for (int i = 2; i <= n; i++) {
            BigInteger temp = a.add(b);
            a = b;
            b = temp;
        }
        return b;
    }

    private static double heavyMathOps(int iterations) {
        Random random = new Random();
        double result = 0.0;
        for (int i = 1; i <= iterations; i++) {
            result += Math.sqrt(i) * Math.pow(i, 1.5) / (random.nextDouble() + 1);
        }
        return result;
    }

    public static void runBenchmark() {
        int primes = countPrimes();
        double[][] matrix = matrixMultiplication(150);
        BigInteger fib = fibonacciBig(200);
        double math = heavyMathOps(500_000);
        double[] arr = new double[2_000_000];
        Random random = new Random();
        for (int i = 0; i < arr.length; i++) {
            arr[i] = random.nextDouble();
        }
        java.util.Arrays.sort(arr);
        double logSum = 0.0;
        for (int i = 1; i <= 2_000_000; i++) {
            logSum += Math.log(i) * Math.pow(i, 1.2);
        }
        Log.i(
            "CPUTest",
            "Benchmark completed: " + RUNS + " runs, counted " + primes + " primes, fib(200)=" + fib +
            ", mathOps=" + math + ", matrix[0][0]=" + matrix[0][0] + ", logSum=" + logSum + ", arr[0]=" + arr[0]
        );
    }
}