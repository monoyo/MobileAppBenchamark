class TestResult {
  final String testName;
  final int executionTimeMs;
  final String details;
  final bool success;

  TestResult(this.testName, this.executionTimeMs, this.details, this.success);

  Map<String, dynamic> toJson() => {
        'testName': testName,
        'executionTimeMs': executionTimeMs,
        'details': details,
        'success': success,
      };

  @override
  String toString() => '$testName: ${executionTimeMs}ms ($details)';
}
