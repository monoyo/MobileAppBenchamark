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
import 'config/sample_configuration.dart';
import 'utils/buffered_csv_writer.dart';
import 'utils/fps_counter.dart';

class BenchmarkSuitePage extends StatefulWidget {
  final int appLaunchMs;
  const BenchmarkSuitePage({super.key, required this.appLaunchMs});

  @override
  State<BenchmarkSuitePage> createState() => _BenchmarkSuitePageState();
}

class _BenchmarkSuitePageState extends State<BenchmarkSuitePage> {
  // Hardcoded config per user request
  final SampleConfig _selectedConfig = SampleConfig.medium;
  
  late final int _allTests;
  int _currentIteration = 0;
  int _currentTestIndex = 0;
  bool _running = false;
  bool _disposed = false;
  String _currentInfo = '';
  double _progress = 0.0; // 0.0 to 1.0
  String? _averagesBlock;

  final Map<String, List<_TestEntry>> _perTestResults = {};
  
  // Resources
  final Map<String, BufferedCsvWriter> _writers = {};
  final FpsCounter _fpsCounter = FpsCounter();
  Directory? _sessionDir;
  int _suiteStartTime = 0;

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
    _currentInfo = 'App launched in: ${widget.appLaunchMs}ms.\nReady to start tests';
  }

  Future<void> _initializeSession() async {
    try {
      final Directory? base = await _getBenchmarksDir();
      if (base == null) return;
      
      final ts = DateTime.now();
      final stamp = '${ts.year}${ts.month.toString().padLeft(2,'0')}${ts.day.toString().padLeft(2,'0')}_${ts.hour.toString().padLeft(2,'0')}${ts.minute.toString().padLeft(2,'0')}${ts.second.toString().padLeft(2,'0')}';
      final dir = Directory('${base.path}/$stamp');
      if (!await dir.exists()) {
        await dir.create(recursive: true);
      }
      _sessionDir = dir;
      
      // Init writers
      _writers.clear();
      for (final name in _testNames) {
        final safeName = name.toLowerCase().replaceAll(' ', '_');
        final writer = BufferedCsvWriter('${dir.path}/$safeName.csv', bufferSize: _selectedConfig.bufferSize);
        await writer.initialize();
        _writers[name] = writer;
      }
    } catch (e) {
      print('Session init error: $e');
    }
  }

  void _startSuite() async {
    setState(() {
      _running = true;
      _perTestResults.clear();
      _currentIteration = 0;
      _currentTestIndex = 0;
      _progress = 0.0;
      _averagesBlock = null;
    });
    
    _suiteStartTime = DateTime.now().millisecondsSinceEpoch;
    await _initializeSession();
    
    await _runNext();
  }

  @override
  void dispose() {
    _disposed = true;
    _closeWriters();
    super.dispose();
  }
  
  Future<void> _closeWriters() async {
    for(final w in _writers.values) {
      await w.close();
    }
    _writers.clear();
  }

  Future<void> _runNext() async {
    if (!mounted || _disposed) return;

    if (_currentTestIndex >= _allTests) {
      // Finished all tests
      await _closeWriters();
      _appendAverages();
      setState(() => _running = false);
      if (_sessionDir != null) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Saved results to ${_sessionDir!.path}')),
        );
      }
      return;
    }

    if (_currentIteration < _selectedConfig.sampleCount) {
      final name = _testNames[_currentTestIndex];
      setState(() {
        _currentInfo = 'Running: $name (Iteration ${_currentIteration + 1}/${_selectedConfig.sampleCount})';
      });

      // Start FPS counter
      _fpsCounter.start();
      
      final intervalStart = DateTime.now().millisecondsSinceEpoch;
      TestResult? res;
      
      // Navigate to test
      // Note: We modify CPUTest to accept iterations
      switch (_currentTestIndex) {
        case 0:
          res = await Navigator.of(context).push(
            MaterialPageRoute(builder: (_) => const UITestPage()),
          );
          break;
        case 1:
          res = await Navigator.of(context).push(
            MaterialPageRoute(builder: (_) => CPUTestPage(iterations: _selectedConfig.cpuIterations)),
          );
          break;
        case 2:
          res = await Navigator.of(context).push(
            MaterialPageRoute(builder: (_) => const RAMTestPage()),
          );
          break;
        case 3:
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
      
      final intervalEnd = DateTime.now().millisecondsSinceEpoch;
      final intervalDuration = intervalEnd - intervalStart;
      final double currentFps = _fpsCounter.fps;
      
      // Stop/Pause FPS counter? Actually persistent callback keeps running, but we grabbed the value.
      // We can stop it if we want to save resources between tests, but overhead is low.
      // Let's stop it for cleanliness.
      _fpsCounter.stop();

      if (!mounted) return;
      
      if (res != null) {
        final iterNum = _currentIteration + 1;
        
        // Add to memory results (for UI display)
        // Only keep last 50 entries in memory to avoid OOM on large datasets
        List<_TestEntry>? list = _perTestResults[res.testName];
        if (list == null) {
            list = <_TestEntry>[];
            _perTestResults[res.testName] = list;
        }
        if (list.length >= 50) list.removeAt(0); // keep window
        list.add(_TestEntry(iterNum, res));

        // Write to CSV immediately
        final writer = _writers[res.testName];
        if (writer != null) {
            writer.write(
                iterNum, 
                res.executionTimeMs, 
                res.details, 
                fps: currentFps,
                intervalStartMs: intervalStart, 
                intervalDurationMs: intervalDuration
            );
        }
      }

      setState(() {
        _currentIteration++;
        _progress = ((_currentTestIndex * _selectedConfig.sampleCount) + _currentIteration) /
            (_selectedConfig.sampleCount * _allTests);
      });

      // Tiny delay to allow UI to breathe
      await Future.delayed(const Duration(milliseconds: 10));
      await _runNext();
    } else {
      // Next test group
      setState(() {
        _currentTestIndex++;
        _currentIteration = 0;
      });
      await _runNext();
    }
  }

  void _appendAverages() {
    final buffer = StringBuffer('# Recent Averages (ms per window)\n');
    for (final entry in _perTestResults.entries) {
      final values = entry.value;
      if (values.isEmpty) continue;
      final total = values.fold<int>(0, (acc, e) => acc + e.result.executionTimeMs);
      final avg = total / values.length;
      buffer.writeln('${entry.key},${avg.toStringAsFixed(2)}');
    }
    setState(() {
      _averagesBlock = buffer.toString();
      _currentInfo = 'All tests completed!';
    });
  }

  Future<Directory?> _getBenchmarksDir() async {
    try {
      if (Platform.isAndroid) {
        final Directory? ext = await getExternalStorageDirectory();
        final Directory base = ext ?? await getApplicationDocumentsDirectory();
        final Directory docs = Directory('${base.path}/Documents');
        if (!(await docs.exists())) await docs.create(recursive: true);
        final Directory out = Directory('${docs.path}/benchmarks');
        if (!(await out.exists())) await out.create(recursive: true);
        return out;
      } else {
        final Directory base = await getApplicationDocumentsDirectory();
        final Directory out = Directory('${base.path}/benchmarks');
        if (!(await out.exists())) await out.create(recursive: true);
        return out;
      }
    } catch (_) {
      return null;
    }
  }

  @override
  Widget build(BuildContext context) {
    final csvText = _buildCsvDisplayState();

    return Scaffold(
      body: Padding(
        padding: const EdgeInsets.symmetric(
          horizontal: 32.0,
          vertical: 52.0,
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text(
              _currentInfo.isNotEmpty ? _currentInfo : 'Ready',
              textAlign: TextAlign.center,
              style: const TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
            ),
            const SizedBox(height: 16),
            
// Config Selector removed per user request
            // We use fixed SampleConfig.medium
            if (!_running)
              const SizedBox(height: 16),

            const SizedBox(height: 16),
            LinearProgressIndicator(value: _running ? _progress : 0.0),
            const SizedBox(height: 16),

            Expanded(
              child: Container(
                decoration: BoxDecoration(border: Border.all(color: Colors.grey)),
                child: SingleChildScrollView(
                  child: Padding(
                    padding: const EdgeInsets.all(8.0),
                    child: SelectableText(csvText, style: const TextStyle(fontSize: 12, fontFamily: 'monospace')),
                  ),
                ),
              ),
            ),

            const SizedBox(height: 16),

            Row(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                ElevatedButton(
                  onPressed: _running ? null : _startSuite,
                  child: Text(_running ? 'Running...' : 'Start Tests'),
                  style: ElevatedButton.styleFrom(
                    padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
                    backgroundColor: const Color.fromARGB(255, 68, 63, 216),
                    foregroundColor: Colors.white,
                  )
                ),
                // Export button is now redundant as we save automatically, 
                // but we can keep it to show path or re-open folder (if feasible).
                // Let's just show path in snackbar upon completion.
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

String _buildCsvDisplayFrom(Map<String, List<_TestEntry>> data, String? averagesBlock) {
  final sb = StringBuffer();
  final keys = data.keys.toList();
  for (final testName in keys) {
    final entries = [...data[testName]!];
    // Show only last 10 for display to avoid lag
    final displayEntries = entries.length > 10 ? entries.sublist(entries.length - 10) : entries;
    
    sb.writeln('# $testName (Last 10 of ${entries.length})');
    sb.writeln('iter,ms,details');
    for (final e in displayEntries) {
      sb.writeln('${e.iteration},${e.result.executionTimeMs},${_csv(e.result.details)}');
    }
    sb.writeln();
  }
  if (averagesBlock != null) {
      sb.writeln(averagesBlock.trim());
  }
  return sb.toString();
}

extension on _BenchmarkSuitePageState {
  String _buildCsvDisplayState() {
    return _buildCsvDisplayFrom(_perTestResults, _averagesBlock);
  }
}

