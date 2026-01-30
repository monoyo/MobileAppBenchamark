package com.jossy.android.mobilebenchmarkappjava.config;

/**
 * Konfiguracja scenariuszy testowych z predefiniowanymi wielkościami próbek.
 * 
 * Każdy scenariusz określa:
 * - sampleCount: liczba próbek (iteracji) do zebrania
 * - displayName: nazwa wyświetlana w UI
 * - bufferSize: rozmiar bufora przed zapisem na dysk (optymalizacja I/O)
 * - flushIntervalMs: interwał czasowy wymuszający flush (backup dla długich
 * iteracji)
 */
public enum SampleConfiguration {

    SMALL(100, "100 samples", 50, 5_000, 1_000), // 100 * 1000ms = 100s
    MEDIUM(1_000, "1K samples", 200, 10_000, 100), // 1000 * 100ms = 100s
    LARGE(10_000, "10K samples", 1_000, 15_000, 10), // 10000 * 10ms = 100s
    VERY_LARGE(100_000, "100K samples", 5_000, 30_000, 1);

    public final int sampleCount;
    public final String displayName;
    public final int bufferSize;
    public final long flushIntervalMs;
    public final int samplingIntervalMs;

    SampleConfiguration(int sampleCount, String displayName, int bufferSize, long flushIntervalMs,
            int samplingIntervalMs) {
        this.sampleCount = sampleCount;
        this.displayName = displayName;
        this.bufferSize = bufferSize;
        this.flushIntervalMs = flushIntervalMs;
        this.samplingIntervalMs = samplingIntervalMs;
    }

    /**
     * Zwraca konfigurację na podstawie indeksu (dla Spinner).
     */
    public static SampleConfiguration fromIndex(int index) {
        SampleConfiguration[] values = values();
        if (index >= 0 && index < values.length) {
            return values[index];
        }
        return SMALL;
    }

    /**
     * Zwraca tablicę nazw wyświetlanych (dla ArrayAdapter).
     */
    public static String[] getDisplayNames() {
        SampleConfiguration[] values = values();
        String[] names = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            names[i] = values[i].displayName;
        }
        return names;
    }
}
