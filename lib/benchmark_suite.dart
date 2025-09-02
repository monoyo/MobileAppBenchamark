import 'dart:io';
import 'package:flutter/material.dart';
import 'models/test_result.dart';
import 'ui_test.dart';
import 'cpu_test.dart';
import 'ram_test.dart';
import 'image_loading_test.dart';
import 'api_test.dart';
import 'location_test.dart';
import 'package:path_provider/path_provider.dart';

class BenchmarkSuitePage extends StatefulWidget {
  const BenchmarkSuitePage({super.key});

  @override
  State<BenchmarkSuitePage> createState() => _BenchmarkSuitePageState();
}

class _BenchmarkSuitePageState extends State<BenchmarkSuitePage> {
  final List<TestResult> _results = [];
  int _iteration = 0;
  int _testIndex = 0;
  bool _running = false;
  final int _iterations = 3;
  String? _lastSavedPath;
  bool _disposed = false;

  final List<String> _testNames = [
    'UI Test',
    'CPU Test',
    'RAM Test',
    'Image Loading Test',
    'API Test',
    'Location Test',
  ];

  void _startSuite() async {
    setState(() {
      _running = true;
      _results.clear();
      _iteration = 0;
      _testIndex = 0;
    });
    await _runNext();
  }

  @override
  void dispose() {
    _disposed = true;
    super.dispose();
  }

Future<void> _runNext() async {
  if (!mounted || _disposed) return;

  if (_iteration >= _iterations) return _finishSuite();

  if (_testIndex >= _testNames.length) {
    _testIndex = 0;
    _iteration++;
    if (_iteration >= _iterations) return _finishSuite();
  }

  // Odśwież UI
  if (mounted) setState(() {});

  TestResult? res;

WidgetsBinding.instance.addPostFrameCallback((_) async {
  if (!mounted) return;

  TestResult? res;

  switch (_testIndex) {
    case 0:
      res = await Navigator.of(context).push(
        MaterialPageRoute(builder: (_) => const UITestPage()),
      );
      break;
    case 1:
      res = await Navigator.of(context).push(
        MaterialPageRoute(builder: (_) => const CPUTestPage()),
      );
      break;
    case 2:
      res = await Navigator.of(context).push(
        MaterialPageRoute(builder: (_) => const RAMTestPage()),
      );
      break;
    case 3:
      res = await Navigator.of(context).push(
        MaterialPageRoute(builder: (_) => const ImageLoadingTestPage()),
      );
      break;
    case 4:
      res = await Navigator.of(context).push(
        MaterialPageRoute(builder: (_) => const ApiTestPage()),
      );
      break;
    case 5:
      res = await Navigator.of(context).push(
        MaterialPageRoute(builder: (_) => const LocationTestPage()),
      );
      break;
  }

  if (!mounted) return;

  if (res != null) _results.add(res);

  _testIndex++;

  await Future.delayed(const Duration(milliseconds: 700));

  if (!mounted) return;

  await _runNext();
});

}


  void _finishSuite() async {
    setState(() => _running = false);
    // Save results automatically and store last path for export convenience
    _lastSavedPath = await _saveResultsToFile();
    showDialog(
      context: context,
      builder:
          (_) => AlertDialog(
            title: const Text('Suite finished'),
            content: Text(
              _lastSavedPath != null
                  ? 'Results saved to $_lastSavedPath'
                  : 'Failed to save results',
            ),
            actions: [
              TextButton(
                onPressed: () => Navigator.pop(context),
                child: const Text('OK'),
              ),
            ],
          ),
    );
  }

  Future<String?> _saveResultsToFile() async {
    try {
      final sb = StringBuffer();
      for (var r in _results) {
        sb.writeln(r.toString());
      }
      final dir = await getApplicationDocumentsDirectory();
      final file = File(
        '${dir.path}/benchmark_results_${DateTime.now().millisecondsSinceEpoch}.txt',
      );
      await file.writeAsString(sb.toString());
      return file.path;
    } catch (e) {
      return null;
    }
  }

  void _exportResults() async {
    final path = await _saveResultsToFile();
    if (path != null) {
      setState(() => _lastSavedPath = path);
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text('Results exported to $path')));
    } else {
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(const SnackBar(content: Text('Failed to export results')));
    }
  }

  @override
  Widget build(BuildContext context) {
    final totalTests = _iterations * _testNames.length;
    final completedTests = (_iteration * _testNames.length) + _testIndex;
    final progress =
        totalTests == 0 ? 0.0 : (completedTests / totalTests).clamp(0.0, 1.0);

    return Scaffold(
      body: Padding(
        padding: const EdgeInsets.symmetric(
          horizontal: 32.0,
          vertical: 52.0,
        ), // match XML padding
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            // Header (centered, bold, 18sp)
            if (_running)
              Text(
                'Running: ${_testNames[_testIndex]}',
                textAlign: TextAlign.center,
                style: const TextStyle(
                  fontSize: 18,
                  fontWeight: FontWeight.bold,
                ),
              ),
            const SizedBox(height: 16),

            // Horizontal progress bar
            LinearProgressIndicator(value: _running ? progress : 0.0),
            const SizedBox(height: 16),

            // Scrollable results area
            Expanded(
              child: Container(
                // ensure it scrolls when content is large
                child: SingleChildScrollView(
                  child: Text(
                    _results.isEmpty
                        ? 'No results yet.'
                        : _results.map((r) => r.toString()).join('\n'),
                    style: const TextStyle(fontSize: 14),
                  ),
                ),
              ),
            ),

            const SizedBox(height: 16),

            // Control buttons row (centered)
            Row(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                ElevatedButton(
                  onPressed: _running ? null : _startSuite,
                  child: Text(_running ? 'Running...' : 'Start Tests'),
                  style: ElevatedButton.styleFrom(
                    padding: const EdgeInsets.symmetric(
                      horizontal: 16,
                      vertical: 12,
                    ),
                    backgroundColor: Color.fromARGB(255, 68, 63, 216),
                    foregroundColor: _running
                            ? Color.fromARGB(255, 54, 53, 53) 
                            : Color.fromARGB(255, 255, 255, 255),
                  )
                ),
                const SizedBox(width: 8),
                ElevatedButton(
                  onPressed: _results.isEmpty ? null : _exportResults,
                  child: const Text('Export Results'),
                  style: ElevatedButton.styleFrom(
                    padding: const EdgeInsets.symmetric(
                      horizontal: 16,
                      vertical: 12,
                    ),
                    backgroundColor: Color.fromARGB(255, 68, 63, 216),
                    foregroundColor: !_running
                            ? Color.fromARGB(255, 54, 53, 53) 
                            : Color.fromARGB(255, 255, 255, 255),
                  )
                  ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}
