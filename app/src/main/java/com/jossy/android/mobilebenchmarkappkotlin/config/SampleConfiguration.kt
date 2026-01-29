package com.jossy.android.mobilebenchmarkappkotlin.config

/**
 * Konfiguracja scenariuszy testowych z adaptacyjnymi parametrami.
 * Optymalizacja pod platformę mobilną dla zbiorów 100 - 1M próbek.
 */
enum class SampleConfiguration(
    val sampleCount: Int,
    val bufferSize: Int,
    val cpuIterationsPerThread: Long,
    val displayName: String
) {
    SMALL(
        sampleCount = 100,
        bufferSize = 50,
        cpuIterationsPerThread = 500_000L,
        displayName = "100 samples"
    ),
    MEDIUM(
        sampleCount = 1_000,
        bufferSize = 200,
        cpuIterationsPerThread = 1_000_000L,
        displayName = "1K samples"
    ),
    LARGE(
        sampleCount = 10_000,
        bufferSize = 1_000,
        cpuIterationsPerThread = 2_000_000L,
        displayName = "10K samples"
    ),
    VERY_LARGE(
        sampleCount = 100_000,
        bufferSize = 5_000,
        cpuIterationsPerThread = 5_000_000L,
        displayName = "100K samples"
    ),
    EXTREME(
        sampleCount = 1_000_000,
        bufferSize = 10_000,
        cpuIterationsPerThread = 10_000_000L,
        displayName = "1M samples"
    );

    /**
     * Optymalny rozmiar bufora I/O w bajtach.
     */
    val optimalWriteBufferBytes: Int
        get() = when {
            sampleCount >= 100_000 -> 128 * 1024  // 128KB
            sampleCount >= 10_000 -> 64 * 1024   // 64KB
            else -> 16 * 1024                     // 16KB
        }

    /**
     * Opóźnienie między testami w ms (mniejsze dla dużych zbiorów).
     */
    val testDelayMs: Long
        get() = if (sampleCount >= 10_000) 100L else 500L

    companion object {
        fun fromOrdinal(ordinal: Int): SampleConfiguration =
            entries.getOrElse(ordinal) { SMALL }
    }
}
