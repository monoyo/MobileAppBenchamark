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

    /**
     * Uruchamia benchmark oparty na określonej liczbie iteracji (nowa metoda).
     * 
     * @param totalIterations Całkowita liczba iteracji do wykonania (rozdzielona
     *                        między wątki)
     * @param threadsOpt      Opcjonalna liczba wątków (null = auto-detect)
     * @return Wynik z czasem wykonania, liczbą iteracji i checksum
     */
    public static CpuResult runBenchmarkIterations(long totalIterations, Integer threadsOpt) {
        final int threads = (threadsOpt != null && threadsOpt > 0) ? threadsOpt
                : Math.max(1, Runtime.getRuntime().availableProcessors());

        final long iterationsPerThread = totalIterations / threads;
        final long startTime = System.nanoTime();

        final long[] counters = new long[threads];
        final double[] sums = new double[threads];
        List<Thread> ts = new ArrayList<>(threads);

        for (int idx = 0; idx < threads; idx++) {
            final int threadIndex = idx;
            Thread t = new Thread(() -> {
                long iter = 0L;
                double acc = 0.0;
                double x = (threadIndex + 1);

                // Pętla iteracyjna zamiast czasowej
                try {
                    for (long i = 0; i < iterationsPerThread; i++) {
                        x = Math.sin(x) * Math.cos(x) + Math.sqrt(x * x + 1.234567);
                        acc += x;
                        iter++;
                    }
                } catch (Exception e) {
                    System.err.println("Thread " + threadIndex + " crashed: " + e);
                }

                counters[threadIndex] = iter;
                sums[threadIndex] = acc;
            }, "cpu-iter-" + idx);
            t.setPriority(Thread.NORM_PRIORITY);
            ts.add(t);
            t.start();
        }

        for (Thread t : ts) {
            try {
                t.join();
            } catch (InterruptedException ignored) {
            }
        }

        long endTime = System.nanoTime();
        long durationMs = (endTime - startTime) / 1_000_000L;

        long actualIterations = 0L;
        double checksum = 0.0;
        for (int i = 0; i < threads; i++) {
            actualIterations += counters[i];
            checksum += sums[i];
        }

        return new CpuResult(threads, durationMs, actualIterations, checksum);
    }

    /**
     * Uruchamia benchmark oparty na czasie (oryginalna metoda - zachowana dla
     * kompatybilności).
     * 
     * @param durationMs Czas trwania testu w milisekundach
     * @param threadsOpt Opcjonalna liczba wątków (null = auto-detect)
     * @return Wynik z czasem wykonania, liczbą iteracji i checksum
     */
    public static CpuResult runBenchmarkParallel(long durationMs, Integer threadsOpt) {
        final int threads = (threadsOpt != null && threadsOpt > 0) ? threadsOpt
                : Math.max(1, Runtime.getRuntime().availableProcessors());
        final long deadline = System.nanoTime() + durationMs * 1_000_000L;

        final long[] counters = new long[threads];
        final double[] sums = new double[threads];
        List<Thread> ts = new ArrayList<>(threads);

        for (int idx = 0; idx < threads; idx++) {
            final int threadIndex = idx;
            Thread t = new Thread(() -> {
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
            }, "cpu-burn-" + idx);
            t.setPriority(Thread.NORM_PRIORITY);
            ts.add(t);
            t.start();
        }

        for (Thread t : ts) {
            try {
                t.join();
            } catch (InterruptedException ignored) {
            }
        }

        long totalIterations = 0L;
        double checksum = 0.0;
        for (int i = 0; i < threads; i++) {
            totalIterations += counters[i];
            checksum += sums[i];
        }

        return new CpuResult(threads, durationMs, totalIterations, checksum);
    }
}
