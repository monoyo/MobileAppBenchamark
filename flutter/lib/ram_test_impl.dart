import 'dart:convert';
import 'dart:math';
import 'package:flutter/services.dart' show rootBundle;
import 'models/test_result.dart';
import 'models/user.dart';
import '../consts/config.dart';

/// RAM benchmark suite testing memory allocation, manipulation, and management.
/// Implements patterns from Java RAMTest: sorting, filtering, serialization, and aggregation.
class RAMTest {
  /// Counters for name frequency analysis.
  static final Map<String, int> nameFrequency = {};
  static final Map<String, int> surnameFrequency = {};

  /// Loads user models from assets with proper error handling.
  static Future<List<User>> _loadUsersFromAssets(String assetPath) async {
    try {
      final jsonString = await rootBundle.loadString(assetPath);
      final List<dynamic> jsonList = jsonDecode(jsonString);
      
      return jsonList
          .whereType<Map<String, dynamic>>()
          .map(User.fromJson)
          .toList();
    } catch (e) {
      throw Exception('Failed to load users: $e');
    }
  }

  /// Executes comprehensive RAM benchmark with sorting, filtering, and aggregation.
  /// 
  /// Test operations:
  /// 1. List shuffling and concatenation
  /// 2. Sorting by name (similar to Java's comparator pattern)
  /// 3. Filtering by active status and age
  /// 4. Data transformation (toUpperCase)
  /// 5. Frequency counting (name and surname aggregation)
  static Future<TestResult> runBenchmark({int? runs}) async {
    final effectiveRuns = runs ?? Config.sampleCount;
    final stopwatch = Stopwatch()..start();
    
    try {
      final users = await _loadUsersFromAssets('assets/users.json');
      final accumulator = <User>[];
      final random = Random();
      
      // Clear frequency maps for fresh counting
      nameFrequency.clear();
      surnameFrequency.clear();

      // Execute multiple iterations of memory operations
      for (int iteration = 0; iteration < effectiveRuns; iteration++) {
        // 1. Shuffle and concatenate (memory allocation)
        final shuffled = List<User>.from(users)..shuffle(random);
        accumulator.addAll(shuffled);

        // 2. Sort by name (similar to Java comparator pattern)
        final sorted = List<User>.from(shuffled)
          ..sort((userA, userB) => userA.name.compareTo(userB.name));

        // 3. Filter and transform (map-reduce pattern like Java)
        final filtered = sorted
            .where((user) => user.active && user.age > 18)
            .map((user) => user.copyWith(name: user.name.toUpperCase()))
            .toList();

        // 3b. Serialization Cycle (Missing heavy load)
        // Matches RAMTest.java: processSerializationCycle
        final String jsonString = jsonEncode(filtered);
        final List<dynamic> decodedList = jsonDecode(jsonString);
        final List<User> deserialized = decodedList.map((e) => User.fromJson(e)).toList();

        // 4. Access and use filtered models
        if (deserialized.isNotEmpty) {
           final randomUser = deserialized[random.nextInt(deserialized.length)];
           randomUser.name.length; 
        }

        // 5. Aggregate name frequencies (similar to Java stream collection pattern)
        for (final user in users) {
          _updateNameFrequency(user.name, nameFrequency);
          _updateNameFrequency(user.surname, surnameFrequency);
        }
      }

      // Clear accumulator to free memory
      accumulator.clear();
      
      final elapsedMs = stopwatch.elapsedMilliseconds;
      
      return TestResult(
        'RAM Test',
        elapsedMs,
        'Completed $effectiveRuns iterations: ${nameFrequency.length} unique names, '
        '${surnameFrequency.length} unique surnames',
        true,
      );
    } catch (e) {
      return TestResult(
        'RAM Test',
        stopwatch.elapsedMilliseconds,
        'Error: $e',
        false,
      );
    }
  }

  /// Updates frequency counter for a given name part.
  /// Uses the Java pattern of Map.getOrDefault equivalent.
  static void _updateNameFrequency(String fullName, Map<String, int> frequencyMap) {
    final parts = fullName.split(' ');
    for (final part in parts) {
      if (part.isNotEmpty) {
        frequencyMap[part] = (frequencyMap[part] ?? 0) + 1;
      }
    }
  }

  /// Returns aggregated statistics about name frequencies.
  /// Useful for verification and reporting.
  static Map<String, dynamic> getStatistics() {
    int maxNameFreq = 0;
    String? mostCommonName;
    
    nameFrequency.forEach((name, freq) {
      if (freq > maxNameFreq) {
        maxNameFreq = freq;
        mostCommonName = name;
      }
    });

    return {
      'uniqueNames': nameFrequency.length,
      'uniqueSurnames': surnameFrequency.length,
      'mostCommonName': mostCommonName,
      'mostCommonNameFrequency': maxNameFreq,
    };
  }
}
