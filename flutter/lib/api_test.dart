import 'package:flutter/material.dart';
import 'models/test_result.dart';
import 'services/api_service.dart';
import 'consts/config.dart';
import 'utils/buffered_csv_writer.dart';

/// API benchmark page testing network request performance.
/// Implements recursive API calling pattern similar to Java API test.
class ApiTest extends StatefulWidget {
  final int? maxDepth;
  final BufferedCsvWriter? writer;
  
  const ApiTest({
    super.key,
    this.maxDepth = 1,
    this.writer,
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
      
      for (int i = 0; i < Config.sampleCount; i++) {
        final sampleStart = DateTime.now().millisecondsSinceEpoch;
        String details = '';
        
        try {
           await _apiService.fetchPosts(timeout: const Duration(seconds: 30));
           successCount++;
           details = 'Success';
        } catch (e) {
           errorCount++;
           details = 'Error: $e';
        }
        
        final sampleEnd = DateTime.now().millisecondsSinceEpoch;
        final sampleDuration = sampleEnd - sampleStart;
        
        // Write per-sample row to CSV
        if (widget.writer != null) {
          await widget.writer!.write(
            i + 1,
            sampleDuration,
            details,
            intervalStartMs: sampleStart,
            intervalDurationMs: sampleDuration,
            cumulativeTimeMs: sampleEnd - _startTime,
          );
        }
        
        if (mounted) {
          setState(() {
            _currentIteration = i + 1;
          });
        }
        
        // Periodic flush to avoid data loss
        if (widget.writer != null && (i + 1) % 100 == 0) {
          await widget.writer!.flush();
        }
      }
      
      if (!mounted) return;

      // Flush remaining buffered data before returning
      if (widget.writer != null) {
        await widget.writer!.flush();
      }

      final elapsedMs = DateTime.now().millisecondsSinceEpoch - _startTime;
      final result = TestResult(
        'API Test',
        elapsedMs,
        'Executed ${Config.sampleCount} calls. Success: $successCount, Errors: $errorCount',
        true,
      );

      Navigator.pop(context, result);
    } catch (error) {
      if (!mounted) return;

      // Flush any buffered data before returning on error
      if (widget.writer != null) {
        await widget.writer!.flush();
      }

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
          'API Test: $_currentIteration / ${Config.sampleCount}',
          textAlign: TextAlign.center,
          style: const TextStyle(fontSize: 16),
        ),
      ),
    );
  }
}
