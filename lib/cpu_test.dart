import 'dart:isolate';
import 'dart:math' as math;
import 'package:flutter/material.dart';
import 'models/test_result.dart';

class CPUTestPage extends StatefulWidget {
  const CPUTestPage({super.key});

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
    final r = await _runCpuBenchmarkParallel(durationMs: 3000);
    final elapsed = DateTime.now().millisecondsSinceEpoch - start;
    if (!mounted) return;
    Navigator.pop(
      context,
      TestResult(
        'CPU Test',
        elapsed,
        'threads=${r.threads}, iterations=${r.iterations}',
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
  final int durationMs;
  final int iterations;
  final double checksum;
  _CpuResult(this.threads, this.durationMs, this.iterations, this.checksum);
}

Future<_CpuResult> _runCpuBenchmarkParallel({int durationMs = 3000, int? threads}) async {
  final t = (threads != null && threads > 0) ? threads : _availableProcessors();
  final deadline = DateTime.now().millisecondsSinceEpoch + durationMs;
  final results = <List<dynamic>>[];
  final futures = <Future<List<dynamic>>>[];
  for (var i = 0; i < t; i++) {
    futures.add(_spawnCpuIsolate(i + 1, deadline));
  }
  results.addAll(await Future.wait(futures));
  final totalIters = results.fold<int>(0, (s, r) => s + (r[0] as int));
  final checksum = results.fold<double>(0.0, (s, r) => s + (r[1] as double));
  return _CpuResult(t, durationMs, totalIters, checksum);
}

Future<List<dynamic>> _spawnCpuIsolate(int seed, int deadlineMs) async {
  final rp = ReceivePort();
  await Isolate.spawn<_IsolateMsg>(_cpuBurn, _IsolateMsg(seed, deadlineMs, rp.sendPort),
      debugName: 'cpu-burn-$seed');
  final msg = await rp.first as List;
  return msg;
}

class _IsolateMsg {
  final int seed;
  final int deadlineMs;
  final SendPort replyTo;
  _IsolateMsg(this.seed, this.deadlineMs, this.replyTo);
}

void _cpuBurn(_IsolateMsg m) {
  var iter = 0;
  var acc = 0.0;
  var x = m.seed.toDouble();
  final rand = math.Random(m.seed);
  while (DateTime.now().millisecondsSinceEpoch < m.deadlineMs) {
    x = math.sin(x) * math.cos(x) + math.sqrt(x * x + 1.234567 + rand.nextDouble());
    acc += x;
    iter++;
  }
  m.replyTo.send([iter, acc]);
}

int _availableProcessors() {
  // Brak oficjalnego API w Flutterze; przyjmujemy 4 jako sensowny default.
  return 4;
}
