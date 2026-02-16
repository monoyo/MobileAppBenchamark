import 'dart:math';
import 'dart:ui' show FrameTiming, FramePhase;
import 'package:flutter/material.dart';
import 'package:flutter/scheduler.dart';
import 'models/test_result.dart';
import 'consts/config.dart';
import 'utils/buffered_csv_writer.dart';


/// UI performance test page rendering animated rectangles.
/// Tests frame rendering performance and animation smoothness.
/// Matches Java/Kotlin UiTest algorithm.
class UiTest extends StatefulWidget {
  final BufferedCsvWriter? writer;
  
  const UiTest({super.key, this.writer});

  @override
  State<UiTest> createState() => _UiTestState();
}

class _UiTestState extends State<UiTest> with SingleTickerProviderStateMixin {
  // Match Java/Kotlin consts (Physical Pixels)
  static const int _initialObjectCount = 250;
  static const int _objectIncrementPerSecond = 250;
  static const double _nativeObjectSizePx = 50.0;
  static const double _nativeMaxVelocityPx = 10.0;
  static const double _minFps = 10.0;
  static const int _warmupFrames = 30;
  // Fallback: max test duration if FrameTiming doesn't report data
  static const int _maxTestDurationMs = 120000; // 2 minutes

  late final int _startTime;
  late Ticker _ticker;
  late final _StressNotifier _notifier;
  
  int _frameCount = 0;
  int _currentObjectCount = 0;

  /// Actual rendered FPS measured by counting FrameTiming callbacks
  /// in a 1-second sliding window. This is the ONLY reliable way to
  /// measure visual FPS in Flutter, as the Ticker fires at vsync rate
  /// even when the GPU/raster thread can't keep up.
  double _renderFps = 0.0;
  bool _hasRenderData = false;
  final List<int> _renderTimestamps = []; // wall-clock ms of each rendered frame

  int _lastUiUpdateFrame = 0;
  int _lastFrameTimestamp = 0; // Microseconds of previous frame's rasterFinish
  
  // Object models
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
    // Register for actual frame timing reports — each callback represents
    // frames that were ACTUALLY rendered and displayed on screen
    SchedulerBinding.instance.addTimingsCallback(_onFrameTimings);
  }

  // Queue to store frame data (id, objectCount) when submitted to engine
  final List<_FrameData> _frameDataQueue = [];

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
        
        _colors.add(Color.fromARGB(
          255,
          _random.nextInt(256),
          _random.nextInt(256),
          _random.nextInt(256),
        ));
        
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

    // Enqueue data for this frame. It will be matched with timing report later.
    _frameDataQueue.add(_FrameData(_frameCount, _currentObjectCount, elapsedMs));

    // Update text overlay only every 10 frames to avoid widget rebuild overhead
    if (_frameCount - _lastUiUpdateFrame >= 10) {
      _lastUiUpdateFrame = _frameCount;
      setState(() {});
    }

    // Fallback: max test duration (in case FrameTiming never reports)
    if (elapsedMs > _maxTestDurationMs) {
      debugPrint('Test exceeded max duration ${_maxTestDurationMs}ms, '
          'stopping at frame $_frameCount');
      _completeTest();
    }
  }

  /// Called by the engine with actual rendered frame timings.
  void _onFrameTimings(List<FrameTiming> timings) {
    if (_disposed || timings.isEmpty) return;
    
    _hasRenderData = true;

    for (final timing in timings) {
      if (_frameDataQueue.isEmpty) break;
      
      final frameData = _frameDataQueue.removeAt(0);
      
      // Calculate display refresh rate using VSync timestamps.
      // We use vsyncStart because it aligns with the device's display refresh cycle.
      // rasterFinish measures GPU throughput, which can exceed the refresh rate (e.g. 300fps on 120Hz).
      double frameTimeMs;

      if (_lastFrameTimestamp == 0) {
          // Skip the first frame for delta calculation as we don't have a previous reference
          frameTimeMs = 0;
      } else {
          // Calculate delta in microseconds then convert to ms
          // FrameTiming.timestampInMicroseconds(FramePhase.vsyncStart) gives the VSync signal time.
          final currentTimestamp = timing.timestampInMicroseconds(FramePhase.vsyncStart);
          final delta = currentTimestamp - _lastFrameTimestamp;
          
          if (delta <= 0) {
            // Invalid or duplicate timestamp, ignore this frame for FPS calc
            frameTimeMs = 0;
          } else {
            frameTimeMs = delta / 1000.0;
          }
          
          _lastFrameTimestamp = currentTimestamp;
      }
      
      // Initialize timestamp if it was 0
      if (_lastFrameTimestamp == 0) {
          _lastFrameTimestamp = timing.timestampInMicroseconds(FramePhase.vsyncStart);
      }

      final fps = frameTimeMs > 0 ? 1000.0 / frameTimeMs : 0.0;

      // Update sliding window for UI display
      final now = DateTime.now().millisecondsSinceEpoch;
      _renderTimestamps.add(now);

       // Remove timestamps older than 1 second
      final cutoff = now - 1000;
      while (_renderTimestamps.isNotEmpty && _renderTimestamps.first < cutoff) {
        _renderTimestamps.removeAt(0);
      }
      _renderFps = _renderTimestamps.length.toDouble();

      // Write to CSV
      if (widget.writer != null) {
        widget.writer!.write([
          frameData.frameId,
          frameData.objectCount,
          frameTimeMs.toStringAsFixed(2),
          fps.toStringAsFixed(1),
          frameData.elapsedMs
        ]);
      }
      
      // Check termination condition based on PROCESSED frames
      if (frameData.frameId >= Config.sampleCount) {
         _completeTest();
         return;
      }
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
    SchedulerBinding.instance.removeTimingsCallback(_onFrameTimings);
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
                        'Render FPS: ${_renderFps.toStringAsFixed(1)}'
                        '${_hasRenderData ? "" : " (waiting...)"}',
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

class _FrameData {
  final int frameId;
  final int objectCount;
  final int elapsedMs;

  _FrameData(this.frameId, this.objectCount, this.elapsedMs);
}
