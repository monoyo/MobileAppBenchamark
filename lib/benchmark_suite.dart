import 'dart:io';
import 'package:flutter/material.dart';
import 'package:path_provider/path_provider.dart';
import 'models/test_result.dart';
import 'ui_test.dart';
import 'cpu_test.dart';
import 'ram_test.dart';
import 'image_loading_test.dart';
import 'api_test.dart';
import 'location_test.dart';

class BenchmarkSuitePage extends StatefulWidget {
  const BenchmarkSuitePage({super.key});

  @override
  State<BenchmarkSuitePage> createState() => _BenchmarkSuitePageState();
}

class _BenchmarkSuitePageState extends State<BenchmarkSuitePage> {
  static const int _iterationsPerTest = 10;
  late final int _allTests;
  int _currentIteration = 0; // per test
  int _currentTestIndex = 0;
  bool _running = false;
  bool _disposed = false;
  String _currentInfo = '';
  double _progress = 0.0;

  // Results per test for CSV
  final Map<String, List<_TestEntry>> _perTestResults = {};

  final List<String> _testNames = const [
    'UI Test',
    'CPU Test',
    'RAM Test',
    'Image Loading Test',
    'API Test',
    'Location Test',
  ];

  @override
  void initState() {
    super.initState();
    _allTests = _testNames.length;
  }

  void _startSuite() async {
    setState(() {
      _running = true;
      _perTestResults.clear();
      _currentIteration = 0;
      _currentTestIndex = 0;
      _currentInfo = '';
      _progress = 0.0;
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

    if (_currentTestIndex >= _allTests) {
      _appendAverages();
      setState(() => _running = false);
      return;
    }

    if (_currentIteration < _iterationsPerTest) {
      final name = _testNames[_currentTestIndex];
      setState(() {
        _currentInfo = 'Running: $name (Iteration ${_currentIteration + 1}/$_iterationsPerTest)';
      });

      TestResult? res;
      switch (_currentTestIndex) {
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
          // Provide a unique runId to the image test to avoid hitting cache across iterations
          final int runId = DateTime.now().millisecondsSinceEpoch ^ _currentIteration ^ _currentTestIndex;
          res = await Navigator.of(context).push(
            MaterialPageRoute(builder: (_) => ImageLoadingTestPage(runId: runId)),
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
      if (res != null) {
        final iterNum = _currentIteration + 1;
        final list = _perTestResults.putIfAbsent(res.testName, () => <_TestEntry>[]);
        list.add(_TestEntry(iterNum, res));
      }

      setState(() {
        _currentIteration++;
        _progress = ((_currentTestIndex * _iterationsPerTest) + _currentIteration) /
            (_iterationsPerTest * _allTests);
      });

      await Future.delayed(const Duration(milliseconds: 500));
      await _runNext();
    } else {
      // move to next test
      setState(() {
        _currentTestIndex++;
        _currentIteration = 0;
      });
      await _runNext();
    }
  }


  void _appendAverages() {
    // Optionally we can append a summary to display; we'll just update info
    setState(() {
      _currentInfo = 'All tests completed!';
    });
  }

  Future<Directory?> _getBenchmarksDir() async {
    try {
      if (Platform.isAndroid) {
        // Prefer app-specific external directory (Android/data/<package>/files)
        final Directory? ext = await getExternalStorageDirectory();
        final Directory base = ext ?? await getApplicationDocumentsDirectory();
        // Ensure Documents/benchmarks exists inside the app-specific directory
        final Directory docs = Directory('${base.path}/Documents');
        if (!(await docs.exists())) {
          await docs.create(recursive: true);
        }
        final Directory out = Directory('${docs.path}/benchmarks');
        if (!(await out.exists())) {
          await out.create(recursive: true);
        }
        return out;
      } else {
        final Directory base = await getApplicationDocumentsDirectory();
        final Directory out = Directory('${base.path}/benchmarks');
        if (!(await out.exists())) {
          await out.create(recursive: true);
        }
        return out;
      }
    } catch (_) {
      return null;
    }
  }

  void _exportResults() async {
    try {
      final dir = await _getBenchmarksDir();
      if (dir == null) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Cannot access output directory')),
        );
        return;
      }
      final ts = DateTime.now();
      final stamp = '${ts.year.toString().padLeft(4, '0')}${ts.month.toString().padLeft(2, '0')}${ts.day.toString().padLeft(2, '0')}_${ts.hour.toString().padLeft(2, '0')}${ts.minute.toString().padLeft(2, '0')}${ts.second.toString().padLeft(2, '0')}';

      var count = 0;
      for (final entry in _perTestResults.entries) {
        final name = entry.key;
        final rows = [...entry.value]..sort((a, b) => a.iteration.compareTo(b.iteration));
        if (rows.isEmpty) continue;
        final safe = name.toLowerCase().replaceAll(' ', '_');
        final file = File('${dir.path}/$safe.$stamp.csv');
        final sb = StringBuffer()..writeln('iteration,executionTimeMs,details,success');
        for (final r in rows) {
          sb.writeln('${r.iteration},${r.result.executionTimeMs},${_csv(r.result.details)},${r.result.success}');
        }
        await file.writeAsString(sb.toString());
        count++;
      }
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(count > 0 ? 'Saved $count CSV files to ${dir.path}' : 'No results to export yet')),
      );
    } catch (e) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Export error: $e')),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
  final total = _iterationsPerTest * _testNames.length;
  final completed = (_currentTestIndex * _iterationsPerTest) + _currentIteration;
  final progress = total == 0 ? 0.0 : (completed / total).clamp(0.0, 1.0);
  final csvText = _buildCsvDisplayState();

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
            Text(
              _running ? _currentInfo : 'Ready',
              textAlign: TextAlign.center,
              style: const TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
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
                  child: SelectableText(csvText, style: const TextStyle(fontSize: 14)),
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
                  onPressed: _perTestResults.isEmpty ? null : _exportResults,
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

class _TestEntry {
  final int iteration;
  final TestResult result;
  _TestEntry(this.iteration, this.result);
}

String _csv(String v) {
  final needs = v.contains(',') || v.contains('"') || v.contains('\n') || v.contains('\r');
  final esc = v.replaceAll('"', '""');
  return needs ? '"$esc"' : esc;
}

String _buildCsvDisplayFrom(Map<String, List<_TestEntry>> data) {
  final sb = StringBuffer();
  final keys = data.keys.toList();
  for (final testName in keys) {
    final entries = [...data[testName]!];
    entries.sort((a, b) => a.iteration.compareTo(b.iteration));
    sb.writeln('# $testName');
    sb.writeln('iteration,executionTimeMs,details,success');
    for (final e in entries) {
      sb.writeln('${e.iteration},${e.result.executionTimeMs},${_csv(e.result.details)},${e.result.success}');
    }
    sb.writeln();
  }
  return sb.toString();
}

extension on _BenchmarkSuitePageState {
  String _buildCsvDisplayState() {
    return _buildCsvDisplayFrom(_perTestResults);
  }
}
