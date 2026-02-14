package com.jossy.android.mobilebenchmarkappkotlin.data

data class CpuResult(
    val threads: Int,
    val durationMs: Long,
    val iterations: Long,
    val checksum: Double
)
