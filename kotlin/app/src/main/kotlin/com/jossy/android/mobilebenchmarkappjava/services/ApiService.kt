package com.jossy.android.mobilebenchmarkappjava.service

import com.jossy.android.mobilebenchmarkappjava.data.Post
import retrofit2.Call
import retrofit2.http.GET

interface ApiService {
    @GET("posts")
    fun getPosts(): Call<List<Post>>
}
