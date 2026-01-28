package com.jossy.android.mobilebenchmarkappkotlin.service

import com.jossy.android.mobilebenchmarkappkotlin.model.Post
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.get
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

object ApiService {
    private val json = Json { ignoreUnknownKeys = true }

    private val client: HttpClient = HttpClient(Android) {
        install(ContentNegotiation) { json(json) }
        install(Logging) { level = LogLevel.INFO }
    }

    suspend fun fetchPosts(): List<Post> = client.get("https://jsonplaceholder.typicode.com/posts").body()
}
