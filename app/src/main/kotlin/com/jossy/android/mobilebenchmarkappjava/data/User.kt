package com.jossy.android.mobilebenchmarkappjava.data

data class User(
    var name: String? = null,
    var surname: String? = null,
    var age: Int = 0,
    var active: Boolean = false
) {
    constructor(user: User) : this(user.name, user.surname, user.age, user.active)
}
