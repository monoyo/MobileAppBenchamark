package com.jossy.android.mobilebenchmarkappjava.data

data class CpuResult(
    val threads: Int,
    val durationMs: Long,
    val iterations: Long,
    val checksum: Double
)
