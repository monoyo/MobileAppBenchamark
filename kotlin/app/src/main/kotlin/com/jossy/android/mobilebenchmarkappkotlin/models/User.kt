package com.jossy.android.mobilebenchmarkappkotlin.data

data class User(
    var name: String = "",
    var surname: String = "",
    var age: Int = 0,
    var active: Boolean = false
) {
    constructor(user: User) : this(user.name, user.surname, user.age, user.active)
}
