import 'package:flutter/material.dart';
import 'models/test_result.dart';
import 'ram_test_impl.dart';
import 'consts/config.dart';
import 'utils/buffered_csv_writer.dart';

/// RAM benchmark page — runs Config.sampleCount samples, each doing 50 runs of RAMTest.
/// Matches Java RamTest: 10000 samples × 50 runs/sample with per-sample CSV logging.
class RamTest extends StatefulWidget {
  final BufferedCsvWriter? writer;
  
  const RamTest({super.key, this.writer});

  @override
  State<RamTest> createState() => _RamTestState();
}

class _RamTestState extends State<RamTest> {
  static const int _runsPerSample = 50;
  
  int _currentSample = 0;
  late final int _startTime;

  @override
  void initState() {
    super.initState();
    _startTime = DateTime.now().millisecondsSinceEpoch;
    _runBenchmark();
  }

  Future<void> _runBenchmark() async {
    final suiteStart = _startTime;

    for (int i = 0; i < Config.sampleCount; i++) {
      if (!mounted) return;

      final sampleStart = DateTime.now().millisecondsSinceEpoch;
      TestResult result;
      try {
        result = await RAMTest.runBenchmark(runs: _runsPerSample);
      } catch (e) {
        result = TestResult('RAM Test', 0, 'Error: $e', false);
      }
      final sampleEnd = DateTime.now().millisecondsSinceEpoch;
      final duration = sampleEnd - sampleStart;

      // Write per-sample row to CSV
      if (widget.writer != null) {
        await widget.writer!.write([
          i + 1,
          duration,
        ]);
      }

      setState(() {
        _currentSample = i + 1;
      });

      // Periodic flush to avoid data loss
      if (widget.writer != null && (i + 1) % 100 == 0) {
        await widget.writer!.flush();
      }

      // Yield to allow UI update
      await Future.delayed(Duration.zero);
    }

    if (!mounted) return;

    // Flush remaining buffered data
    if (widget.writer != null) {
      await widget.writer!.flush();
    }

    final elapsedMs = DateTime.now().millisecondsSinceEpoch - _startTime;
    Navigator.pop(
      context,
      TestResult(
        'RAM Test',
        elapsedMs,
        'Batch completed: ${Config.sampleCount} samples',
        true,
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Center(
        child: Text(
          'RAM Test: $_currentSample / ${Config.sampleCount}',
          style: const TextStyle(fontSize: 18),
        ),
      ),
    );
  }
}
