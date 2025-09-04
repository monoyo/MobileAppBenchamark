import 'dart:convert';
import 'dart:math';
import 'package:flutter/services.dart' show rootBundle;
import 'models/test_result.dart';
import 'models/user.dart';

class RAMTest {
  static const int RUNS = 1800;
  static final Map<String, int> nameCounter = {};
  static final Map<String, int> surnameCounter = {};

  static Future<List<User>> loadUsersFromAssets(String path) async {
    final data = await rootBundle.loadString(path);
    final List<dynamic> jsonList = jsonDecode(data);
    return jsonList.map((e) => User.fromJson(e)).toList();
  }

  static Future<TestResult> runBenchmark() async {
    final sw = Stopwatch()..start();
    final users = await RAMTest.loadUsersFromAssets('assets/users.json');
    final bigList = <User>[];
    final rand = Random();
    nameCounter.clear();
    surnameCounter.clear();

    for (int iteration = 0; iteration < RUNS; iteration++) {
      final shuffled = List<User>.from(users)..shuffle(rand);
      bigList.addAll(shuffled);

      final sorted = List<User>.from(shuffled)
        ..sort((a, b) => a.name.compareTo(b.name));

      final filtered = sorted
          .where((u) => u.active && u.age > 18)
          .map((u) => u.copyWith(name: u.name.toUpperCase()))
          .toList();

      if (filtered.isNotEmpty) {
        final randomUser = filtered[rand.nextInt(filtered.length)];
        final randomUserName = randomUser.name;
      }

      for (final user in users) {
        final parts = user.name.split(' ');
        if (parts.isNotEmpty) {
          final firstName = parts[0];
          nameCounter[firstName] = (nameCounter[firstName] ?? 0) + 1;
        }
        if (parts.length > 1) {
          final surname = parts[1];
          surnameCounter[surname] = (surnameCounter[surname] ?? 0) + 1;
        }
      }
    }

    bigList.clear();
    final elapsed = sw.elapsedMilliseconds;
    return TestResult(
      'RAM Test',
      elapsed,
      'RAM intensive operations completed',
      true,
    );
  }
}
