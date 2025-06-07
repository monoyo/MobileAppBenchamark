package com.jossy.android.mobilebenchmarkappkotlin

import java.io.File

data class User(
    val id: Int,
    val name: String,
    val age: Int,
    val active: Boolean,
)

object RAMTest {
    private val file = File("users.json")
    private val RUNS = 95000
    val nameCounter = mutableMapOf<String, Int>()
    val surnameCounter = mutableMapOf<String, Int>()

    fun runBenchmark() {
        val bigList = mutableListOf<List<User>>()
        nameCounter.clear()
        surnameCounter.clear()
        for (i in 0 until RUNS) {
            if (file.exists()) {
                val json = file.readText()
                val userListType = object : com.google.gson.reflect.TypeToken<List<User>>() {}.type
                val users: List<User> =
                    com.google.gson
                        .Gson()
                        .fromJson(json, userListType)
                val shuffled = users.shuffled()
                bigList.add(shuffled)
                val sorted = shuffled.sortedBy { it.name }
                val filtered = sorted.filter { it.active && it.age > 18 }.map { it.copy(name = it.name.uppercase()) }
                val serialized =
                    com.google.gson
                        .Gson()
                        .toJson(filtered)
                val deserialized: List<User> =
                    com.google.gson
                        .Gson()
                        .fromJson(serialized, userListType)
                if (deserialized.isNotEmpty()) {
                    val randomUser = deserialized[(deserialized.indices).random()]
                    randomUser.name
                }
                // Liczenie imion i nazwisk
                for (user in users) {
                    val parts = user.name.split(" ")
                    if (parts.isNotEmpty()) {
                        val firstName = parts.first()
                        nameCounter[firstName] = nameCounter.getOrDefault(firstName, 0) + 1
                    }
                    if (parts.size > 1) {
                        val surname = parts.last()
                        surnameCounter[surname] = surnameCounter.getOrDefault(surname, 0) + 1
                    }
                }
            }
        }
        bigList.clear()
    }
}
