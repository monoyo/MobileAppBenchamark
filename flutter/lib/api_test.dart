import 'package:flutter/material.dart';
import 'models/test_result.dart';
import 'services/api_service.dart';
import 'consts/config.dart';

/// API benchmark page testing network request performance.
/// Implements recursive API calling pattern similar to Java API test.
class ApiTest extends StatefulWidget {
  final int? maxDepth;
  
  const ApiTest({
    super.key,
    this.maxDepth = 1,
  });

  @override
  State<ApiTest> createState() => _ApiTestState();
}

class _ApiTestState extends State<ApiTest> {
  final ApiService _apiService = ApiService();
  late final int _startTime;
  int _currentIteration = 0;

  @override
  void initState() {
    super.initState();
    _startTime = DateTime.now().millisecondsSinceEpoch;
    _executeApiTest();
  }

  /// Executes API benchmark with proper error handling and resource cleanup.
  Future<void> _executeApiTest() async {
    try {
      int successCount = 0;
      int errorCount = 0;
      
      for (int i = 0; i < SampleConfig.sampleCount; i++) {
        try {
           // Fetch posts from remote API
           await _apiService.fetchPosts(timeout: const Duration(seconds: 30));
           successCount++;
        } catch (_) {
           errorCount++;
        }
        
        if (mounted) {
          setState(() {
            _currentIteration = i + 1;
          });
        }
      }
      
      if (!mounted) return;

      final elapsedMs = DateTime.now().millisecondsSinceEpoch - _startTime;
      final result = TestResult(
        'API Test',
        elapsedMs,
        'Executed ${SampleConfig.sampleCount} calls. Success: $successCount, Errors: $errorCount',
        true,
      );

      Navigator.pop(context, result);
    } catch (error) {
      if (!mounted) return;

      final elapsedMs = DateTime.now().millisecondsSinceEpoch - _startTime;
      final errorMessage = error is Exception ? error.toString() : 'Unknown error';
      final result = TestResult(
        'API Test',
        elapsedMs,
        'Failed: $errorMessage',
        false,
      );

      Navigator.pop(context, result);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Center(
        child: Text(
          'API Test: $_currentIteration / ${SampleConfig.sampleCount}',
          textAlign: TextAlign.center,
          style: const TextStyle(fontSize: 16),
        ),
      ),
    );
  }
}
