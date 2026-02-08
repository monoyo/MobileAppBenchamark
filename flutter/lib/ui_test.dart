import 'dart:math';
import 'package:flutter/material.dart';
import 'package:flutter/scheduler.dart';
import 'models/test_result.dart';
import 'config/sample_configuration.dart';

/// UI performance test page rendering animated rectangles.
/// Tests frame rendering performance and animation smoothness.
/// Matches Java/Kotlin GPUTestActivity algorithm.
class UITestPage extends StatefulWidget {
  const UITestPage({super.key});

  @override
  State<UITestPage> createState() => _UITestPageState();
}

class _UITestPageState extends State<UITestPage> with SingleTickerProviderStateMixin {
  // Match Java/Kotlin constants
  static const int _initialObjectCount = 250;
  static const int _objectIncrementPerSecond = 250;
  static const double _objectSize = 50.0;
  static const double _maxVelocity = 10.0; // ±10 px/frame like Java

  late final int _startTime;
  late Ticker _ticker;
  
  int _frameCount = 0;
  int _currentObjectCount = 0;
  double _currentFps = 0.0;
  int _lastFpsUpdateMs = 0;
  int _framesSinceFpsUpdate = 0;
  
  // Object data (like Java arrays)
  final List<Rect> _objects = [];
  final List<Color> _colors = [];
  final List<double> _velocitiesX = [];
  final List<double> _velocitiesY = [];
  final Random _random = Random();
  
  Size _screenSize = Size.zero;
  bool _disposed = false;

  @override
  void initState() {
    super.initState();
    _startTime = DateTime.now().millisecondsSinceEpoch;
    _lastFpsUpdateMs = _startTime;
    
    _ticker = createTicker(_onFrame);
    
    // Defer start until we have screen size
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted && !_disposed) {
        _screenSize = MediaQuery.of(context).size;
        _initObjects(_initialObjectCount);
        _ticker.start();
      }
    });
  }

  void _initObjects(int count) {
    _objects.clear();
    _colors.clear();
    _velocitiesX.clear();
    _velocitiesY.clear();
    _currentObjectCount = 0;
    _addObjects(count);
  }

  void _addObjects(int count) {
    if (count <= 0) return;
    
    final maxW = _screenSize.width > 0 ? _screenSize.width : 1000.0;
    final maxH = _screenSize.height > 0 ? _screenSize.height : 2000.0;

    for (int i = 0; i < count; i++) {
      final x = _random.nextDouble() * (maxW - _objectSize);
      final y = _random.nextDouble() * (maxH - _objectSize);

      _objects.add(Rect.fromLTWH(x, y, _objectSize, _objectSize));
      
      // Deterministic color based on hue
      final hue = ((_currentObjectCount + i) % 12) * 30.0;
      _colors.add(HSVColor.fromAHSV(1.0, hue, 0.8, 0.9).toColor());
      
      // Random velocity: (random - 0.5) * 20 = range [-10, 10]
      _velocitiesX.add((_random.nextDouble() - 0.5) * _maxVelocity * 2);
      _velocitiesY.add((_random.nextDouble() - 0.5) * _maxVelocity * 2);
    }
    _currentObjectCount += count;
  }

  void _onFrame(Duration elapsed) {
    if (_disposed || !mounted) return;

    final now = DateTime.now().millisecondsSinceEpoch;
    final elapsedMs = now - _startTime;
    
    // Update FPS every 500ms
    _framesSinceFpsUpdate++;
    if (now - _lastFpsUpdateMs >= 500) {
      final elapsedSeconds = (now - _lastFpsUpdateMs) / 1000.0;
      _currentFps = _framesSinceFpsUpdate / elapsedSeconds;
      _framesSinceFpsUpdate = 0;
      _lastFpsUpdateMs = now;
    }

    // Manage object count (add 250 per second like Java)
    final secondsElapsed = elapsedMs ~/ 1000;
    final desiredObjects = _initialObjectCount + (secondsElapsed * _objectIncrementPerSecond);
    if (desiredObjects > _currentObjectCount) {
      _addObjects(desiredObjects - _currentObjectCount);
    }

    // Update positions with physics
    _updatePositions();

    _frameCount++;

    // Check completion based on sample count
    if (_frameCount >= SampleConfiguration.samplesAmount) {
      _completeTest();
    } else {
      setState(() {}); // Trigger repaint
    }
  }

  void _updatePositions() {
    if (_screenSize.width == 0) return;

    for (int i = 0; i < _objects.length; i++) {
      var rect = _objects[i];
      var vx = _velocitiesX[i];
      var vy = _velocitiesY[i];

      // Move
      rect = rect.translate(vx, vy);

      // Bounce off walls (like Java handleBoundsCollision)
      if (rect.left < 0 || rect.right > _screenSize.width) {
        vx = -vx;
        _velocitiesX[i] = vx;
        rect = rect.translate(-vx * 2, 0);
      }
      if (rect.top < 0 || rect.bottom > _screenSize.height) {
        vy = -vy;
        _velocitiesY[i] = vy;
        rect = rect.translate(0, -vy * 2);
      }

      _objects[i] = rect;
    }
  }

  void _completeTest() {
    _ticker.stop();
    _disposed = true;

    if (!mounted) return;

    final elapsedMs = DateTime.now().millisecondsSinceEpoch - _startTime;
    final details = 'Max Objects: $_currentObjectCount, Samples: $_frameCount, FPS: ${_currentFps.toStringAsFixed(1)}';

    final result = TestResult('UI Test', elapsedMs, details, true);
    Navigator.pop(context, result);
  }

  @override
  void dispose() {
    _disposed = true;
    _ticker.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.white,
      body: Stack(
        children: [
          // Render all squares using CustomPaint for performance
          CustomPaint(
            size: _screenSize,
            painter: _SquaresPainter(_objects, _colors),
          ),
          // Info overlay
          Positioned(
            left: 20,
            top: 40,
            child: Container(
              padding: const EdgeInsets.all(8),
              color: Colors.white.withOpacity(0.8),
              child: Text(
                'Samples: $_frameCount / ${SampleConfiguration.samplesAmount}\n'
                'Objects: $_currentObjectCount\n'
                'FPS: ${_currentFps.toStringAsFixed(1)}',
                style: const TextStyle(fontSize: 14, color: Colors.black),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

/// Custom painter for efficient rendering of many rectangles.
class _SquaresPainter extends CustomPainter {
  final List<Rect> objects;
  final List<Color> colors;

  _SquaresPainter(this.objects, this.colors);

  @override
  void paint(Canvas canvas, Size size) {
    final paint = Paint()..style = PaintingStyle.fill;
    
    for (int i = 0; i < objects.length; i++) {
      paint.color = colors[i];
      canvas.drawRect(objects[i], paint);
    }
  }

  @override
  bool shouldRepaint(covariant _SquaresPainter oldDelegate) => true;
}

