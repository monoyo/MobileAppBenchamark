package com.jossy.android.mobilebenchmarkappjava.service;

import com.jossy.android.mobilebenchmarkappjava.data.Post;

import java.util.List;
import retrofit2.Call;
import retrofit2.http.GET;

public interface ApiService {
    @GET("posts")
    Call<List<Post>> getPosts();
}
