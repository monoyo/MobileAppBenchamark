import 'dart:io';
import 'statistics_utils.dart';

/// Generates summary.csv with aggregated statistics from raw benchmark CSV files.
class SummaryWriter {
  
  /// Reads all CSV files in the given directory and writes a summary.csv with aggregated statistics.
  static Future<bool> writeSummary(Directory outputDir) async {
    try {
      final files = await outputDir.list().toList();
      final csvFiles = files
          .whereType<File>()
          .where((f) => f.path.endsWith('.csv') && !f.path.endsWith('summary.csv'))
          .toList();

      if (csvFiles.isEmpty) {
        print('SummaryWriter: No CSV files found in ${outputDir.path}');
        return false;
      }

      final allStats = <AggregatedStats>[];

      for (final csvFile in csvFiles) {
        final stats = await _parseAndAggregate(csvFile);
        if (stats != null) {
          allStats.add(stats);
        }
      }

      if (allStats.isEmpty) {
        print('SummaryWriter: No valid stats computed');
        return false;
      }

      final summaryFile = File('${outputDir.path}/summary.csv');
      final sink = summaryFile.openWrite();
      sink.writeln(AggregatedStats.csvHeader);
      for (final stats in allStats) {
        sink.writeln(stats.toCsvLine());
      }
      await sink.close();

      print('SummaryWriter: Summary written: ${summaryFile.path}');
      return true;
    } catch (e) {
      print('SummaryWriter: Failed to write summary: $e');
      return false;
    }
  }

  /// Parses a CSV file and computes aggregated statistics.
  static Future<AggregatedStats?> _parseAndAggregate(File csvFile) async {
    try {
      final lines = await csvFile.readAsLines();
      if (lines.isEmpty) return null;

      // Skip header
      final dataLines = lines.skip(1).where((l) => l.trim().isNotEmpty).toList();
      
      final times = <int>[];
      String? testName;
      int failures = 0;

      for (final line in dataLines) {
        final parts = _parseCsvLine(line);
        if (parts.length >= 4) {
          // New format: platform,test_name,iteration,execution_time_ms,...
          testName ??= parts[1];
          final executionTime = int.tryParse(parts[3]);
          if (executionTime != null && executionTime >= 0) {
            times.add(executionTime);
          } else {
            failures++;
          }
        } else if (parts.length >= 2) {
          // Legacy format: iteration,executionTimeMs,...
          testName ??= csvFile.path.split('/').last.replaceAll('.csv', '').replaceAll('_', ' ');
          final executionTime = int.tryParse(parts[1]);
          if (executionTime != null && executionTime >= 0) {
            times.add(executionTime);
          } else {
            failures++;
          }
        }
      }

      if (times.isEmpty) {
        return null;
      }

      return StatisticsUtils.aggregate(
        testName ?? csvFile.path.split('/').last.replaceAll('.csv', ''),
        times,
        failures: failures,
        platform: 'flutter',
      );
    } catch (e) {
      print('SummaryWriter: Failed to parse ${csvFile.path}: $e');
      return null;
    }
  }

  /// Simple CSV line parser that handles quoted fields.
  static List<String> _parseCsvLine(String line) {
    final result = <String>[];
    final current = StringBuffer();
    bool inQuotes = false;

    for (final char in line.runes) {
      final c = String.fromCharCode(char);
      if (c == '"') {
        inQuotes = !inQuotes;
      } else if (c == ',' && !inQuotes) {
        result.add(current.toString().trim());
        current.clear();
      } else {
        current.write(c);
      }
    }
    result.add(current.toString().trim());
    return result;
  }
}
