package com.jossy.android.mobilebenchmarkappkotlin.utils

import android.util.Log
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileReader
import java.io.FileWriter

/**
 * Generates summary.csv with aggregated statistics from raw benchmark CSV files.
 */
object SummaryWriter {
    private const val TAG = "SummaryWriter"

    /**
     * Reads all CSV files in the given directory and writes a summary.csv with aggregated statistics.
     *
     * @param outputDir Directory containing per-test CSV files
     * @return true if summary was written successfully
     */
    fun writeSummary(outputDir: File): Boolean {
        try {
            val csvFiles = outputDir.listFiles { _, name -> 
                name.endsWith(".csv") && !name.equals("summary.csv", ignoreCase = true)
            } ?: return false

            if (csvFiles.isEmpty()) {
                Log.w(TAG, "No CSV files found in ${outputDir.absolutePath}")
                return false
            }

            val allStats = mutableListOf<StatisticsUtils.AggregatedStats>()

            for (csvFile in csvFiles) {
                val stats = parseAndAggregate(csvFile)
                if (stats != null) {
                    allStats.add(stats)
                }
            }

            if (allStats.isEmpty()) {
                Log.w(TAG, "No valid stats computed")
                return false
            }

            val summaryFile = File(outputDir, "summary.csv")
            BufferedWriter(FileWriter(summaryFile)).use { writer ->
                writer.write(StatisticsUtils.AggregatedStats.CSV_HEADER)
                writer.newLine()
                for (stats in allStats) {
                    writer.write(stats.toCsvLine())
                    writer.newLine()
                }
            }

            Log.i(TAG, "Summary written: ${summaryFile.absolutePath}")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write summary", e)
            return false
        }
    }

    /**
     * Parses a CSV file and computes aggregated statistics.
     */
    private fun parseAndAggregate(csvFile: File): StatisticsUtils.AggregatedStats? {
        try {
            val times = mutableListOf<Long>()
            var testName: String? = null
            var failures = 0

            BufferedReader(FileReader(csvFile)).use { reader ->
                // Skip header
                val header = reader.readLine() ?: return null

                reader.forEachLine { line ->
                    if (line.isBlank()) return@forEachLine

                    val parts = parseCsvLine(line)
                    if (parts.size >= 4) {
                        // New format: platform,test_name,iteration,execution_time_ms,...
                        if (testName == null) {
                            testName = parts[1]
                        }
                        val executionTimeMs = parts[3].toLongOrNull()
                        if (executionTimeMs != null && executionTimeMs >= 0) {
                            times.add(executionTimeMs)
                        } else {
                            failures++
                        }
                    } else if (parts.size >= 2) {
                        // Legacy format: iteration,executionTimeMs,...
                        if (testName == null) {
                            testName = csvFile.nameWithoutExtension.replace("_", " ")
                        }
                        val executionTimeMs = parts[1].toLongOrNull()
                        if (executionTimeMs != null && executionTimeMs >= 0) {
                            times.add(executionTimeMs)
                        } else {
                            failures++
                        }
                    }
                }
            }

            if (times.isEmpty()) {
                return null
            }

            return StatisticsUtils.aggregate(
                testName = testName ?: csvFile.nameWithoutExtension,
                times = times,
                failures = failures,
                platform = "kotlin"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse ${csvFile.name}", e)
            return null
        }
    }

    /**
     * Simple CSV line parser that handles quoted fields.
     */
    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        var current = StringBuilder()
        var inQuotes = false

        for (char in line) {
            when {
                char == '"' -> inQuotes = !inQuotes
                char == ',' && !inQuotes -> {
                    result.add(current.toString().trim())
                    current = StringBuilder()
                }
                else -> current.append(char)
            }
        }
        result.add(current.toString().trim())
        return result
    }
}
