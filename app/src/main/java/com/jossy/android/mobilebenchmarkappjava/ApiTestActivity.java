package com.jossy.android.mobilebenchmarkappjava;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
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
    private PostAdapter adapter;
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
                    statusTextView.setText("Data fetched and parsed in " + totalTime + "ms");
                    Log.d("ApiTestActivity", "API test completed in " + totalTime + "ms");
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

    private static class PostAdapter extends RecyclerView.Adapter<PostViewHolder> {
        private final List<Post> posts = new ArrayList<>();

        @NonNull
        @Override
        public PostViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new PostViewHolder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_post, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull PostViewHolder holder, int position) {
            Post post = posts.get(position);
            holder.titleView.setText(post.getTitle());
            holder.bodyView.setText(post.getBody());
        }

        @Override
        public int getItemCount() {
            return posts.size();
        }
    }

    private static class PostViewHolder extends RecyclerView.ViewHolder {
        final TextView titleView;
        final TextView bodyView;

        PostViewHolder(@NonNull android.view.View itemView) {
            super(itemView);
            titleView = itemView.findViewById(R.id.postTitle);
            bodyView = itemView.findViewById(R.id.postBody);
        }
    }
}
