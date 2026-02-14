package com.jossy.android.mobilebenchmarkappkotlin.service

import com.jossy.android.mobilebenchmarkappkotlin.data.Post
import retrofit2.Call
import retrofit2.http.GET

interface ApiService {
    @GET("posts")
    fun getPosts(): Call<List<Post>>
}
