import 'dart:async';
import 'package:flutter/material.dart';
import 'package:geolocator/geolocator.dart';
import 'models/test_result.dart';
import 'consts/config.dart';

/// Location test page measuring models access overhead from a continuous GPS stream.
/// Uses "Stream + Poll" architecture:
/// - Background: Listens to GPS stream.
/// - Foreground: Polls the latest value in a tight loop (10,000 times).
import 'utils/buffered_csv_writer.dart';

class LocationTest extends StatefulWidget {
  final LocationAccuracy desiredAccuracy;
  final BufferedCsvWriter? writer;
  
  const LocationTest({
    super.key,
    this.desiredAccuracy = LocationAccuracy.high,
    this.writer,
  });

  @override
  State<LocationTest> createState() => _LocationTestState();
}

class _LocationTestState extends State<LocationTest> {
  late final int _startTime;
  String _statusMessage = 'Initializing GPS...';
  
  // Shared state
  Position? _latestPosition;
  StreamSubscription<Position>? _positionStream;

  @override
  void initState() {
    super.initState();
    _startTime = DateTime.now().millisecondsSinceEpoch;
    _executeLocationTest();
  }

  @override
  void dispose() {
    _positionStream?.cancel();
    super.dispose();
  }

  /// Executes location acquisition benchmark.
  Future<void> _executeLocationTest() async {
    try {
      // 1. Check services and permissions
      if (!await _checkLocationServiceEnabled()) {
        _finishWithError('Location services disabled');
        return;
      }

      final permission = await _checkAndRequestPermission();
      if (permission == LocationPermission.denied ||
          permission == LocationPermission.deniedForever) {
        _finishWithError('Location permission denied');
        return;
      }

      // 2. Start Stream (Background Producer)
      _updateStatus('Starting GPS Stream...');
      final streamCompleter = Completer<void>();
      
      _positionStream = Geolocator.getPositionStream(
        locationSettings: const LocationSettings(
          accuracy: LocationAccuracy.high,
          distanceFilter: 0, 
        ),
      ).listen(
        (Position position) {
          _latestPosition = position;
          if (!streamCompleter.isCompleted) {
             streamCompleter.complete(); // First fix received
          }
        },
        onError: (error) {
          if (!streamCompleter.isCompleted) {
            streamCompleter.completeError(error);
          }
        },
      );

      // Wait for first fix to ensure stream is active
      await streamCompleter.future.timeout(const Duration(seconds: 15), onTimeout: () {
         throw TimeoutException("Timed out waiting for first GPS fix");
      });

      // 3. Benchmark Loop (Foreground Consumer)
      int samples = 0;
      int successCount = 0;
      final int targetSamples = Config.sampleCount; // 10,000

      _updateStatus('Testing access overhead...');
      
      while (samples < targetSamples) {
        if (!mounted) return;

        final loopStart = DateTime.now().millisecondsSinceEpoch;
        
        // Poll latest value
        final pos = _latestPosition;
        if (pos != null) {
          successCount++;
        }
        
        final loopEnd = DateTime.now().millisecondsSinceEpoch;
        final duration = loopEnd - loopStart;
        
        samples++;
        
        // Write sample to CSV
        if (widget.writer != null) {
             final details = pos != null 
                 ? 'Lat:${pos.latitude.toStringAsFixed(6)},Lon:${pos.longitude.toStringAsFixed(6)}' 
                 : 'No Signal';
             
             // timestamp, duration, details, intervalStart, intervalDuration, cumulative
             // We use loopStart as intervalStart for single-sample granularity
             await widget.writer!.write(
                 samples,
                 duration,
                 details,
                 intervalStartMs: loopStart,
                 intervalDurationMs: duration,
                 cumulativeTimeMs: loopEnd - _startTime,
             );
        }
        
        // Yield execution to allow stream updates to process
        // 1ms delay is enough to let the event loop process the stream
        await Future.delayed(const Duration(milliseconds: 1));
        
        // Update UI occasionally
        if (samples % 100 == 0) {
           _updateStatus('Sampling: $samples / $targetSamples');
        }
      }

      final elapsedMs = DateTime.now().millisecondsSinceEpoch - _startTime;
      final details = 'Stream+Poll: ${targetSamples} samples. Success: $successCount. Last: ${_formatPositionDetails(_latestPosition)}';
      
      final result = TestResult(
        'Location Test',
        elapsedMs,
        details,
        true,
      );

      if (mounted) {
        Navigator.pop(context, result);
      }
    } catch (error) {
      _finishWithError(_formatErrorMessage(error));
    }
  }

  /// Checks if location services are enabled on the device.
  Future<bool> _checkLocationServiceEnabled() async {
    return await Geolocator.isLocationServiceEnabled();
  }

  /// Checks current permission status and requests if necessary.
  Future<LocationPermission> _checkAndRequestPermission() async {
    LocationPermission permission = await Geolocator.checkPermission();

    if (permission == LocationPermission.denied) {
      permission = await Geolocator.requestPermission();
    }

    return permission;
  }

  /// Formats position models into readable string.
  String _formatPositionDetails(Position? position) {
    if (position == null) return 'No fixes acquired';
    return 'Lat: ${position.latitude.toStringAsFixed(6)}, '
        'Lon: ${position.longitude.toStringAsFixed(6)}, '
        'Acc: ${position.accuracy.toStringAsFixed(1)}m';
  }

  /// Formats error message based on exception type.
  String _formatErrorMessage(dynamic error) {
    if (error is LocationServiceDisabledException) {
      return 'Location services disabled';
    } else if (error is PermissionDeniedException) {
      return 'Permission denied';
    } else if (error is TimeoutException) {
      return 'GPS timeout - no fix';
    } else if (error is Exception) {
      return error.toString();
    }
    return 'Error: $error';
  }

  /// Completes test with error result.
  void _finishWithError(String errorMessage) {
    if (!mounted) return;

    final elapsedMs = DateTime.now().millisecondsSinceEpoch - _startTime;
    final result = TestResult(
      'Location Test',
      elapsedMs,
      errorMessage,
      false,
    );

    Navigator.pop(context, result);
  }

  /// Updates UI with status message.
  void _updateStatus(String message) {
    if (!mounted) return;
    setState(() => _statusMessage = message);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            const CircularProgressIndicator(),
            const SizedBox(height: 20),
            Text(
              _statusMessage,
              textAlign: TextAlign.center,
              style: const TextStyle(
                fontSize: 16,
                fontWeight: FontWeight.w600,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

