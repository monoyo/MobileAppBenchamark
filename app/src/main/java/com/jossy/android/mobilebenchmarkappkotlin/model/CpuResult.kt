package com.jossy.android.mobilebenchmarkappkotlin.model

data class CpuResult(
    val threads: Int,
    val durationMs: Long,
    val iterations: Long,
    val checksum: Double
)
