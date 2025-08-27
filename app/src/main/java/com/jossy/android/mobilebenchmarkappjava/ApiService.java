package com.jossy.android.mobilebenchmarkappjava;

import java.util.List;
import retrofit2.Call;
import retrofit2.http.GET;

public interface ApiService {
    @GET("posts")
    Call<List<Post>> getPosts();
}
