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
        val bigList = mutableListOf<List<User>>()
        for (i in 0 until RUNS) {
            if (file.exists()) {
                val json = file.readText()
                val userListType = object : TypeToken<List<User>>() {}.type
                val users: List<User> = Gson().fromJson(json, userListType)
                val shuffled = users.shuffled()
                bigList.add(shuffled)
                val sorted = shuffled.sortedBy { it.name }
                val filtered = sorted.filter { it.active && it.age > 18 }.map { it.copy(name = it.name.uppercase()) }
                val serialized = Gson().toJson(filtered)
                val deserialized: List<User> = Gson().fromJson(serialized, userListType)
                if (deserialized.isNotEmpty()) {
                    val randomUser = deserialized[(deserialized.indices).random()]
                    randomUser.name
                }
            }
        }
        Thread.sleep(2000)
        bigList.clear()
    }
}
