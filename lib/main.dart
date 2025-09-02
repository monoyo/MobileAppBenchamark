import 'dart:async';
import 'dart:math' as math;

import 'package:flutter/material.dart';

import 'benchmark_suite.dart';

void main() {
  runApp(const BenchmarkApp());
}

class BenchmarkApp extends StatelessWidget {
  const BenchmarkApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Mobile Benchmark',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(primarySwatch: Colors.blue),
      home: const HomePage(),
    );
  }
}

class HomePage extends StatefulWidget {
  const HomePage({super.key});

  @override
  State<HomePage> createState() => _HomePageState();
}

class _HomePageState extends State<HomePage>
    with SingleTickerProviderStateMixin {
  late final int _startTime;
  int? _launchMs;

  @override
  void initState() {
    super.initState();
    _startTime = DateTime.now().millisecondsSinceEpoch;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      final elapsed = DateTime.now().millisecondsSinceEpoch - _startTime;
      setState(() => _launchMs = elapsed);
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('')),
      body: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              'Welcome to the Mobile Benchmark App - Flutter Edition!',
              style: TextStyle(
                fontSize: 24,
                fontWeight: FontWeight.bold,
                color: Colors.black
              ),
            ),
            Container(
                padding: EdgeInsets.only(left: 25, bottom: 24, top: 24),
                child:
                  Center(child:  Text('App Launch Time: ${_launchMs != null ? '${_launchMs}ms' : 'calculating...'}'),)
            ),
            Text("Next step: Click the button below to start the benchmark."),
            Center(child: ElevatedButton(
              onPressed: () => Navigator.push(context, MaterialPageRoute(builder: (_) => const BenchmarkSuitePage())),
              style: ElevatedButton.styleFrom(
                  padding: const EdgeInsets.symmetric(horizontal: 100, vertical: 8),
                  textStyle: const TextStyle(fontSize: 12, fontWeight: FontWeight.bold),
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(24)),
                  backgroundColor: const Color.fromARGB(255, 76, 175, 80),
                  foregroundColor: Colors.white,
                  shadowColor: Colors.black54,
                ),
              child: const Text('Run All Tests'),
            )),
            const SizedBox(height: 12),
          ],
        ),
      ),
    );
  }
}
