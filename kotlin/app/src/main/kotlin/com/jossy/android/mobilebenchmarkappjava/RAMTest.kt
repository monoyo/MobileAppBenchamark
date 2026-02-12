package com.jossy.android.mobilebenchmarkappjava

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.jossy.android.mobilebenchmarkappjava.data.User
import java.util.Locale
import kotlin.random.Random

object RAMTest {
    private const val RUNS = 1800
    private val nameCounter = mutableMapOf<String, Int>()
    private val surnameCounter = mutableMapOf<String, Int>()
    private val gson = Gson()
    private val userListType = object : TypeToken<List<User>>() {}.type
    private var jsonData = Users().list

    fun runBenchmark(runs: Int = RUNS) {
        val bigList = mutableListOf<User>()
        val users = parseUsers()

        repeat(runs) {
            processSingleRun(users, bigList)
        }

        bigList.clear()
    }

    private fun parseUsers(): List<User> =
        gson.fromJson(jsonData, userListType) ?: emptyList()

    private fun processSingleRun(users: List<User>, bigList: MutableList<User>) {
        val shuffled = shuffleUsers(users)
        bigList.addAll(shuffled)

        val filtered = filterAndProcessUsers(shuffled)
        processSerializationCycle(filtered)

        updateNameCounters(users)
    }

    private fun shuffleUsers(users: List<User>): List<User> =
        users.toMutableList().apply {
            shuffle(Random(System.nanoTime()))
        }

    private fun filterAndProcessUsers(shuffled: List<User>): List<User> =
        shuffled
            .sortedBy { it.name }
            .filter { it.active && it.age > 18 }
            .map { user ->
                User(
                    name = user.name.uppercase(Locale.ROOT),
                    surname = user.surname,
                    age = user.age,
                    active = user.active
                )
            }

    private fun processSerializationCycle(filtered: List<User>) {
        val serialized = gson.toJson(filtered)
        val deserialized: List<User>? = gson.fromJson(serialized, userListType)

        deserialized?.randomOrNull()?.name?.let { /* prevent optimization */ }
    }

    private fun updateNameCounters(users: List<User>) {
        nameCounter.clear()
        surnameCounter.clear()

        users.forEach { user ->
            val parts = user.name.split(" ")
            if (parts.isNotEmpty() && parts[0].isNotEmpty()) {
                nameCounter[parts[0]] = nameCounter.getOrDefault(parts[0], 0) + 1
            }
            if (parts.size > 1 && parts[1].isNotEmpty()) {
                surnameCounter[parts[1]] = surnameCounter.getOrDefault(parts[1], 0) + 1
            }
        }
    }
}
