package com.jossy.android.mobilebenchmarkappjava.data;

/**
 * Reprezentuje pojedynczy wpis wynikowy testu z pełnymi danymi czasowymi.
 * 
 * Rozszerzony o monitorowanie interwałów dla analizy wydajności przy dużych
 * zbiorach danych.
 */
public class TestEntry {
    public final int iteration;
    public final TestResult result;
    public final long intervalStartMs; // Timestamp rozpoczęcia tej iteracji
    public final long intervalDurationMs; // Czas trwania tej iteracji (ms)
    public final long cumulativeTimeMs; // Łączny czas od początku testu (ms)

    /**
     * Konstruktor pełny z danymi czasowymi.
     */
    public TestEntry(int iteration, TestResult result, long intervalStartMs,
            long intervalDurationMs, long cumulativeTimeMs) {
        this.iteration = iteration;
        this.result = result;
        this.intervalStartMs = intervalStartMs;
        this.intervalDurationMs = intervalDurationMs;
        this.cumulativeTimeMs = cumulativeTimeMs;
    }

    /**
     * Konstruktor kompatybilny wstecz (dla istniejącego kodu).
     */
    public TestEntry(int iteration, TestResult result) {
        this(iteration, result, System.currentTimeMillis(),
                result.getExecutionTimeMs(), result.getExecutionTimeMs());
    }

    @Override
    public String toString() {
        return "TestEntry{" +
                "iteration=" + iteration +
                ", executionTime=" + result.getExecutionTimeMs() +
                ", intervalDuration=" + intervalDurationMs +
                ", cumulative=" + cumulativeTimeMs +
                '}';
    }
}