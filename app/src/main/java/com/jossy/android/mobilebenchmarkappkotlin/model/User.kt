package com.jossy.android.mobilebenchmarkappkotlin.model

import kotlinx.serialization.Serializable

@Serializable
data class User(
    var name: String,
    var surname: String,
    var age: Int,
    var active: Boolean,
)
