package com.jossy.android.mobilebenchmarkappkotlin.config

/**
 * Konfiguracja scenariuszy testowych z predefiniowanymi wielkościami próbek.
 *
 * Każdy scenariusz określa:
 * - sampleCount: liczba próbek (iteracji) do zebrania
 * - displayName: nazwa wyświetlana w UI
 * - bufferSize: rozmiar bufora przed zapisem na dysk (optymalizacja I/O)
 * - flushIntervalMs: interwał czasowy wymuszający flush (backup dla długich iteracji)
 * - samplingIntervalMs: interwał próbkowania metryk
 * - cpuIterationsPerThread: liczba operacji CPU na próbkę
 * - testDelayMs: opóźnienie między kolejnymi testami w suite
 */
enum class SampleConfiguration(
    val sampleCount: Int,
    val displayName: String,
    val bufferSize: Int,
    val flushIntervalMs: Long,
    val samplingIntervalMs: Int,
    val cpuIterationsPerThread: Long,
    val testDelayMs: Long
) {
    SMALL(100, "100 samples", 50, 5_000, 1_000, 100_000L, 500L),
    MEDIUM(1_000, "1K samples", 200, 10_000, 100, 1_000_000L, 1_000L),
    LARGE(10_000, "10K samples", 1_000, 15_000, 10, 5_000_000L, 2_000L),
    VERY_LARGE(100_000, "100K samples", 5_000, 30_000, 1, 10_000_000L, 5_000L);

    companion object {
        fun fromOrdinal(ordinal: Int): SampleConfiguration =
            entries.getOrElse(ordinal) { SMALL }

        fun getDisplayNames(): Array<String> =
            entries.map { it.displayName }.toTypedArray()
    }
}
