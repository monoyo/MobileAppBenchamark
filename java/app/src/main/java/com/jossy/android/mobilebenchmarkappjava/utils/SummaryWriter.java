package com.jossy.android.mobilebenchmarkappjava.utils;

import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates summary.csv with aggregated statistics from raw benchmark CSV files.
 */
public final class SummaryWriter {
    private static final String TAG = "SummaryWriter";

    private SummaryWriter() {}

    /**
     * Reads all CSV files in the given directory and writes a summary.csv with aggregated statistics.
     *
     * @param outputDir Directory containing per-test CSV files
     * @return true if summary was written successfully
     */
    public static boolean writeSummary(File outputDir) {
        try {
            File[] csvFiles = outputDir.listFiles((dir, name) -> 
                name.endsWith(".csv") && !name.equalsIgnoreCase("summary.csv")
            );

            if (csvFiles == null || csvFiles.length == 0) {
                Log.w(TAG, "No CSV files found in " + outputDir.getAbsolutePath());
                return false;
            }

            List<StatisticsUtils.AggregatedStats> allStats = new ArrayList<>();

            for (File csvFile : csvFiles) {
                StatisticsUtils.AggregatedStats stats = parseAndAggregate(csvFile);
                if (stats != null) {
                    allStats.add(stats);
                }
            }

            if (allStats.isEmpty()) {
                Log.w(TAG, "No valid stats computed");
                return false;
            }

            File summaryFile = new File(outputDir, "summary.csv");
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(summaryFile))) {
                writer.write(StatisticsUtils.AggregatedStats.CSV_HEADER);
                writer.newLine();
                for (StatisticsUtils.AggregatedStats stats : allStats) {
                    writer.write(stats.toCsvLine());
                    writer.newLine();
                }
            }

            Log.i(TAG, "Summary written: " + summaryFile.getAbsolutePath());
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Failed to write summary", e);
            return false;
        }
    }

    /**
     * Parses a CSV file and computes aggregated statistics.
     */
    private static StatisticsUtils.AggregatedStats parseAndAggregate(File csvFile) {
        try {
            List<Long> times = new ArrayList<>();
            String testName = null;
            int failures = 0;

            try (BufferedReader reader = new BufferedReader(new FileReader(csvFile))) {
                // Skip header
                String header = reader.readLine();
                if (header == null) return null;

                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) continue;

                    String[] parts = parseCsvLine(line);
                    if (parts.length >= 4) {
                        // New format: platform,test_name,iteration,execution_time_ms,...
                        if (testName == null) {
                            testName = parts[1];
                        }
                        try {
                            long executionTime = Long.parseLong(parts[3]);
                            if (executionTime >= 0) {
                                times.add(executionTime);
                            } else {
                                failures++;
                            }
                        } catch (NumberFormatException e) {
                            failures++;
                        }
                    } else if (parts.length >= 2) {
                        // Legacy format: iteration,executionTimeMs,...
                        if (testName == null) {
                            testName = csvFile.getName().replace(".csv", "").replace("_", " ");
                        }
                        try {
                            long executionTime = Long.parseLong(parts[1]);
                            if (executionTime >= 0) {
                                times.add(executionTime);
                            } else {
                                failures++;
                            }
                        } catch (NumberFormatException e) {
                            failures++;
                        }
                    }
                }
            }

            if (times.isEmpty()) {
                return null;
            }

            return StatisticsUtils.aggregate(
                testName != null ? testName : csvFile.getName().replace(".csv", ""),
                times,
                failures,
                "java"
            );
        } catch (IOException e) {
            Log.e(TAG, "Failed to parse " + csvFile.getName(), e);
            return null;
        }
    }

    /**
     * Simple CSV line parser that handles quoted fields.
     */
    private static String[] parseCsvLine(String line) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (char c : line.toCharArray()) {
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                result.add(current.toString().trim());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        result.add(current.toString().trim());
        return result.toArray(new String[0]);
    }
}
