import 'package:flutter/scheduler.dart';

class FpsCounter {
  int _frames = 0;
  double _fps = 0.0;
  int _lastTime = 0;
  Duration _updateInterval = const Duration(milliseconds: 1000);
  bool _running = false;

  void start() {
    if (_running) return;
    _running = true;
    _lastTime = DateTime.now().millisecondsSinceEpoch;
    _frames = 0;
    SchedulerBinding.instance.addPersistentFrameCallback(_onFrame);
  }

  void stop() {
    if (!_running) return;
    _running = false;
    // Note: We cannot easily remove a specific callback from persistent callbacks in standard API easily
    // without hacks, but we can just make the callback a no-op if !running, OR just ignore it.
    // Actually, simple frame callback (addPostFrameCallback) is one-shot. Persistent is... persistent.
    // Better approach: use Ticker or manual requestFrame?
    // Let's us a simple boolean gate in the callback.
  }

  void _onFrame(Duration timestamp) {
    if (!_running) return; // Keep it alive but do nothing? Or keep accumulating?
    // Ideally we want to unregister. But for now, let's just count.
    
    _frames++;
    final now = DateTime.now().millisecondsSinceEpoch;
    if (now - _lastTime >= _updateInterval.inMilliseconds) {
      _fps = _frames / ((now - _lastTime) / 1000.0);
      _frames = 0;
      _lastTime = now;
    }
  }

  double get fps => _fps;
}
