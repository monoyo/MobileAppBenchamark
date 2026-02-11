import 'dart:isolate';
import 'dart:math' as math;
import 'dart:io' show Platform;
import 'package:flutter/material.dart';
import 'models/test_result.dart';
import 'config/sample_configuration.dart';

/// CPU benchmark page that distributes computation across isolates.
/// Uses time-based and iteration-based modes similar to Java CPUTest pattern.
class CpuTest extends StatefulWidget {
  final int iterations;
  final Duration? timeLimitDuration;
  
  const CpuTest({
    super.key,
    this.iterations = SampleConfig.sampleCount,
    this.timeLimitDuration,
  });

  @override
  State<CpuTest> createState() => _CpuTestState();
}

class _CpuTestState extends State<CpuTest> {
  int _currentIteration = 0;
  String _statusMsg = 'Initializing...';
  _CPUBenchmark? _benchmark;

  @override
  void initState() {
    super.initState();
    _startBenchmark();
  }

  @override
  void dispose() {
    _benchmark?.dispose();
    super.dispose();
  }

  Future<void> _startBenchmark() async {
    final start = DateTime.now().millisecondsSinceEpoch;
    int totalIterationsProcessed = 0;
    double totalChecksum = 0.0;
    int threadCount = 0;

    try {
      _benchmark = _CPUBenchmark();
      await _benchmark!.initialize();
      threadCount = _benchmark!.threadCount;

      // Outer loop: 10,000 "Samples"
      // We process 1 by 1 to strictly match Kotlin's UI behavior (test after test).
      const int batchSize = 1;
      
      for (int i = 0; i < widget.iterations; i += batchSize) {
          if (!mounted) return;

          int remaining = widget.iterations - i;
          int currentBatch = remaining > batchSize ? batchSize : remaining;
          
          // Distribute this batch of "Samples" across workers.
          // Each sample involves [SampleConfig.cpuIterations] ops.
          // So we ask workers to perform (currentBatch * cpuIterations) / threads ops?
          // No, to strictly match Kotlin:
          // Kotlin: 10,000 outer loops. Each loop calls runBenchmarkIterations(10,000).
          // So Total Ops = 10,000 * 10,000.
          // Our `_benchmark.runBatch` should distribute (currentBatch * widget.iterations) ops.
          // Wait, widget.iterations IS SampleConfig.sampleCount (10,000).
          // So we need to run (currentBatch * 10,000) ops distributed across threads.
          
          final result = await _benchmark!.runBatch(
             samples: currentBatch, 
             opsPerSample: widget.iterations // 10,000 ops per sample
          );

          totalIterationsProcessed += result.totalIterations;
          totalChecksum += result.checksum;

          setState(() {
            _currentIteration = i + currentBatch;
            _statusMsg = 'CPU Test: $_currentIteration / ${widget.iterations}';
          });
          
          // Yield to allow UI update
          await Future.delayed(Duration.zero);
      }

      final elapsed = DateTime.now().millisecondsSinceEpoch - start;
      
      if (!mounted) return;
      Navigator.pop(
        context,
        TestResult(
          'CPU Test',
          elapsed,
          'threads=$threadCount, total_ops=$totalIterationsProcessed, checksum=${totalChecksum.toStringAsFixed(2)}',
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
    return Scaffold(
      body: Center(
        child: Text(
          _statusMsg,
          style: const TextStyle(fontSize: 18),
        ),
      ),
    );
  }
}

class _CPUResult {
  final int totalIterations;
  final double checksum;
  
  const _CPUResult({
    required this.totalIterations,
    required this.checksum,
  });
}

class _WorkerMessage {
  final int ops;
  final SendPort replyTo;
  
  const _WorkerMessage(this.ops, this.replyTo);
}

class _WorkerResult {
  final int ops;
  final double checksum;
  
  const _WorkerResult(this.ops, this.checksum);
}

class _CPUBenchmark {
  final List<Isolate> _isolates = [];
  final List<SendPort> _sendPorts = [];
  
  int get threadCount => Platform.numberOfProcessors > 0 ? Platform.numberOfProcessors : 4;

  Future<void> initialize() async {
    final count = threadCount;
    for (int i = 0; i < count; i++) {
       final receivePort = ReceivePort();
       final isolate = await Isolate.spawn(_workerEntry, receivePort.sendPort);
       _isolates.add(isolate);
       final sendPort = await receivePort.first as SendPort;
       _sendPorts.add(sendPort);
    }
  }

  Future<_CPUResult> runBatch({required int samples, required int opsPerSample}) async {
      int totalOps = samples * opsPerSample;
      int count = _sendPorts.length;
      int opsPerThread = (totalOps / count).ceil();
      
      List<Future<_WorkerResult>> futures = [];
      
      for (int i = 0; i < count; i++) {
         final rp = ReceivePort();
         _sendPorts[i].send(_WorkerMessage(opsPerThread, rp.sendPort));
         futures.add(rp.first.then((v) => v as _WorkerResult));
      }

      final results = await Future.wait(futures);
      
      int actualOps = 0;
      double checksum = 0.0;
      for (var r in results) {
          actualOps += r.ops;
          checksum += r.checksum;
      }
      
      return _CPUResult(totalIterations: actualOps, checksum: checksum);
  }

  void dispose() {
    for (var i in _isolates) {
      i.kill();
    }
    _isolates.clear();
    _sendPorts.clear();
  }

  static void _workerEntry(SendPort mainSendPort) {
     final commandPort = ReceivePort();
     mainSendPort.send(commandPort.sendPort);
     
     commandPort.listen((message) {
        if (message is _WorkerMessage) {
            double acc = 0.0;
            double v = 1.2345; 
            int processed = 0;
            for (int i = 0; i < message.ops; i++) {
                 v = math.sin(v) * math.cos(v) + math.sqrt((v * v) + 1.234567) + math.tan(v);
                 acc += v;
                 processed++;
            }
            message.replyTo.send(_WorkerResult(processed, acc));
        }
     });
  }
}


