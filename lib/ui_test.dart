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

    Future.delayed(const Duration(seconds: 5), () {
      final elapsed = DateTime.now().millisecondsSinceEpoch - _start;
      final res =
          TestResult('UI Test', elapsed, 'Animation frames rendered', true);
      if (mounted) Navigator.pop(context, res);
    });
  }

@override
Widget build(BuildContext context) {
  final screen = MediaQuery.of(context).size;
  final pixelRatio = MediaQuery.of(context).devicePixelRatio;
  final size = 50 / pixelRatio; // odpowiada 50px z Androida

  final squares = List.generate(1500, (i) {
    final x = _rnd.nextDouble() * (screen.width - size);
    final y = _rnd.nextDouble() * (screen.height - size);
    final color = Color.fromARGB(
        255, _rnd.nextInt(256), _rnd.nextInt(256), _rnd.nextInt(256));

    final dx = (_rnd.nextDouble() * 400 / pixelRatio) - (200 / pixelRatio);
    final dy = (_rnd.nextDouble() * 400 / pixelRatio) - (200 / pixelRatio);

    return AnimatedPositionedSquare(
      startX: x,
      startY: y,
      dx: dx,
      dy: dy,
      size: size,
      color: color,
    );
  });

  return Scaffold(
  body: Container(
    color: const Color.fromARGB(255, 255, 255, 255),
    child: Stack(children: squares),
  ),
);
}


}

class AnimatedPositionedSquare extends StatefulWidget {
  final double startX, startY, dx, dy, size;
  final Color color;

  const AnimatedPositionedSquare({
    super.key,
    required this.startX,
    required this.startY,
    required this.dx,
    required this.dy,
    required this.size,
    required this.color,
  });

  @override
  State<AnimatedPositionedSquare> createState() =>
      _AnimatedPositionedSquareState();
}

class _AnimatedPositionedSquareState extends State<AnimatedPositionedSquare>
    with SingleTickerProviderStateMixin {
  late AnimationController _controller;
  late Animation<double> _animX, _animY;

  @override
  void initState() {
    super.initState();

    _controller =
      AnimationController(vsync: this, duration: const Duration(seconds: 2))
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
