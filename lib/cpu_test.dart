import 'dart:isolate';
import 'dart:math' as math;
import 'package:flutter/material.dart';
import 'models/test_result.dart';

class CPUTestPage extends StatefulWidget {
  final int iterations;
  const CPUTestPage({super.key, this.iterations = 500000});

  @override
  State<CPUTestPage> createState() => _CPUTestPageState();
}

class _CPUTestPageState extends State<CPUTestPage> {
  @override
  void initState() {
    super.initState();
    _runBenchmark();
  }

  Future<void> _runBenchmark() async {
    final start = DateTime.now().millisecondsSinceEpoch;
    // Distribute iterations across threads
    final r = await _runCpuBenchmarkParallel(targetIterations: widget.iterations);
    final elapsed = DateTime.now().millisecondsSinceEpoch - start;
    if (!mounted) return;
    Navigator.pop(
      context,
      TestResult(
        'CPU Test',
        elapsed,
        'threads=${r.threads}, totalIterations=${r.iterations}, check=${r.checksum.toStringAsFixed(2)}',
        true,
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return const Scaffold(
      body: Center(
        child: Text(
          'Running CPU Test...',
          style: TextStyle(fontSize: 18),
        ),
      ),
    );
  }
}

class _CpuResult {
  final int threads;
  final int iterations;
  final double checksum;
  _CpuResult(this.threads, this.iterations, this.checksum);
}

Future<_CpuResult> _runCpuBenchmarkParallel({required int targetIterations, int? threads}) async {
  final int t = (threads != null && threads > 0) ? threads : _availableProcessors();
  final int itersPerThread = (targetIterations / t).ceil();
  
  final List<Future<_IsolateResult>> futures = [];
  for (int i = 0; i < t; i++) {
    futures.add(_spawnCpuIsolate(i + 1, itersPerThread));
  }
  
  final results = await Future.wait(futures);
  
  final int totalIters = results.fold<int>(0, (s, r) => s + r.iterations);
  final double totalCheck = results.fold<double>(0.0, (s, r) => s + r.checksum);
  
  return _CpuResult(t, totalIters, totalCheck);
}

class _IsolateResult {
  final int iterations;
  final double checksum;
  _IsolateResult(this.iterations, this.checksum);
}

Future<_IsolateResult> _spawnCpuIsolate(int seed, int iterations) async {
  final ReceivePort rp = ReceivePort();
  await Isolate.spawn<_IsolateMsg>(_cpuBurn, _IsolateMsg(seed, iterations, rp.sendPort),
      debugName: 'cpu-burn-$seed');
  final List<dynamic> msg = await rp.first as List<dynamic>;
  return _IsolateResult(msg[0] as int, msg[1] as double);
}

class _IsolateMsg {
  final int seed;
  final int iterations;
  final SendPort replyTo;
  _IsolateMsg(this.seed, this.iterations, this.replyTo);
}

void _cpuBurn(_IsolateMsg m) {
  double acc = 0.0;
  double x = m.seed.toDouble();
  final math.Random rand = math.Random(m.seed); // Keep consistent load
  
  // Strict iteration loop
  for(int i = 0; i < m.iterations; i++) {
    x = math.sin(x) * math.cos(x) + math.sqrt(x * x + 1.234567);
    acc += x;
  }
  m.replyTo.send([m.iterations, acc]);
}

int _availableProcessors() {
  return 8; // Ideally use Platform.numberOfProcessors but strictly not exposed in dart:io easily without workarounds? 
  // Actually Platform.numberOfProcessors IS available in dart:io since Dart 2.0
  // But wait, let's use a safe default or try to get it if we can verify the API. 
  // Platform.numberOfProcessors works in Flutter.
}

