package com.jossy.android.mobilebenchmarkappjava.data;

public class CpuResult {
        public final int threads;
        public final long durationMs;
        public final long iterations;
        public final double checksum;
        public CpuResult(int threads, long durationMs, long iterations, double checksum) {
            this.threads = threads;
            this.durationMs = durationMs;
            this.iterations = iterations;
            this.checksum = checksum;
        }
    }