import 'package:flutter/material.dart';
import 'package:geolocator/geolocator.dart';
import 'models/test_result.dart';

/// Location test page measuring GPS acquisition and position accuracy.
/// Tests permission handling, service availability, and position retrieval.
class LocationTestPage extends StatefulWidget {
  final LocationAccuracy desiredAccuracy;
  
  const LocationTestPage({
    super.key,
    this.desiredAccuracy = LocationAccuracy.high,
  });

  @override
  State<LocationTestPage> createState() => _LocationTestPageState();
}

class _LocationTestPageState extends State<LocationTestPage> {
  late final int _startTime;
  String _statusMessage = 'Initializing...';

  @override
  void initState() {
    super.initState();
    _startTime = DateTime.now().millisecondsSinceEpoch;
    _executeLocationTest();
  }

  /// Executes location acquisition benchmark with proper permission and service checks.
  Future<void> _executeLocationTest() async {
    try {
      // 1. Check if location services are enabled
      if (!await _checkLocationServiceEnabled()) {
        _finishWithError('Location services disabled');
        return;
      }

      // 2. Check and request permissions
      final permission = await _checkAndRequestPermission();
      if (permission == LocationPermission.denied ||
          permission == LocationPermission.deniedForever) {
        _finishWithError('Location permission denied');
        return;
      }

      // 3. Acquire current position
      _updateStatus('Acquiring GPS position...');
      final position = await Geolocator.getCurrentPosition(
        desiredAccuracy: widget.desiredAccuracy,
        timeLimit: const Duration(seconds: 30),
      );

      if (!mounted) return;

      final elapsedMs = DateTime.now().millisecondsSinceEpoch - _startTime;
      final details = _formatPositionDetails(position);
      
      final result = TestResult(
        'Location Test',
        elapsedMs,
        details,
        true,
      );

      Navigator.pop(context, result);
    } catch (error) {
      _finishWithError(_formatErrorMessage(error));
    }
  }

  /// Checks if location services are enabled on the device.
  Future<bool> _checkLocationServiceEnabled() async {
    _updateStatus('Checking location services...');
    return await Geolocator.isLocationServiceEnabled();
  }

  /// Checks current permission status and requests if necessary.
  Future<LocationPermission> _checkAndRequestPermission() async {
    _updateStatus('Checking permissions...');
    LocationPermission permission = await Geolocator.checkPermission();

    if (permission == LocationPermission.denied) {
      _updateStatus('Requesting location permission...');
      permission = await Geolocator.requestPermission();
    }

    return permission;
  }

  /// Formats position data into readable string.
  String _formatPositionDetails(Position position) {
    return 'Lat: ${position.latitude.toStringAsFixed(6)}, '
        'Lon: ${position.longitude.toStringAsFixed(6)}, '
        'Accuracy: ${position.accuracy.toStringAsFixed(1)}m, '
        'Altitude: ${position.altitude.toStringAsFixed(1)}m';
  }

  /// Formats error message based on exception type.
  String _formatErrorMessage(dynamic error) {
    if (error is LocationServiceDisabledException) {
      return 'Location services disabled';
    } else if (error is PermissionDeniedException) {
      return 'Permission denied';
    } else if (error is TimeoutException) {
      return 'GPS timeout - no fix acquired';
    } else if (error is Exception) {
      return error.toString();
    }
    return 'Unknown error: $error';
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
            const SizedBox(height: 24),
            Text(
              _statusMessage,
              textAlign: TextAlign.center,
              style: Theme.of(context).textTheme.bodyMedium,
            ),
          ],
        ),
      ),
    );
  }
}
