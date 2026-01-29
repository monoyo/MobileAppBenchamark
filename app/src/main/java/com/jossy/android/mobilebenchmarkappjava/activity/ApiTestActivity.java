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

    private int requestCount = 0;
    private int targetSamples = 0;
    private long suiteStartTime;
    private String csvPath;
    private com.jossy.android.mobilebenchmarkappjava.io.BufferedCsvWriter csvWriter;

    private ApiService apiService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_api_test);
        statusTextView = findViewById(R.id.apiStatus);

        targetSamples = getIntent().getIntExtra("iterations", 10);
        csvPath = getIntent().getStringExtra("csv_path");

        Log.i("ApiTestActivity", "Starting API Batch: " + targetSamples);
        statusTextView.setText("Initializing API Batch...");

        setupRetrofit();
        startBatchTest();
    }

    private void setupRetrofit() {
        HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor();
        loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.NONE); // Disable logging logic for speed in batch

        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(loggingInterceptor)
                .build();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://jsonplaceholder.typicode.com/")
                .addConverterFactory(GsonConverterFactory.create())
                .client(client)
                .build();

        apiService = retrofit.create(ApiService.class);
    }

    private void startBatchTest() {
        suiteStartTime = System.currentTimeMillis();

        try {
            csvWriter = new com.jossy.android.mobilebenchmarkappjava.io.BufferedCsvWriter(
                    new java.io.File(csvPath != null ? csvPath : getFilesDir() + "/temp_api.csv"), 1000, 64 * 1024);
            csvWriter.initialize();
        } catch (Exception e) {
            Log.e("ApiTestActivity", "Writer init failed", e);
            finish();
            return;
        }

        makeApiRequest();
    }

    private void makeApiRequest() {
        if (requestCount >= targetSamples) {
            finishBatch();
            return;
        }

        long start = System.currentTimeMillis();
        // Trigger generic GET request
        apiService.getPosts().enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<List<Post>> call, @NonNull Response<List<Post>> response) {
                boolean success = response.isSuccessful() && response.body() != null;
                recordResult(start, success, success ? "Success" : "Error: " + response.code());
            }

            @Override
            public void onFailure(@NonNull Call<List<Post>> call, @NonNull Throwable t) {
                recordResult(start, false, "Failure: " + t.getMessage());
            }
        });
    }

    private void recordResult(long startTime, boolean success, String details) {
        long duration = System.currentTimeMillis() - startTime;

        try {
            com.jossy.android.mobilebenchmarkappjava.data.TestResult tr = new com.jossy.android.mobilebenchmarkappjava.data.TestResult(
                    "API Test", duration, details, success);
            com.jossy.android.mobilebenchmarkappjava.data.TestEntry entry = new com.jossy.android.mobilebenchmarkappjava.data.TestEntry(
                    requestCount, tr, startTime, duration, System.currentTimeMillis() - suiteStartTime);

            if (csvWriter != null)
                csvWriter.write(entry);

        } catch (Exception e) {
            Log.e("ApiTestActivity", "Log failed", e);
        }

        requestCount++;

        if (requestCount % 10 == 0) {
            runOnUiThread(() -> statusTextView.setText("Requests: " + requestCount + "/" + targetSamples));
        }

        // Recursive call
        makeApiRequest();
    }

    private void finishBatch() {
        long totalTime = System.currentTimeMillis() - suiteStartTime;
        try {
            if (csvWriter != null) {
                csvWriter.flush();
                csvWriter.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        TestResult result = new TestResult("API Test", totalTime, "Batch completed: " + requestCount, true);
        Intent intent = new Intent();
        intent.putExtra(BenchmarkApplication.RESULT, result);
        setResult(RESULT_OK, intent);
        finish();
    }
}
