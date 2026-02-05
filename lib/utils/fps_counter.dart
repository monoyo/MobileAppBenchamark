import 'package:flutter/scheduler.dart';

/// Tracks application frame rate performance.
/// Measures FPS by monitoring frame callbacks from the scheduler.
class FpsCounter {
  int _frameCount = 0;
  double _averageFps = 0.0;
  int _lastUpdateTime = 0;
  final Duration _updateInterval;
  bool _isRunning = false;
  final List<double> _fpsReadings = [];

  FpsCounter({Duration updateInterval = const Duration(milliseconds: 1000)})
      : _updateInterval = updateInterval;

  /// Starts FPS measurement.
  void start() {
    if (_isRunning) return;
    
    _isRunning = true;
    _lastUpdateTime = DateTime.now().millisecondsSinceEpoch;
    _frameCount = 0;
    _fpsReadings.clear();
    
    SchedulerBinding.instance.addPersistentFrameCallback(_onFrame);
  }

  /// Stops FPS measurement.
  void stop() {
    _isRunning = false;
  }

  /// Frame callback handler called on each frame.
  void _onFrame(Duration timestamp) {
    if (!_isRunning) return;

    _frameCount++;
    
    final now = DateTime.now().millisecondsSinceEpoch;
    final elapsedMs = now - _lastUpdateTime;

    if (elapsedMs >= _updateInterval.inMilliseconds) {
      final elapsedSeconds = elapsedMs / 1000.0;
      final fps = _frameCount / elapsedSeconds;
      
      _fpsReadings.add(fps);
      _averageFps = _calculateAverage(_fpsReadings);
      
      _frameCount = 0;
      _lastUpdateTime = now;
    }
  }

  /// Calculates average FPS from all readings.
  double _calculateAverage(List<double> values) {
    if (values.isEmpty) return 0.0;
    final sum = values.fold<double>(0.0, (a, b) => a + b);
    return sum / values.length;
  }

  /// Returns current average FPS.
  double get fps => _averageFps;

  /// Returns all FPS readings collected.
  List<double> get readings => List.unmodifiable(_fpsReadings);

  /// Returns minimum FPS recorded.
  double get minFps => _fpsReadings.isEmpty ? 0.0 : _fpsReadings.reduce((a, b) => a < b ? a : b);

  /// Returns maximum FPS recorded.
  double get maxFps => _fpsReadings.isEmpty ? 0.0 : _fpsReadings.reduce((a, b) => a > b ? a : b);
}
