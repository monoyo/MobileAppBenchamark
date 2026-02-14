import 'dart:math';
import 'package:flutter/material.dart';
import 'package:flutter/scheduler.dart';
import 'models/test_result.dart';
import 'consts/config.dart';


/// UI performance test page rendering animated rectangles.
/// Tests frame rendering performance and animation smoothness.
/// Matches Java/Kotlin UiTest algorithm.
class UiTest extends StatefulWidget {
  const UiTest({super.key});

  @override
  State<UiTest> createState() => _UiTestState();
}

class _UiTestState extends State<UiTest> with SingleTickerProviderStateMixin {
  // Match Java/Kotlin consts (Physical Pixels)
  static const int _initialObjectCount = 250;
  static const int _objectIncrementPerSecond = 250;
  static const double _nativeObjectSizePx = 50.0;
  static const double _nativeMaxVelocityPx = 10.0;

  late final int _startTime;
  late Ticker _ticker;
  late final _StressNotifier _notifier;
  
  int _frameCount = 0;
  int _currentObjectCount = 0;
  double _currentFps = 0.0;
  int _lastFrameTime = 0;
  int _lastUiUpdateFrame = 0;
  
  // Object models — use Float64Lists for better cache locality
  final List<double> _posX = [];
  final List<double> _posY = [];
  final List<double> _velocitiesX = [];
  final List<double> _velocitiesY = [];
  final List<Color> _colors = [];
  final Random _random = Random();
  
  double _objectSize = 50.0;
  Size _viewSize = Size.zero;
  double _devicePixelRatio = 1.0;
  bool _disposed = false;
  bool _initialized = false;

  @override
  void initState() {
    super.initState();
    _startTime = DateTime.now().millisecondsSinceEpoch;
    _notifier = _StressNotifier();
    _ticker = createTicker(_onFrame);
  }

  void _initObjects(int count) {
    _posX.clear();
    _posY.clear();
    _velocitiesX.clear();
    _velocitiesY.clear();
    _colors.clear();
    _currentObjectCount = 0;
    _addObjects(count);
  }

  void _addObjects(int count) {
    if (count <= 0 || _viewSize.isEmpty) return;
    
    final maxW = _viewSize.width;
    final maxH = _viewSize.height;

    for (int i = 0; i < count; i++) {
        final x = _random.nextDouble() * (maxW - _objectSize);
        final y = _random.nextDouble() * (maxH - _objectSize);

        _posX.add(x);
        _posY.add(y);
        
        // Match Java: Color.rgb(random.nextInt(256), ...)
        _colors.add(Color.fromARGB(
          255,
          _random.nextInt(256),
          _random.nextInt(256),
          _random.nextInt(256),
        ));
        
        // Match Java: (random.nextFloat() - 0.5f) * 20
        final maxVelocity = _nativeMaxVelocityPx / _devicePixelRatio;
        _velocitiesX.add((_random.nextDouble() - 0.5) * 2 * maxVelocity);
        _velocitiesY.add((_random.nextDouble() - 0.5) * 2 * maxVelocity);
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

    // Manage object count
    final secondsElapsed = elapsedMs ~/ 1000;
    final desiredObjects = _initialObjectCount + (secondsElapsed * _objectIncrementPerSecond);
    if (desiredObjects > _currentObjectCount) {
      _addObjects(desiredObjects - _currentObjectCount);
    }

    // Update positions in-place
    _updatePositions();

    _frameCount++;

    // Notify painter to repaint (bypasses widget tree rebuild)
    _notifier.repaint();

    // Update text overlay only every 10 frames to avoid widget rebuild overhead
    if (_frameCount - _lastUiUpdateFrame >= 10) {
      _lastUiUpdateFrame = _frameCount;
      setState(() {});
    }

    if (_frameCount >= Config.sampleCount) {
      _completeTest();
    }
  }

  void _updatePositions() {
    if (_viewSize.isEmpty) return;
    
    final width = _viewSize.width;
    final height = _viewSize.height;
    final size = _objectSize;

    for (int i = 0; i < _currentObjectCount; i++) {
      var x = _posX[i] + _velocitiesX[i];
      var y = _posY[i] + _velocitiesY[i];

      // Bounce off walls — matches Java handleBoundsCollision
      if (x < 0 || x + size > width) {
        _velocitiesX[i] = -_velocitiesX[i];
        x += _velocitiesX[i] * 2;
      }
      if (y < 0 || y + size > height) {
        _velocitiesY[i] = -_velocitiesY[i];
        y += _velocitiesY[i] * 2;
      }

      _posX[i] = x;
      _posY[i] = y;
    }
  }

  void _completeTest() {
    _ticker.stop();
    _disposed = true;

    if (!mounted) return;

    final elapsedMs = DateTime.now().millisecondsSinceEpoch - _startTime;
    final details = 'Max Objects: $_currentObjectCount, Samples: $_frameCount';

    final result = TestResult('UI Test', elapsedMs, details, true);
    Navigator.pop(context, result);
  }

  @override
  void dispose() {
    _disposed = true;
    _ticker.dispose();
    _notifier.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    _devicePixelRatio = MediaQuery.of(context).devicePixelRatio;
    _objectSize = _nativeObjectSizePx / _devicePixelRatio;

    return Scaffold(
      backgroundColor: Colors.white,
      body: LayoutBuilder(
        builder: (context, constraints) {
            final newSize = Size(constraints.maxWidth, constraints.maxHeight);
            
            if (newSize != _viewSize) {
                _viewSize = newSize;
                
                if (!_initialized && !_viewSize.isEmpty) {
                    _initialized = true;
                    _initObjects(_initialObjectCount);
                    _ticker.start();
                }
            }

            return Stack(
                children: [
                // RepaintBoundary isolates the CustomPaint from the text overlay
                RepaintBoundary(
                  child: CustomPaint(
                      size: _viewSize,
                      painter: _SquaresPainter(
                        _posX, _posY, _colors,
                        _objectSize, _notifier,
                      ),
                  ),
                ),
                Positioned(
                    left: 20,
                    top: 40,
                    child: Container(
                    padding: const EdgeInsets.all(8),
                    color: Colors.white.withOpacity(0.8),
                    child: Text(
                        'Samples: $_frameCount / ${Config.sampleCount}\n'
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

/// Notifier to trigger repaint without widget tree rebuild.
class _StressNotifier extends ChangeNotifier {
  void repaint() {
    notifyListeners();
  }
}

/// Custom painter that listens to a ChangeNotifier for repaint signals.
/// This avoids the overhead of setState/widget rebuild on every frame.
class _SquaresPainter extends CustomPainter {
  final List<double> posX;
  final List<double> posY;
  final List<Color> colors;
  final double objectSize;

  _SquaresPainter(this.posX, this.posY, this.colors, this.objectSize,
      _StressNotifier notifier)
      : super(repaint: notifier);

  @override
  void paint(Canvas canvas, Size size) {
    final paint = Paint()
      ..style = PaintingStyle.fill
      ..isAntiAlias = false; // Match Java: paint.setAntiAlias(false)

    final count = posX.length;
    for (int i = 0; i < count; i++) {
      paint.color = colors[i];
      canvas.drawRect(
        Rect.fromLTWH(posX[i], posY[i], objectSize, objectSize),
        paint,
      );
    }
  }

  @override
  bool shouldRepaint(covariant _SquaresPainter oldDelegate) => false;
  // Repainting is driven by the ChangeNotifier, not by shouldRepaint
}
