import 'dart:isolate';
import 'dart:math' as math;
import 'dart:io' show Platform;
import 'package:flutter/material.dart';
import 'models/test_result.dart';
import 'consts/config.dart';
import 'utils/buffered_csv_writer.dart';
import 'models/cpu_result.dart';

/// CPU benchmark page that distributes computation across isolates.
/// Uses time-based and iteration-based modes similar to Java CPUTest pattern.
class CpuTest extends StatefulWidget {
  final int iterations;
  final Duration? timeLimitDuration;
  final BufferedCsvWriter? writer;
  
  const CpuTest({
    super.key,
    this.iterations = Config.sampleCount,
    this.timeLimitDuration,
    this.writer,
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
    int iterationsProcessed = 0;
    double totalChecksum = 0.0;
    int threadCount = 0;

    try {
      _benchmark = _CPUBenchmark();
      await _benchmark!.initialize();
      threadCount = _benchmark!.threadCount;

      for (int i = 0; i < widget.iterations; i++) {
          if (!mounted) return;

          final sampleStart = DateTime.now().millisecondsSinceEpoch;
          
          final result = await _benchmark!.runBatch(
             samples: 1, 
             opsPerSample: Config.cpuIterations
          );

          final sampleEnd = DateTime.now().millisecondsSinceEpoch;
          final sampleDuration = sampleEnd - sampleStart;

          iterationsProcessed += result.iterations;
          totalChecksum += result.checksum;

          // Write per-sample row to CSV
          if (widget.writer != null) {
            await widget.writer!.write(
              i + 1,
              sampleDuration,
              'threads=$threadCount',
              intervalStartMs: sampleStart,
              intervalDurationMs: sampleDuration,
              cumulativeTimeMs: sampleEnd - start,
            );
          }

          setState(() {
            _currentIteration = i + 1;
            _statusMsg = 'CPU Test: $_currentIteration / ${widget.iterations}';
          });
          
          // Periodic flush to avoid data loss
          if (widget.writer != null && (i + 1) % 100 == 0) {
            await widget.writer!.flush();
          }
          
          // Yield to allow UI update
          await Future.delayed(Duration.zero);
      }

      final elapsed = DateTime.now().millisecondsSinceEpoch - start;
      
      if (!mounted) return;

      // Flush remaining buffered data
      if (widget.writer != null) {
        await widget.writer!.flush();
      }

      Navigator.pop(
        context,
        TestResult(
          'CPU Test',
          elapsed,
          'threads=$threadCount, total_ops=$iterationsProcessed, checksum=${totalChecksum.toStringAsFixed(2)}',
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

class _WorkerMessage {
  final int ops;
  final int threadIndex;
  final SendPort replyTo;
  
  const _WorkerMessage(this.ops, this.threadIndex, this.replyTo);
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

  Future<CpuResult> runBatch({required int samples, required int opsPerSample}) async {
      int totalOps = samples * opsPerSample;
      int count = _sendPorts.length;
      int opsPerThread = (totalOps / count).ceil();
      
      List<Future<_WorkerResult>> futures = [];
      
      for (int i = 0; i < count; i++) {
         final rp = ReceivePort();
         _sendPorts[i].send(_WorkerMessage(opsPerThread, i, rp.sendPort));
         futures.add(rp.first.then((v) => v as _WorkerResult));
      }

      final results = await Future.wait(futures);
      
      int actualOps = 0;
      double checksum = 0.0;
      for (var r in results) {
          actualOps += r.ops;
          checksum += r.checksum;
      }
      
      return CpuResult(threads: _sendPorts.length, durationMs: 0, iterations: actualOps, checksum: checksum);
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
            double v = (message.threadIndex + 1).toDouble();
            int processed = 0;
            for (int i = 0; i < message.ops; i++) {
                 v = math.sin(v) * math.cos(v) + math.sqrt((v * v) + 1.234567);
                 acc += v;
                 processed++;
            }
            message.replyTo.send(_WorkerResult(processed, acc));
        }
     });
  }
}


