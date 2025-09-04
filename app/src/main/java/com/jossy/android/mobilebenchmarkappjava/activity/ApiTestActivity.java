package com.jossy.android.mobilebenchmarkappjava.activity;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.jossy.android.mobilebenchmarkappjava.BenchmarkApplication;
import com.jossy.android.mobilebenchmarkappjava.data.Post;
import com.jossy.android.mobilebenchmarkappjava.R;
import com.jossy.android.mobilebenchmarkappjava.data.TestResult;
import com.jossy.android.mobilebenchmarkappjava.service.ApiService;

import java.util.List;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;

public class ApiTestActivity extends AppCompatActivity {
    private TextView statusTextView;
    private long startTime;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d("ApiTestActivity", "onCreate called");
        setContentView(R.layout.activity_api_test);
        statusTextView = findViewById(R.id.apiStatus);
        startApiTest();
    }

    private void startApiTest() {
        Log.d("ApiTestActivity", "Starting API test");
        startTime = System.currentTimeMillis();
        statusTextView.setText("Starting API request...");

        HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor();
        loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BASIC);

        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(loggingInterceptor)
                .build();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://jsonplaceholder.typicode.com/")
                .addConverterFactory(GsonConverterFactory.create())
                .client(client)
                .build();

        ApiService apiService = retrofit.create(ApiService.class);

        apiService.getPosts().enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<List<Post>> call, @NonNull Response<List<Post>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    long totalTime = System.currentTimeMillis() - startTime;
                    Log.d("ApiTestActivity", "API test completed in " + totalTime + "ms");
                    TestResult result = new TestResult("API Test", totalTime, "API request completed successfully", true);
                    Intent intent = new Intent();
                    intent.putExtra(BenchmarkApplication.RESULT, result);
                    setResult(RESULT_OK, intent);
                    finish();
                } else {
                    statusTextView.setText("Error: " + response.code());
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<Post>> call, @NonNull Throwable t) {
                statusTextView.setText("Error: " + t.getMessage());
            }
        });
    }
}
