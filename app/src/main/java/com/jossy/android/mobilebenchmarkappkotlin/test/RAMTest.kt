package com.jossy.android.mobilebenchmarkappkotlin.test

import com.jossy.android.mobilebenchmarkappkotlin.model.User
import com.jossy.android.mobilebenchmarkappkotlin.source.jsonData
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.random.Random

object RAMTest {
    private val nameCounter: MutableMap<String, Int> = mutableMapOf()
    private val surnameCounter: MutableMap<String, Int> = mutableMapOf()

    /**
     * Runs ONE sample cycle.
     */
    fun runBenchmarkCycle(seed: Int) {
        val bigList = mutableListOf<User>()
        nameCounter.clear()
        surnameCounter.clear()

        // 1. Deserializacja
        val users = Json.Default.decodeFromString<List<User>>(jsonData)

        // 2. Mieszanie
        val shuffled = users.shuffled(Random(seed))
        bigList.addAll(shuffled)

        // 3. Sortowanie
        val sorted = shuffled.sortedBy { it.name }

        // 4. Filtrowanie i Transformacja (Deep Copy via copy)
        val filtered = sorted
            .filter { it.active && it.age > 18 }
            .map { it.copy(name = it.name.uppercase()) }

        // 5. Serializacja
        val serialized = Json.Default.encodeToString(filtered)
        
        val deserialized: List<User> = Json.Default.decodeFromString(serialized)
        if (deserialized.isNotEmpty()) {
            val randomUser = deserialized[Random.Default.nextInt(deserialized.size)]
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
        
        bigList.clear()
    }
}