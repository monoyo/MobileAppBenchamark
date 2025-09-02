import 'dart:math' as math;
import 'package:flutter/material.dart';
import 'models/test_result.dart';

class CPUTestPage extends StatefulWidget {
  const CPUTestPage({super.key});

  @override
  State<CPUTestPage> createState() => _CPUTestPageState();
}

class _CPUTestPageState extends State<CPUTestPage> {
  @override
  void initState() {
    super.initState();
    _runBenchmark();
  }

  Future<void> _runBenchmark() async {
    final sw = Stopwatch()..start();

    int countPrimes(int max) {
      int count = 0;
      for (int i = 2; i <= max; i++) {
        bool prime = true;
        int sqrtI = math.sqrt(i).floor();
        for (int j = 2; j <= sqrtI; j++) {
          if (i % j == 0) {
            prime = false;
            break;
          }
        }
        if (prime) count++;
      }
      return count;
    }

    final primes = countPrimes(1_000_000);

    final size = 150;
    final a = List.generate(
      size,
      (_) => List.generate(size, (_) => math.Random().nextDouble()),
    );
    final b = List.generate(
      size,
      (_) => List.generate(size, (_) => math.Random().nextDouble()),
    );
    final result = List.generate(size, (_) => List.filled(size, 0.0));
    for (int i = 0; i < size; i++) {
      for (int j = 0; j < size; j++) {
        double sum = 0.0;
        for (int k = 0; k < size; k++) {
          sum += a[i][k] * b[k][j];
        }
        result[i][j] = sum;
      }
    }

    String fibonacciBig(int n) {
      if (n <= 1) return n.toString();
      List<int> digits = [0, 1]; // reprezentacja liczby w odwrotnej kolejności
      for (int i = 2; i <= n; i++) {
        int carry = 0;
        for (int j = 0; j < digits.length; j++) {
          int prod = digits[j] + carry;
          digits[j] = prod % 10;
          carry = prod ~/ 10;
        }
        while (carry > 0) {
          digits.add(carry % 10);
          carry ~/= 10;
        }
      }
      return digits.reversed.join();
    }

    final fib = fibonacciBig(200);

    double heavyMathOps(int iterations) {
      double res = 0.0;
      final rand = math.Random();
      for (int i = 1; i <= iterations; i++) {
        res += math.sqrt(i.toDouble()) * math.pow(i.toDouble(), 1.5) / (rand.nextDouble() + 1);
      }
      return res;
    }

    final mathOps = heavyMathOps(500_000);

    final arr = List.generate(2_000_000, (_) => math.Random().nextDouble());
    arr.sort();

    double logSum = 0.0;
    for (int i = 1; i <= 2_000_000; i++) {
      logSum += math.log(i.toDouble()) * math.pow(i.toDouble(), 1.2);
    }

    sw.stop();
    final elapsed = sw.elapsedMilliseconds;

    final resultObj = TestResult(
      'CPU Test',
      elapsed,
      "",
      true,
    );

    if (mounted) {
      Navigator.pop(context, resultObj);
    }
  }

  @override
  Widget build(BuildContext context) {
    return const Scaffold(
      body: Center(
        child: Text(
          'Running CPU Test...',
          style: TextStyle(fontSize: 18),
        ),
      ),
    );
  }
}
