package com.jossy.android.mobilebenchmarkappjava.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Utility class for computing statistical aggregates from benchmark results.
 * Used to generate summary.csv with meaningful statistics for research.
 */
public final class StatisticsUtils {

    private StatisticsUtils() {}

    public static class AggregatedStats {
        public final String testName;
        public final String platform;
        public final int samples;
        public final long minMs;
        public final long maxMs;
        public final double avgMs;
        public final double medianMs;
        public final double stdMs;
        public final long p25Ms;
        public final long p75Ms;
        public final long p95Ms;
        public final long p99Ms;
        public final int successes;
        public final int failures;

        public static final String CSV_HEADER = "platform,test_name,samples,min_ms,max_ms,avg_ms,median_ms,std_ms,p25_ms,p75_ms,p95_ms,p99_ms,successes,failures";

        public AggregatedStats(String testName, String platform, int samples,
                               long minMs, long maxMs, double avgMs, double medianMs,
                               double stdMs, long p25Ms, long p75Ms, long p95Ms, long p99Ms,
                               int successes, int failures) {
            this.testName = testName;
            this.platform = platform;
            this.samples = samples;
            this.minMs = minMs;
            this.maxMs = maxMs;
            this.avgMs = avgMs;
            this.medianMs = medianMs;
            this.stdMs = stdMs;
            this.p25Ms = p25Ms;
            this.p75Ms = p75Ms;
            this.p95Ms = p95Ms;
            this.p99Ms = p99Ms;
            this.successes = successes;
            this.failures = failures;
        }

        public String toCsvLine() {
            return String.format("%s,%s,%d,%d,%d,%.2f,%.2f,%.2f,%d,%d,%d,%d,%d,%d",
                    platform, testName, samples, minMs, maxMs, avgMs, medianMs, stdMs,
                    p25Ms, p75Ms, p95Ms, p99Ms, successes, failures);
        }
    }

    /**
     * Compute aggregated statistics from a list of execution times.
     */
    public static AggregatedStats aggregate(String testName, List<Long> times, int failures, String platform) {
        if (times == null || times.isEmpty()) {
            return new AggregatedStats(testName, platform, 0, 0, 0, 0.0, 0.0, 0.0, 0, 0, 0, 0, 0, failures);
        }

        List<Long> sorted = new ArrayList<>(times);
        Collections.sort(sorted);

        int n = sorted.size();
        long sum = 0;
        for (Long t : sorted) sum += t;
        double avg = (double) sum / n;

        // Standard deviation
        double variance = 0;
        for (Long t : sorted) {
            variance += (t - avg) * (t - avg);
        }
        variance /= n;
        double std = Math.sqrt(variance);

        return new AggregatedStats(
                testName,
                platform,
                n + failures,
                sorted.get(0),
                sorted.get(n - 1),
                avg,
                median(sorted),
                std,
                percentile(sorted, 0.25),
                percentile(sorted, 0.75),
                percentile(sorted, 0.95),
                percentile(sorted, 0.99),
                n,
                failures
        );
    }

    public static AggregatedStats aggregate(String testName, List<Long> times, int failures) {
        return aggregate(testName, times, failures, "java");
    }

    /**
     * Compute median of a sorted list.
     */
    private static double median(List<Long> sorted) {
        if (sorted.isEmpty()) return 0.0;
        int mid = sorted.size() / 2;
        if (sorted.size() % 2 == 0) {
            return (sorted.get(mid - 1) + sorted.get(mid)) / 2.0;
        } else {
            return sorted.get(mid);
        }
    }

    /**
     * Compute percentile using nearest-rank method.
     * @param p Percentile value between 0 and 1 (e.g., 0.95 for P95)
     */
    private static long percentile(List<Long> sorted, double p) {
        if (sorted.isEmpty()) return 0;
        int rank = (int) (p * sorted.size());
        rank = Math.max(0, Math.min(sorted.size() - 1, rank));
        return sorted.get(rank);
    }
}
