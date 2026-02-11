/// Encapsulates benchmark test execution result.
/// Contains test metrics for analysis and reporting.
class TestResult {
  final String testName;
  final int executionTimeMs;
  final String details;
  final bool success;
  final DateTime timestamp;

  TestResult(
    this.testName,
    this.executionTimeMs,
    this.details,
    this.success, {
    DateTime? timestamp,
  }) : timestamp = timestamp ?? DateTime.now();

  /// Creates a TestResult from JSON models.
  factory TestResult.fromJson(Map<String, dynamic> json) {
    return TestResult(
      json['testName'] as String,
      json['executionTimeMs'] as int,
      json['details'] as String,
      json['success'] as bool,
      timestamp: json['timestamp'] != null
          ? DateTime.parse(json['timestamp'] as String)
          : null,
    );
  }

  /// Converts TestResult to JSON representation.
  Map<String, dynamic> toJson() => {
    'testName': testName,
    'executionTimeMs': executionTimeMs,
    'details': details,
    'success': success,
    'timestamp': timestamp.toIso8601String(),
  };

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
