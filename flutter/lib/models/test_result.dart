/// Encapsulates benchmark test execution result.
/// Contains test metrics for analysis and reporting.
class TestResult {
  final String testName;
  final int executionTimeMs;
  final String details;
  final bool success;

  TestResult(
    this.testName,
    this.executionTimeMs,
    this.details,
    this.success,
  );

  /// Returns execution time in seconds for display.
  double get executionTimeSeconds => executionTimeMs / 1000.0;

  /// Provides comprehensive result summary.
  String get summary =>
      '$testName: ${executionTimeMs}ms - ${success ? 'PASS' : 'FAIL'} ($details)';

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is TestResult &&
          runtimeType == other.runtimeType &&
          testName == other.testName &&
          executionTimeMs == other.executionTimeMs &&
          details == other.details &&
          success == other.success;

  @override
  int get hashCode =>
      testName.hashCode ^
      executionTimeMs.hashCode ^
      details.hashCode ^
      success.hashCode;

  @override
  String toString() => summary;
}
