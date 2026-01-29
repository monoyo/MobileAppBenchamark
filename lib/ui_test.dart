import 'dart:math';
import 'package:flutter/material.dart';
import 'models/test_result.dart';

class UITestPage extends StatefulWidget {
  const UITestPage({super.key});

  @override
  State<UITestPage> createState() => _UITestPageState();
}

class _UITestPageState extends State<UITestPage>
    with SingleTickerProviderStateMixin {
  late final int _start;
  final Random _rnd = Random();

  @override
  void initState() {
    super.initState();
    _start = DateTime.now().millisecondsSinceEpoch;

    Future.delayed(const Duration(milliseconds: 2000), () {
      final elapsed = DateTime.now().millisecondsSinceEpoch - _start;
      final res =
          TestResult('UI Test', elapsed, 'Animation frames rendered (1500 rects)', true);
      if (mounted) Navigator.pop(context, res);
    });
  }

// ... inside _AnimatedPositionedSquareState

  @override
  void initState() {
    super.initState();

    _controller =
      AnimationController(vsync: this, duration: const Duration(milliseconds: 1000))
          ..repeat();

    _animX = TweenSequence([
      TweenSequenceItem(
          tween: Tween(begin: widget.startX, end: widget.startX + widget.dx),
          weight: 50),
      TweenSequenceItem(
          tween: Tween(begin: widget.startX + widget.dx, end: widget.startX),
          weight: 50),
    ]).animate(_controller);

    _animY = TweenSequence([
      TweenSequenceItem(
          tween: Tween(begin: widget.startY, end: widget.startY + widget.dy),
          weight: 50),
      TweenSequenceItem(
          tween: Tween(begin: widget.startY + widget.dy, end: widget.startY),
          weight: 50),
    ]).animate(_controller);
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: _controller,
      builder: (_, __) {
        return Positioned(
          left: _animX.value,
          top: _animY.value,
          child: Container(
            width: widget.size,
            height: widget.size,
            color: widget.color,
          ),
        );
      },
    );
  }
}
