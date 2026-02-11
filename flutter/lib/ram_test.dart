import 'package:flutter/material.dart';
import 'models/test_result.dart';
import 'ram_test_impl.dart';

class RamTest extends StatelessWidget {
  const RamTest({super.key});

  @override
  Widget build(BuildContext context) {
    WidgetsBinding.instance.addPostFrameCallback((_) async {
      final res = await RAMTest.runBenchmark();

      Navigator.pop(context, res);
    });

    return const Scaffold(
      body: Center(
        child: Text(
          'RAM processing ...',
          style: TextStyle(fontSize: 18),
        ),
      ),
    );
  }
}

