import 'dart:math';

/// Utility class for computing statistical aggregates from benchmark results.
/// Used to generate summary.csv with meaningful statistics for research.

class AggregatedStats {
  final String testName;
  final String platform;
  final int samples;
  final int minMs;
  final int maxMs;
  final double avgMs;
  final double medianMs;
  final double stdMs;
  final int p25Ms;
  final int p75Ms;
  final int p95Ms;
  final int p99Ms;
  final int successes;
  final int failures;

  static const String csvHeader = 'platform,test_name,samples,min_ms,max_ms,avg_ms,median_ms,std_ms,p25_ms,p75_ms,p95_ms,p99_ms,successes,failures';

  AggregatedStats({
    required this.testName,
    this.platform = 'flutter',
    required this.samples,
    required this.minMs,
    required this.maxMs,
    required this.avgMs,
    required this.medianMs,
    required this.stdMs,
    required this.p25Ms,
    required this.p75Ms,
    required this.p95Ms,
    required this.p99Ms,
    required this.successes,
    required this.failures,
  });

  String toCsvLine() {
    return '$platform,$testName,$samples,$minMs,$maxMs,${avgMs.toStringAsFixed(2)},${medianMs.toStringAsFixed(2)},${stdMs.toStringAsFixed(2)},$p25Ms,$p75Ms,$p95Ms,$p99Ms,$successes,$failures';
  }
}

class StatisticsUtils {
  /// Compute aggregated statistics from a list of execution times.
  static AggregatedStats aggregate(
    String testName,
    List<int> times, {
    int failures = 0,
    String platform = 'flutter',
  }) {
    if (times.isEmpty) {
      return AggregatedStats(
        testName: testName,
        platform: platform,
        samples: 0,
        minMs: 0,
        maxMs: 0,
        avgMs: 0.0,
        medianMs: 0.0,
        stdMs: 0.0,
        p25Ms: 0,
        p75Ms: 0,
        p95Ms: 0,
        p99Ms: 0,
        successes: 0,
        failures: failures,
      );
    }

    final sorted = List<int>.from(times)..sort();
    final n = sorted.length;
    final sum = sorted.reduce((a, b) => a + b);
    final avg = sum / n;

    // Standard deviation
    final variance = sorted.map((t) => (t - avg) * (t - avg)).reduce((a, b) => a + b) / n;
    final std = sqrt(variance);

    return AggregatedStats(
      testName: testName,
      platform: platform,
      samples: n + failures,
      minMs: sorted.first,
      maxMs: sorted.last,
      avgMs: avg,
      medianMs: _median(sorted),
      stdMs: std,
      p25Ms: _percentile(sorted, 0.25),
      p75Ms: _percentile(sorted, 0.75),
      p95Ms: _percentile(sorted, 0.95),
      p99Ms: _percentile(sorted, 0.99),
      successes: n,
      failures: failures,
    );
  }

  /// Compute median of a sorted list.
  static double _median(List<int> sorted) {
    if (sorted.isEmpty) return 0.0;
    final mid = sorted.length ~/ 2;
    if (sorted.length % 2 == 0) {
      return (sorted[mid - 1] + sorted[mid]) / 2.0;
    } else {
      return sorted[mid].toDouble();
    }
  }

  /// Compute percentile using nearest-rank method.
  /// [p] Percentile value between 0 and 1 (e.g., 0.95 for P95)
  static int _percentile(List<int> sorted, double p) {
    if (sorted.isEmpty) return 0;
    var rank = (p * sorted.length).toInt();
    rank = rank.clamp(0, sorted.length - 1);
    return sorted[rank];
  }
}
