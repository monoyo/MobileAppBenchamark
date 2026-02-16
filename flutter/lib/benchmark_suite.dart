import 'dart:io';
import 'package:flutter/material.dart';
import 'package:path_provider/path_provider.dart';
import 'models/test_result.dart';
import 'models/test_entry.dart';
import 'ui_test.dart';
import 'cpu_test.dart';
import 'ram_test.dart';
import 'image_loading_test.dart';
import 'api_test.dart';
import 'location_test.dart';
import 'consts/config.dart';
import 'utils/buffered_csv_writer.dart';
import 'utils/fps_counter.dart';
import 'utils/summary_writer.dart';

class BenchmarkSuitePage extends StatefulWidget {
  final int appLaunchMs;
  const BenchmarkSuitePage({super.key, required this.appLaunchMs});

  @override
  State<BenchmarkSuitePage> createState() => _BenchmarkSuitePageState();
}

class _BenchmarkSuitePageState extends State<BenchmarkSuitePage> {
  // Fixed 10000 samples as per requirement
  
  late final int _allTests;
  int _currentTestIndex = 0;
  bool _running = false;
  bool _disposed = false;
  String _currentInfo = '';
  double _progress = 0.0; // 0.0 to 1.0
  String? _averagesBlock;

  final Map<String, bool> _selectedTests = {};
  final Map<String, List<TestEntry>> _perTestResults = {};
  
  // Resources
  final Map<String, BufferedCsvWriter> _writers = {};
  final FpsCounter _fpsCounter = FpsCounter();
  Directory? _sessionDir;

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
    _currentInfo = 'Benchmark Suite';
    
    // Initialize all tests as selected by default
    for (final name in _testNames) {
      _selectedTests[name] = true;
    }
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
        if (_selectedTests[name] != true) continue;
        
        final safeName = name.toLowerCase().replaceAll(' ', '_');
        final writer = BufferedCsvWriter('${dir.path}/$safeName.csv', bufferSize: Config.bufferSize);
        String header;
        if (name == 'UI Test') {
          header = 'Frame,ObjectCount,FrameTimeMs,FPS,ElapsedMs';
        } else if (name == 'RAM Test' || name == 'CPU Test') {
          header = 'iteration,elapsedTimeMs';
        } else {
          header = 'iteration,execution_time_ms,details,interval_start_ms,interval_duration_ms,cumulative_time_ms';
        }
        await writer.initialize(header);
        _writers[name] = writer;
      }
    } catch (e) {
      print('Session init error: $e');
    }
  }

  void _startSuite() async {
    // Check if any test is selected
    if (!_testNames.any((name) => _selectedTests[name] == true)) {
        ScaffoldMessenger.of(context).showSnackBar(
           const SnackBar(content: Text('Please select at least one test')),
        );
        return;
    }

    setState(() {
      _running = true;
      _perTestResults.clear();
      _currentTestIndex = 0;
      _progress = 0.0;
      _averagesBlock = null;
      _currentInfo = 'Running Tests...';
    });
    
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

    // Skip unselected tests
    while (_currentTestIndex < _allTests && _selectedTests[_testNames[_currentTestIndex]] != true) {
        _currentTestIndex++;
    }

    if (_currentTestIndex >= _allTests) {
      // Finished all tests
      await _closeWriters();
      
      // Generate summary.csv removed as per request
      if (_sessionDir != null) {
        // await SummaryWriter.writeSummary(_sessionDir!);
      }
      
      _appendAverages();
      setState(() => _running = false);
      if (_sessionDir != null) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Saved results to ${_sessionDir!.path}')),
        );
      }
      return;
    }

    final name = _testNames[_currentTestIndex];
    setState(() {
      _currentInfo = 'Running: $name';
    });

    // Start FPS counter
    _fpsCounter.start();
    
    TestResult? res;
    
    // All tests receive a CSV writer for per-sample logging
    final writer = _writers[_testNames[_currentTestIndex]];

    switch (_currentTestIndex) {
      case 0:
        res = await Navigator.of(context).push(
          MaterialPageRoute(builder: (_) => UiTest(writer: writer)),
        );
        break;
      case 1:
        res = await Navigator.of(context).push(
          MaterialPageRoute(builder: (_) => CpuTest(iterations: Config.cpuIterations, writer: writer)),
        );
        break;
      case 2:
        res = await Navigator.of(context).push(
          MaterialPageRoute(builder: (_) => RamTest(writer: writer)),
        );
        break;
      case 3:
        final int runId = DateTime.now().millisecondsSinceEpoch ^ _currentTestIndex;
        res = await Navigator.of(context).push(
          MaterialPageRoute(builder: (_) => ImageLoadingTest(runId: runId, writer: writer)),
        );
        break;
      case 4:
        res = await Navigator.of(context).push(
          MaterialPageRoute(builder: (_) => ApiTest(writer: writer)),
        );
        break;
      case 5:
        res = await Navigator.of(context).push(
          MaterialPageRoute(builder: (_) => LocationTest(writer: writer)),
        );
        break;
    }
    
    _fpsCounter.stop();

    if (!mounted) return;
    
    if (res != null) {
      // Add to memory results (for UI display)
      List<TestEntry>? list = _perTestResults[res.testName];
      if (list == null) {
          list = <TestEntry>[];
          _perTestResults[res.testName] = list;
      }
      list.add(TestEntry(1, res));
    }

    // Flush the writer to ensure all buffered data is written,
    // even if the test was canceled (res == null).
    if (writer != null) {
        await writer.flush();
    }

    setState(() {
      _currentTestIndex++;
      _progress = (_currentTestIndex) / _allTests;
    });

    // Tiny delay to allow UI to breathe
    await Future.delayed(const Duration(milliseconds: 10));
    await _runNext();
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
      backgroundColor: Colors.white,
      body: Padding(
        padding: const EdgeInsets.symmetric(
          horizontal: 32.0,
          vertical: 52.0,
        ),
        child: Column(
          children: [
            // Header
            Text(
              _currentInfo.isNotEmpty ? _currentInfo : 'Benchmark Suite',
              textAlign: TextAlign.center,
              style: const TextStyle(
                fontSize: 18, 
                fontWeight: FontWeight.bold,
                color: Colors.black,
              ),
            ),
            const SizedBox(height: 16),
            
            // Progress Bar
            ClipRRect(
              borderRadius: BorderRadius.circular(2),
              child: LinearProgressIndicator(
                value: _running ? _progress : 0.0,
                minHeight: 4,
                backgroundColor: const Color(0xFFEEEEEE),
                valueColor: const AlwaysStoppedAnimation<Color>(Color(0xFF443FD8)),
              ),
            ),
            const SizedBox(height: 16),

            // Results Area & Selection
            Expanded(
              child: Container(
                width: double.infinity,
                decoration: const BoxDecoration(
                  color: Color(0xFFFAFAFA),
                ),
                child: _running 
                  ? SingleChildScrollView(
                      child: Padding(
                        padding: const EdgeInsets.all(8.0),
                        child: Text(
                          csvText, 
                          style: const TextStyle(
                            fontSize: 14, 
                            fontFamily: 'monospace',
                            color: Colors.black,
                          )
                        ),
                      ),
                    )
                  : ListView(
                      padding: const EdgeInsets.all(8.0),
                      children: _testNames.map((name) {
                        return CheckboxListTile(
                          title: Text(name),
                          value: _selectedTests[name] == true,
                          onChanged: (bool? value) {
                            setState(() {
                              _selectedTests[name] = value == true;
                            });
                          },
                          activeColor: const Color(0xFF443FD8),
                        );
                      }).toList(),
                    ),
              ),
            ),

            const SizedBox(height: 16),

            // Buttons
            Row(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                ElevatedButton(
                  onPressed: _running ? null : _startSuite,
                  child: Text(_running ? 'Running...' : 'Start Tests'),
                  style: ElevatedButton.styleFrom(
                    backgroundColor: const Color(0xFF443FD8),
                    foregroundColor: Colors.white,
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(8),
                    ),
                    padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
                    elevation: 2,
                  )
                ),
                const SizedBox(width: 16),
                OutlinedButton(
                  onPressed: () { 
                     // Export is strict, but functionality is auto-save.
                     // We show this mainly for visual parity.
                     if (_sessionDir != null) {
                        ScaffoldMessenger.of(context).showSnackBar(
                           SnackBar(content: Text('Saved: ${_sessionDir!.path}'))
                        );
                     }
                  },
                  child: const Text('Export Results'),
                  style: OutlinedButton.styleFrom(
                    foregroundColor: const Color(0xFF443FD8),
                    side: const BorderSide(color: Color(0xFF443FD8)),
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(8),
                    ),
                    padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
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

String _csv(String v) {
  final needs = v.contains(',') || v.contains('"') || v.contains('\n') || v.contains('\r');
  final esc = v.replaceAll('"', '""');
  return needs ? '"$esc"' : esc;
}

String _buildCsvDisplayFrom(Map<String, List<TestEntry>> data, String? averagesBlock) {
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
