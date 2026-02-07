import 'package:flutter/material.dart';
import 'benchmark_suite.dart';

void main() {
  final startMs = DateTime.now().millisecondsSinceEpoch;
  runApp(BenchmarkApp(appStartEpochMs: startMs));
}

class BenchmarkApp extends StatelessWidget {
  final int appStartEpochMs;
  const BenchmarkApp({super.key, required this.appStartEpochMs});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Mobile Benchmark',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(primarySwatch: Colors.blue),
      home: _LaunchTimeResolver(appStartEpochMs: appStartEpochMs),
    );
  }
}

class _LaunchTimeResolver extends StatefulWidget {
  final int appStartEpochMs;
  const _LaunchTimeResolver({required this.appStartEpochMs});
  @override
  State<_LaunchTimeResolver> createState() => _LaunchTimeResolverState();
}

class _LaunchTimeResolverState extends State<_LaunchTimeResolver> {
  int? _launchMs;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      final diff = DateTime.now().millisecondsSinceEpoch - widget.appStartEpochMs;
      if (mounted) setState(() => _launchMs = diff);
    });
  }

  @override
  Widget build(BuildContext context) {
    if (_launchMs == null) {
      return const Scaffold(body: Center(child: Text('Inicjalizacja...')));
    }
    return BenchmarkSuitePage(appLaunchMs: _launchMs!);
  }
}
