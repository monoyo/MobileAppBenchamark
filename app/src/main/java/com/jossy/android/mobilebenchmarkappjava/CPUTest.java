package com.jossy.android.mobilebenchmarkappjava;

import com.jossy.android.mobilebenchmarkappjava.data.CpuResult;

import java.util.ArrayList;
import java.util.List;

public class CPUTest {

    public static CpuResult runBenchmarkParallel(long durationMs, Integer threadsOpt) {
        final int threads = (threadsOpt != null && threadsOpt > 0) ? threadsOpt :
                Math.max(1, Runtime.getRuntime().availableProcessors());
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
            try { t.join(); } catch (InterruptedException ignored) { }
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