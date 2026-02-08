import 'package:flutter/material.dart';
import 'models/test_result.dart';
import 'services/api_service.dart';

/// API benchmark page testing network request performance.
/// Implements recursive API calling pattern similar to Java API test.
class ApiTestPage extends StatefulWidget {
  final int? maxDepth;
  
  const ApiTestPage({
    super.key,
    this.maxDepth = 1,
  });

  @override
  State<ApiTestPage> createState() => _ApiTestPageState();
}

class _ApiTestPageState extends State<ApiTestPage> {
  final ApiService _apiService = ApiService();
  late final int _startTime;

  @override
  void initState() {
    super.initState();
    _startTime = DateTime.now().millisecondsSinceEpoch;
    _executeApiTest();
  }

  /// Executes API benchmark with proper error handling and resource cleanup.
  Future<void> _executeApiTest() async {
    try {
      // Fetch posts from remote API
      final posts = await _apiService.fetchPosts(timeout: const Duration(seconds: 30));
      
      if (!mounted) return;

      final elapsedMs = DateTime.now().millisecondsSinceEpoch - _startTime;
      final result = TestResult(
        'API Test',
        elapsedMs,
        'Fetched ${posts.length} posts successfully',
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
    return const Scaffold(
      body: Center(
        child: Text(
          'Fetching API data...',
          textAlign: TextAlign.center,
          style: TextStyle(fontSize: 16),
        ),
      ),
    );
  }
}
