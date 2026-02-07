package com.jossy.android.mobilebenchmarkappjava.activity;

import android.content.Intent;
import android.util.Log;
import androidx.annotation.NonNull;

import com.jossy.android.mobilebenchmarkappjava.R;
import com.jossy.android.mobilebenchmarkappjava.consts.Config;
import com.jossy.android.mobilebenchmarkappjava.data.Post;
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

public class ApiTestActivity extends BaseTestActivity {
    private static final String TAG = "ApiTestActivity";

    private ApiService apiService;

    @Override
    protected void initializeActivity() {
        setContentView(R.layout.activity_api_test);
        statusText = findViewById(R.id.apiStatus);
        setupRetrofit();
    }

    @Override
    protected void executeBenchmark() throws Exception {
        initializeCsvWriter();
        startBatchTest();
    }

    private void setupRetrofit() {
        HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor();
        loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.NONE);

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
        currentSampleIndex = 0;
        makeApiRequest();
    }

    private void makeApiRequest() {
        if (currentSampleIndex >= Config.samplesAmount) {
            finishBatch();
            return;
        }

        long start = System.currentTimeMillis();
        apiService.getPosts().enqueue(new ApiCallback(start, currentSampleIndex));
        currentSampleIndex++;
    }

    private class ApiCallback implements Callback<List<Post>> {
        private final long startTime;
        private final int iteration;

        ApiCallback(long startTime, int iteration) {
            this.startTime = startTime;
            this.iteration = iteration;
        }

        @Override
        public void onResponse(@NonNull Call<List<Post>> call, @NonNull Response<List<Post>> response) {
            boolean success = response.isSuccessful() && response.body() != null;
            recordResult(success, success ? "Success" : "Error: " + response.code());
        }

        @Override
        public void onFailure(@NonNull Call<List<Post>> call, @NonNull Throwable t) {
            recordResult(false, "Failure: " + t.getMessage());
        }

        private void recordResult(boolean success, String details) {
            long duration = System.currentTimeMillis() - startTime;

            try {
                TestResult result = new TestResult("API Test", duration, details, success);
                logTestResult(iteration, result);
                updateProgress(iteration + 1);
            } catch (Exception e) {
                Log.e(TAG, "Failed to record result: " + e.getMessage(), e);
            }

            makeApiRequest();
        }
    }

    private void finishBatch() {
        closeCsvWriter();
        long totalTime = System.currentTimeMillis() - suiteStartTime;
        TestResult result = new TestResult(
            "API Test",
            totalTime,
            "Batch completed: " + currentSampleIndex,
            true
        );
        runOnUiThread(() -> {
            Intent intent = new Intent();
            intent.putExtra(com.jossy.android.mobilebenchmarkappjava.BenchmarkApplication.RESULT, result);
            setResult(RESULT_OK, intent);
            finish();
        });
    }

    @Override
    protected String getProgressDisplayText(int currentIteration) {
        return "API Test: " + currentIteration + " / " + Config.samplesAmount;
    }

    @Override
    protected String getTestName() {
        return "API Test";
    }
}
