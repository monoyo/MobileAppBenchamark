package com.jossy.android.mobilebenchmarkappkotlin

import com.jossy.android.mobilebenchmarkappkotlin.data.User
import kotlin.random.Random

object RAMTest {
    private const val RUNS = 95_000
    val nameCounter: MutableMap<String, Int> = mutableMapOf()
    val surnameCounter: MutableMap<String, Int> = mutableMapOf()

    fun runBenchmark(users: List<User>) {
        val bigList = mutableListOf<User>()
        nameCounter.clear()
        surnameCounter.clear()

        repeat(RUNS) { iteration ->
            val shuffled = users.shuffled(Random(iteration))
            bigList.addAll(shuffled)

            val sorted = shuffled.sortedBy { it.name }

            val filtered =
                sorted
                    .filter { it.active && it.age > 18 }
                    .map { it.copy(name = it.name.uppercase()) }

            if (filtered.isNotEmpty()) {
                val randomUser = filtered[Random.nextInt(filtered.size)]
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
