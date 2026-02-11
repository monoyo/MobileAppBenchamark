import 'dart:math';
import 'package:flutter/material.dart';
import 'package:flutter/scheduler.dart';
import 'models/test_result.dart';
import 'consts/config.dart';


/// UI performance test page rendering animated rectangles.
/// Tests frame rendering performance and animation smoothness.
/// Matches Java/Kotlin GPUTestActivity algorithm.
class UiTest extends StatefulWidget {
  const UiTest({super.key});

  @override
  State<UiTest> createState() => _UiTestState();
}

class _UiTestState extends State<UiTest> with SingleTickerProviderStateMixin {
  // Match Java/Kotlin consts (Physical Pixels)
  static const int _initialObjectCount = 250;
  static const int _objectIncrementPerSecond = 250;
  static const double _nativeObjectSizePx = 50.0; // 50 physical pixels
  static const double _nativeMaxVelocityPx = 10.0; // 10 physical pixels

  late final int _startTime;
  late Ticker _ticker;
  
  int _frameCount = 0;
  int _currentObjectCount = 0;
  double _currentFps = 0.0;
  int _lastFpsUpdateMs = 0;
  int _framesSinceFpsUpdate = 0;
  int _lastFrameTime = 0;
  
  // Object models
  final List<Rect> _objects = [];
  final List<Color> _colors = [];
  final List<double> _velocitiesX = [];
  final List<double> _velocitiesY = [];
  final Random _random = Random();
  
  Size _viewSize = Size.zero; // Size of the rendering area in logical pixels
  double _devicePixelRatio = 1.0;
  bool _disposed = false;
  bool _initialized = false;

  @override
  void initState() {
    super.initState();
    _startTime = DateTime.now().millisecondsSinceEpoch;
    _lastFpsUpdateMs = _startTime;
    
    _ticker = createTicker(_onFrame);
    // Ticker will be started when we have valid size from LayoutBuilder
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
    if (count <= 0 || _viewSize.isEmpty) return;
    
    // Logic uses logical pixels, so we convert physical consts to logical
    final objectSizeLogical = _nativeObjectSizePx / _devicePixelRatio;
    final maxVelocityLogical = _nativeMaxVelocityPx / _devicePixelRatio;

    final maxW = _viewSize.width;
    final maxH = _viewSize.height;

    for (int i = 0; i < count; i++) {
        // Random position within bounds
        // Ensure we don't spawn outside if size is small, though maxW should be >> objectSize
        final x = _random.nextDouble() * (maxW - objectSizeLogical);
        final y = _random.nextDouble() * (maxH - objectSizeLogical);

        _objects.add(Rect.fromLTWH(x, y, objectSizeLogical, objectSizeLogical));
        
        // Deterministic color
        final hue = ((_currentObjectCount + i) % 12) * 30.0;
        _colors.add(HSVColor.fromAHSV(1.0, hue, 0.8, 0.9).toColor());
        
        // Random velocity: (random - 0.5) * 2 * maxVelocity
        _velocitiesX.add((_random.nextDouble() - 0.5) * 2 * maxVelocityLogical);
        _velocitiesY.add((_random.nextDouble() - 0.5) * 2 * maxVelocityLogical);
    }
    _currentObjectCount += count;
  }

  void _onFrame(Duration elapsed) {
    if (_disposed || !mounted || !_initialized) return;

    final now = DateTime.now().millisecondsSinceEpoch;
    final elapsedMs = now - _startTime;
    
    // Calculate instantaneous FPS
    if (_lastFrameTime != 0) {
      final delta = now - _lastFrameTime;
      if (delta > 0) {
        _currentFps = 1000.0 / delta;
      }
    }
    _lastFrameTime = now;

    // Update FPS every 500ms for overlay (optional, but requested 16ms sampling)
    // User requested "sampling" to be 16ms.
    // If we just update _currentFps every frame, the print log will use it.
    
    /* Removed 500ms averaging block */

    // Manage object count
    final secondsElapsed = elapsedMs ~/ 1000;
    final desiredObjects = _initialObjectCount + (secondsElapsed * _objectIncrementPerSecond);
    if (desiredObjects > _currentObjectCount) {
      _addObjects(desiredObjects - _currentObjectCount);
    }

    // Update positions
    _updatePositions();

    _frameCount++;

    print('Frame $_frameCount: Objects=$_currentObjectCount, FPS=${_currentFps.toStringAsFixed(1)}'); 
    if (_frameCount >= SampleConfig.sampleCount || _currentFps < SampleConfig.maxFPS && _currentFps != 0.0) {
      _completeTest();
    } else {
      setState(() {});
    }
  }

  void _updatePositions() {
    if (_viewSize.isEmpty) return;
    
    // Bounds in logical pixels
    final width = _viewSize.width;
    final height = _viewSize.height;

    for (int i = 0; i < _objects.length; i++) {
      var rect = _objects[i];
      var vx = _velocitiesX[i];
      var vy = _velocitiesY[i];

      // Move
      rect = rect.translate(vx, vy);

      // Bounce off walls
      // Note: rect.left/right/top/bottom are in logical pixels
      if (rect.left < 0 || rect.right > width) {
        vx = -vx;
        _velocitiesX[i] = vx;
        // Correct position to stay in bounds
        if (rect.left < 0) {
            rect = rect.shift(Offset(-rect.left * 2, 0)); // reflect
        } else {
             rect = rect.shift(Offset(-(rect.right - width) * 2, 0));
        }
      }
      if (rect.top < 0 || rect.bottom > height) {
        vy = -vy;
        _velocitiesY[i] = vy;
        if (rect.top < 0) {
            rect = rect.shift(Offset(0, -rect.top * 2));
        } else {
            rect = rect.shift(Offset(0, -(rect.bottom - height) * 2));
        }
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
    // Get device pixel ratio for physical <-> logical conversion
    _devicePixelRatio = MediaQuery.of(context).devicePixelRatio;

    return Scaffold(
      backgroundColor: Colors.white,
      body: LayoutBuilder(
        builder: (context, constraints) {
            // Update view size based on actual layout constraints
            final newSize = Size(constraints.maxWidth, constraints.maxHeight);
            
            if (newSize != _viewSize) {
                _viewSize = newSize;
                
                // If this is the first time we have a valid size, start the test
                if (!_initialized && !_viewSize.isEmpty) {
                    _initialized = true;
                    // Initialize objects now that we know the size and pixel ratio
                    _initObjects(_initialObjectCount);
                    _ticker.start();
                }
            }

            return Stack(
                children: [
                CustomPaint(
                    size: _viewSize,
                    painter: _SquaresPainter(_objects, _colors),
                ),
                Positioned(
                    left: 20,
                    top: 40,
                    child: Container(
                    padding: const EdgeInsets.all(8),
                    color: Colors.white.withOpacity(0.8),
                    child: Text(
                        'Samples: $_frameCount / ${SampleConfig.sampleCount}\n'
                        'Objects: $_currentObjectCount\n'
                        'FPS: ${_currentFps.toStringAsFixed(1)}',
                        style: const TextStyle(fontSize: 14, color: Colors.black),
                    ),
                    ),
                ),
                ],
            );
        },
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

