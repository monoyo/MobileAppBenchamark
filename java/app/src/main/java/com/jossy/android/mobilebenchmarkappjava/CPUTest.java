package com.jossy.android.mobilebenchmarkappjava;

import com.jossy.android.mobilebenchmarkappjava.data.CpuResult;

import java.util.ArrayList;
import java.util.List;

/**
 * Test wydajności CPU z obsługą dwóch trybów:
 * 1. Time-based (oryginalna metoda) - pętla działa przez określony czas
 * 2. Iteration-based (nowa metoda) - pętla wykonuje określoną liczbę iteracji
 * 
 * Optymalizacje:
 * - Minimalna alokacja pamięci w pętli (tylko prymitywy)
 * - Operacje FP (sin, cos, sqrt) intensywnie obciążające CPU
 * - Checksum zapobiegający optymalizacji przez kompilator
 */
public class CPUTest {

    private static final int THREAD_PRIORITY = Thread.NORM_PRIORITY;

    /**
     * Uruchamia benchmark oparty na określonej liczbie iteracji (nowa metoda).
     */
    public static CpuResult runBenchmarkIterations(long totalIterations, Integer threadsOpt) {
        final int threads = getThreadCount(threadsOpt);
        final long iterationsPerThread = totalIterations / threads;
        final long startTime = System.nanoTime();

        final long[] counters = new long[threads];
        final double[] sums = new double[threads];
        List<Thread> threadList = createWorkerThreads(threads, iterationsPerThread, counters, sums, 
            (threadIndex, iterations, countersArray, sumsArray) -> runIterationBased(threadIndex, iterations, countersArray, sumsArray));

        startAndJoinThreads(threadList);

        long endTime = System.nanoTime();
        long durationMs = (endTime - startTime) / 1_000_000L;
        long actualIterations = sumCounters(counters);
        double checksum = sumSums(sums);

        return new CpuResult(threads, durationMs, actualIterations, checksum);
    }

    /**
     * Uruchamia benchmark oparty na czasie (oryginalna metoda).
     */
    public static CpuResult runBenchmarkParallel(long durationMs, Integer threadsOpt) {
        final int threads = getThreadCount(threadsOpt);
        final long deadline = System.nanoTime() + durationMs * 1_000_000L;

        final long[] counters = new long[threads];
        final double[] sums = new double[threads];
        List<Thread> threadList = createWorkerThreads(threads, deadline, counters, sums,
            (threadIndex, deadline1, countersArray, sumsArray) -> runTimeBased(threadIndex, deadline1, countersArray, sumsArray));

        startAndJoinThreads(threadList);

        long totalIterations = sumCounters(counters);
        double checksum = sumSums(sums);

        return new CpuResult(threads, durationMs, totalIterations, checksum);
    }

    private static int getThreadCount(Integer threadsOpt) {
        return (threadsOpt != null && threadsOpt > 0) ? threadsOpt
                : Math.max(1, Runtime.getRuntime().availableProcessors());
    }

    private static List<Thread> createWorkerThreads(int threadCount, long parameter, 
            long[] counters, double[] sums, ThreadWorker worker) {
        List<Thread> threads = new ArrayList<>(threadCount);
        
        for (int idx = 0; idx < threadCount; idx++) {
            final int threadIndex = idx;
            Thread t = new Thread(() -> 
                worker.execute(threadIndex, parameter, counters, sums),
                "cpu-worker-" + idx);
            t.setPriority(THREAD_PRIORITY);
            threads.add(t);
        }
        
        return threads;
    }

    private static void startAndJoinThreads(List<Thread> threads) {
        for (Thread t : threads) {
            t.start();
        }
        
        for (Thread t : threads) {
            try {
                t.join();
            } catch (InterruptedException ignored) {
            }
        }
    }

    private static long sumCounters(long[] counters) {
        long sum = 0L;
        for (long counter : counters) {
            sum += counter;
        }
        return sum;
    }

    private static double sumSums(double[] sums) {
        double sum = 0.0;
        for (double s : sums) {
            sum += s;
        }
        return sum;
    }

    private static void runIterationBased(int threadIndex, long iterations, long[] counters, double[] sums) {
        long iter = 0L;
        double acc = 0.0;
        double x = (threadIndex + 1);

        try {
            for (long i = 0; i < iterations; i++) {
                x = Math.sin(x) * Math.cos(x) + Math.sqrt(x * x + 1.234567);
                acc += x;
                iter++;
            }
        } catch (Exception e) {
            System.err.println("Thread " + threadIndex + " crashed: " + e);
        }

        counters[threadIndex] = iter;
        sums[threadIndex] = acc;
    }

    private static void runTimeBased(int threadIndex, long deadline, long[] counters, double[] sums) {
        long iter = 0L;
        double acc = 0.0;
        double x = (threadIndex + 1);
        
        while (System.nanoTime() < deadline) {
            x = Math.sin(x) * Math.cos(x) + Math.sqrt(x * x + 1.234567);
            acc += x;
            iter++;
        }
        
        counters[threadIndex] = iter;
        sums[threadIndex] = acc;
    }

    @FunctionalInterface
    private interface ThreadWorker {
        void execute(int threadIndex, long parameter, long[] counters, double[] sums);
    }
}
