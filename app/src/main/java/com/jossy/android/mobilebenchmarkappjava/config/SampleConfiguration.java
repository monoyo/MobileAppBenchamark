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

    SMALL(100, "100 próbek", 50, 5_000),
    MEDIUM(1_000, "1K próbek", 200, 10_000),
    LARGE(10_000, "10K próbek", 1_000, 15_000),
    VERY_LARGE(100_000, "100K próbek", 5_000, 30_000),
    EXTREME(1_000_000, "1M próbek", 10_000, 60_000);

    public final int sampleCount;
    public final String displayName;
    public final int bufferSize;
    public final long flushIntervalMs;

    SampleConfiguration(int sampleCount, String displayName, int bufferSize, long flushIntervalMs) {
        this.sampleCount = sampleCount;
        this.displayName = displayName;
        this.bufferSize = bufferSize;
        this.flushIntervalMs = flushIntervalMs;
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

    /**
     * Oblicza optymalny rozmiar bufora I/O dla zapisu plików.
     * Dla dużych zbiorów danych używamy większych buforów.
     */
    public int getOptimalWriteBufferBytes() {
        if (sampleCount >= 100_000) {
            return 128 * 1024; // 128KB dla bardzo dużych zbiorów
        } else if (sampleCount >= 10_000) {
            return 64 * 1024; // 64KB dla dużych zbiorów
        } else {
            return 16 * 1024; // 16KB dla małych zbiorów
        }
    }

    /**
     * Określa liczbę iteracji CPU per wątek dla testu CPU.
     * Skalowane proporcjonalnie do wielkości próbki.
     */
    public long getCpuIterationsPerThread() {
        return switch (this) {
            case SMALL -> 500_000L;
            case MEDIUM -> 1_000_000L;
            case LARGE -> 2_000_000L;
            case VERY_LARGE -> 5_000_000L;
            case EXTREME -> 10_000_000L;
        };
    }
}
