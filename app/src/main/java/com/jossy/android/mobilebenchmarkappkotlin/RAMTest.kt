package com.jossy.android.mobilebenchmarkappkotlin

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.lang.reflect.Type
import java.util.ArrayList
import java.util.HashMap
import java.util.Random

object RAMTest {
    private val file = File("users.json")
    private const val RUNS = 95000
    val nameCounter: MutableMap<String, Int> = HashMap()
    val surnameCounter: MutableMap<String, Int> = HashMap()

    fun runBenchmark() {
        val bigList: MutableList<User> = ArrayList()
        nameCounter.clear()
        surnameCounter.clear()
        for (i in 0 until RUNS) {
            if (file.exists()) {
                val json = file.toString()
                val userListType: Type = object : TypeToken<List<User>>() {}.type
                val users: List<User> = Gson().fromJson(json, userListType)
                val shuffled = ArrayList(users)
                java.util.Collections.shuffle(shuffled)
                bigList.addAll(shuffled)
                shuffled.sortWith(java.util.Comparator { u1, u2 -> u1.name.compareTo(u2.name) })
                val filtered: MutableList<User> = ArrayList()
                for (user in shuffled) {
                    if (user.active && user.age > 18) {
                        val copy = User(user)
                        copy.name = user.name.uppercase()
                        filtered.add(copy)
                    }
                }
                val serialized = Gson().toJson(filtered)
                val deserialized: List<User> = Gson().fromJson(serialized, userListType)
                if (deserialized.isNotEmpty()) {
                    val rand = Random()
                    val randomUser = deserialized[rand.nextInt(deserialized.size)]
                    val randomUserName = randomUser.name
                }
                for (user in users) {
                    val parts = user.name.split(" ")
                    if (parts.isNotEmpty()) {
                        val firstName = parts[0]
                        val count = if (nameCounter.containsKey(firstName)) nameCounter[firstName]!! else 0
                        nameCounter[firstName] = count + 1
                    }
                    if (parts.size > 1) {
                        val surname = parts[1]
                        val count = if (surnameCounter.containsKey(surname)) surnameCounter[surname]!! else 0
                        surnameCounter[surname] = count + 1
                    }
                }
            }
        }
        bigList.clear()
    }
}

data class User(
    var name: String,
    var surname: String,
    var age: Int,
    var active: Boolean
) {
    constructor(user: User) : this(user.name, user.surname, user.age, user.active)
}
