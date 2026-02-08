import 'dart:isolate';
import 'dart:math' as math;
import 'dart:io' show Platform;
import 'package:flutter/material.dart';
import 'models/test_result.dart';

/// CPU benchmark page that distributes computation across isolates.
/// Uses time-based and iteration-based modes similar to Java CPUTest pattern.
class CPUTestPage extends StatefulWidget {
  final int iterations;
  final Duration? timeLimitDuration;
  
  const CPUTestPage({
    super.key,
    this.iterations = 500000,
    this.timeLimitDuration,
  });

  @override
  State<CPUTestPage> createState() => _CPUTestPageState();
}

class _CPUTestPageState extends State<CPUTestPage> {
  @override
  void initState() {
    super.initState();
    _runBenchmark();
  }

  /// Executes CPU benchmark with thread distribution across available processors.
  Future<void> _runBenchmark() async {
    final start = DateTime.now().millisecondsSinceEpoch;
    try {
      final result = await _CPUBenchmark.runParallel(
        targetIterations: widget.iterations,
        timeLimit: widget.timeLimitDuration,
      );
      final elapsed = DateTime.now().millisecondsSinceEpoch - start;
      
      if (!mounted) return;
      Navigator.pop(
        context,
        TestResult(
          'CPU Test',
          elapsed,
          'threads=${result.threadCount}, iterations=${result.totalIterations}, checksum=${result.checksum.toStringAsFixed(2)}',
          true,
        ),
      );
    } catch (e) {
      if (!mounted) return;
      Navigator.pop(
        context,
        TestResult('CPU Test', -1, 'Error: $e', false),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    return const Scaffold(
      body: Center(
        child: Text(
          'CPU processing ...',
          style: TextStyle(fontSize: 18),
        ),
      ),
    );
  }
}

/// Result container for CPU benchmark execution.
class _CPUResult {
  final int threadCount;
  final int totalIterations;
  final double checksum;
  
  const _CPUResult({
    required this.threadCount,
    required this.totalIterations,
    required this.checksum,
  });
}

/// Message passed to isolate for CPU computation.
class _CPUIsolateMessage {
  final int seed;
  final int iterations;
  final int? timeoutMs;
  final SendPort replyTo;
  
  const _CPUIsolateMessage({
    required this.seed,
    required this.iterations,
    required this.timeoutMs,
    required this.replyTo,
  });
}

/// Result from isolate computation.
class _CPUIsolateResult {
  final int completedIterations;
  final double checksum;
  
  const _CPUIsolateResult({
    required this.completedIterations,
    required this.checksum,
  });
}

/// CPU benchmark coordinator managing multi-core execution.
class _CPUBenchmark {
  static int get _processorCount =>
      Platform.numberOfProcessors > 0 ? Platform.numberOfProcessors : 8;

  /// Runs CPU benchmark distributed across available processors.
  static Future<_CPUResult> runParallel({
    required int targetIterations,
    Duration? timeLimit,
  }) async {
    final int threadCount = _processorCount;
    final int iterationsPerThread = (targetIterations / threadCount).ceil();
    final int? timeLimitMs = timeLimit?.inMilliseconds;

    final List<Future<_CPUIsolateResult>> futures = [];
    
    for (int i = 0; i < threadCount; i++) {
      futures.add(
        _spawnWorkerIsolate(
          seed: i + 1,
          iterations: iterationsPerThread,
          timeoutMs: timeLimitMs,
        ),
      );
    }

    final results = await Future.wait(futures);
    
    final int totalIterations = results.fold<int>(
      0,
      (sum, r) => sum + r.completedIterations,
    );
    final double totalChecksum = results.fold<double>(
      0.0,
      (sum, r) => sum + r.checksum,
    );

    return _CPUResult(
      threadCount: threadCount,
      totalIterations: totalIterations,
      checksum: totalChecksum,
    );
  }

  /// Spawns an isolate to perform CPU-intensive calculations.
  static Future<_CPUIsolateResult> _spawnWorkerIsolate({
    required int seed,
    required int iterations,
    required int? timeoutMs,
  }) async {
    final receivePort = ReceivePort();
    
    await Isolate.spawn(
      _cpuWorkerEntryPoint,
      _CPUIsolateMessage(
        seed: seed,
        iterations: iterations,
        timeoutMs: timeoutMs,
        replyTo: receivePort.sendPort,
      ),
      debugName: 'cpu-worker-$seed',
    );

    final dynamic response = await receivePort.first;
    if (response is _CPUIsolateResult) {
      return response;
    }
    throw Exception('Unexpected response from CPU worker');
  }
}

/// Entry point for CPU worker isolate.
/// Performs mathematical operations to simulate CPU load.
void _cpuWorkerEntryPoint(_CPUIsolateMessage message) {
  final startTime = DateTime.now().millisecondsSinceEpoch;
  int iterationCount = 0;
  double accumulator = 0.0;
  double value = message.seed.toDouble();
  
  // Perform CPU-intensive calculations
  for (int i = 0; i < message.iterations; i++) {
    // Check time limit if specified
    if (message.timeoutMs != null && i % 1000 == 0) {
      final elapsed = DateTime.now().millisecondsSinceEpoch - startTime;
      if (elapsed > message.timeoutMs!) {
        break;
      }
    }

    // Complex mathematical operations to stress CPU
    value = math.sin(value) * math.cos(value) + 
            math.sqrt((value * value) + 1.234567) +
            math.tan(value);
    accumulator += value;
    iterationCount++;
  }

  message.replyTo.send(
    _CPUIsolateResult(
      completedIterations: iterationCount,
      checksum: accumulator,
    ),
  );
}

