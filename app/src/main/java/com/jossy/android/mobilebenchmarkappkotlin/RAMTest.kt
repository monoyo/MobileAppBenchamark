package com.jossy.android.mobilebenchmarkappkotlin

import com.jossy.android.mobilebenchmarkappkotlin.data.User
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.random.Random

object RAMTest {
    private const val RUNS = 1800
    private val nameCounter: MutableMap<String, Int> = mutableMapOf()
    private val surnameCounter: MutableMap<String, Int> = mutableMapOf()

    fun runBenchmark() {
        val bigList = mutableListOf<User>()
        nameCounter.clear()
        surnameCounter.clear()

        val users = Json.decodeFromString<List<User>>(jsonData)

        repeat(RUNS) {
            val shuffled = users.shuffled(Random(it))
            bigList.addAll(shuffled)

            val sorted = shuffled.sortedBy { it.name }

            val filtered =
                sorted
                    .filter { it.active && it.age > 18 }
                    .map { it.copy(name = it.name.uppercase()) }

            val serialized = Json.encodeToString(filtered)
            val deserialized: List<User> = Json.decodeFromString(serialized)

            if (deserialized.isNotEmpty()) {
                val randomUser = deserialized[Random.nextInt(deserialized.size)]
                val randomUserName = randomUser.name
            }

            users.forEach { user ->
                val parts = user.name.split(" ")
                if (parts.isNotEmpty()) {
                    val firstName = parts[0]
                    nameCounter[firstName] = nameCounter.getOrDefault(firstName, 0) + 1
                }
                if (parts.size > 1) {
                    val surname = parts[1]
                    surnameCounter[surname] = surnameCounter.getOrDefault(surname, 0) + 1
                }
            }
        }

        bigList.clear()
    }
}
