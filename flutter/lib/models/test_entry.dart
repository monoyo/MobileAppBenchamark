import 'test_result.dart';

/// Encapsulates a single benchmark test iteration entry.
/// Contains timing data for each iteration of a test.
class TestEntry {
  final int iteration;
  final TestResult result;
  final int intervalStartMs;
  final int intervalDurationMs;
  final int cumulativeTimeMs;

  TestEntry(
    this.iteration,
    this.result, {
    int? intervalStartMs,
    int? intervalDurationMs,
    int? cumulativeTimeMs,
  })  : intervalStartMs = intervalStartMs ?? 0,
        intervalDurationMs = intervalDurationMs ?? result.executionTimeMs,
        cumulativeTimeMs = cumulativeTimeMs ?? result.executionTimeMs;

  @override
  String toString() =>
      'TestEntry(iteration=$iteration, executionTimeMs=${result.executionTimeMs}, '
      'intervalStart=$intervalStartMs, intervalDuration=$intervalDurationMs, '
      'cumulative=$cumulativeTimeMs)';
}