import 'dart:math';
import 'package:flutter/material.dart';
import 'package:flutter/scheduler.dart';
import 'models/test_result.dart';

/// UI performance test page rendering animated rectangles.
/// Tests frame rendering performance and animation smoothness.
/// Similar to Java UITest with animated elements and frame tracking.
class UITestPage extends StatefulWidget {
  final Duration testDuration;
  final int rectangleCount;
  
  const UITestPage({
    super.key,
    this.testDuration = const Duration(seconds: 2),
    this.rectangleCount = 1500,
  });

  @override
  State<UITestPage> createState() => _UITestPageState();
}

class _UITestPageState extends State<UITestPage> with TickerProviderStateMixin {
  late final int _startTime;
  late final List<AnimationController> _controllers;
  late final FrameCounter _frameCounter;
  int _frameCount = 0;

  @override
  void initState() {
    super.initState();
    _startTime = DateTime.now().millisecondsSinceEpoch;
    _frameCounter = FrameCounter();
    _frameCounter.start();

    // Create animation controllers for each rectangle
    _controllers = List.generate(
      widget.rectangleCount,
      (index) => AnimationController(
        vsync: this,
        duration: Duration(milliseconds: 800 + (index % 200)),
      )..repeat(),
    );

    // Schedule test completion
    Future.delayed(widget.testDuration, _completeTest);
  }

  void _completeTest() {
    _frameCounter.stop();
    
    if (!mounted) return;

    final elapsedMs = DateTime.now().millisecondsSinceEpoch - _startTime;
    final fps = _frameCounter.averageFps;
    final frameText = 'Rendered ${widget.rectangleCount} animating rectangles, '
        'FPS: ${fps.toStringAsFixed(2)}, Frames: ${_frameCounter.totalFrames}';
    
    final result = TestResult('UI Test', elapsedMs, frameText, true);
    Navigator.pop(context, result);
  }

  @override
  void dispose() {
    for (final controller in _controllers) {
      controller.dispose();
    }
    _frameCounter.stop();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Stack(
        children: List.generate(
          widget.rectangleCount,
          (index) => _AnimatedRectangle(
            controller: _controllers[index],
            index: index,
            screenSize: MediaQuery.of(context).size,
          ),
        ),
      ),
    );
  }
}

/// Single animated rectangle widget.
class _AnimatedRectangle extends StatefulWidget {
  final AnimationController controller;
  final int index;
  final Size screenSize;

  const _AnimatedRectangle({
    required this.controller,
    required this.index,
    required this.screenSize,
  });

  @override
  State<_AnimatedRectangle> createState() => _AnimatedRectangleState();
}

class _AnimatedRectangleState extends State<_AnimatedRectangle>
    with SingleTickerProviderStateMixin {
  late final Animation<double> _positionX;
  late final Animation<double> _positionY;
  late final Color _color;

  @override
  void initState() {
    super.initState();
    
    final random = Random(widget.index);
    final startX = random.nextDouble() * (widget.screenSize.width - 20);
    final startY = random.nextDouble() * (widget.screenSize.height - 20);
    final endX = random.nextDouble() * (widget.screenSize.width - 20);
    final endY = random.nextDouble() * (widget.screenSize.height - 20);

    // Create position animations with tween sequences
    _positionX = TweenSequence<double>([
      TweenSequenceItem(
        tween: Tween<double>(begin: startX, end: endX),
        weight: 50,
      ),
      TweenSequenceItem(
        tween: Tween<double>(begin: endX, end: startX),
        weight: 50,
      ),
    ]).animate(widget.controller);

    _positionY = TweenSequence<double>([
      TweenSequenceItem(
        tween: Tween<double>(begin: startY, end: endY),
        weight: 50,
      ),
      TweenSequenceItem(
        tween: Tween<double>(begin: endY, end: startY),
        weight: 50,
      ),
    ]).animate(widget.controller);

    // Deterministic color based on index
    _color = _generateColor(widget.index);
  }

  /// Generates deterministic color for rectangle.
  Color _generateColor(int index) {
    final hue = (index % 12) * 30.0;
    return HSVColor.fromAHSV(1.0, hue, 0.8, 0.9).toColor();
  }

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: widget.controller,
      builder: (context, child) {
        return Positioned(
          left: _positionX.value,
          top: _positionY.value,
          child: Container(
            width: 20,
            height: 20,
            decoration: BoxDecoration(
              color: _color,
              borderRadius: BorderRadius.circular(2),
            ),
          ),
        );
      },
    );
  }
}

/// Tracks frame rendering performance.
class FrameCounter {
  int _totalFrames = 0;
  int _framesSinceLastCheck = 0;
  int _lastCheckTime = 0;
  double _currentFps = 0.0;
  bool _isRunning = false;
  final List<double> _fpsHistory = [];

  void start() {
    if (_isRunning) return;
    _isRunning = true;
    _totalFrames = 0;
    _framesSinceLastCheck = 0;
    _lastCheckTime = DateTime.now().millisecondsSinceEpoch;
    _fpsHistory.clear();
    
    SchedulerBinding.instance.addPersistentFrameCallback(_onFrame);
  }

  void stop() {
    _isRunning = false;
  }

  void _onFrame(Duration timestamp) {
    if (!_isRunning) return;

    _totalFrames++;
    _framesSinceLastCheck++;

    final now = DateTime.now().millisecondsSinceEpoch;
    const checkIntervalMs = 500; // Update FPS every 500ms

    if (now - _lastCheckTime >= checkIntervalMs) {
      final elapsedSeconds = (now - _lastCheckTime) / 1000.0;
      _currentFps = _framesSinceLastCheck / elapsedSeconds;
      _fpsHistory.add(_currentFps);
      
      _framesSinceLastCheck = 0;
      _lastCheckTime = now;
    }
  }

  /// Returns current frames per second.
  double get currentFps => _currentFps;

  /// Returns average FPS across all measurements.
  double get averageFps {
    if (_fpsHistory.isEmpty) return 0.0;
    return _fpsHistory.reduce((a, b) => a + b) / _fpsHistory.length;
  }

  /// Returns total frames rendered.
  int get totalFrames => _totalFrames;
}
