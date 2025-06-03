package com.jossy.android.mobilebenchmarkappkotlin

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

data class User(
    val id: Int,
    val name: String,
    val age: Int,
    val active: Boolean,
)

object RAMTest {
    private val file = File("users.json")
    private val RUNS = 1000

    fun runBenchmark() {
        for (i in 0 until RUNS) {
            if (file.exists()) {
                val json = file.readText()
                val userListType = object : TypeToken<List<User>>() {}.type
                val users: List<User> = Gson().fromJson(json, userListType)
                users.forEach { it.name } // Accessing to simulate memory usage
            }
        }
    }
}
