package com.jossy.android.mobilebenchmarkappjava;

import android.app.Application;
import android.os.StrictMode;

import com.github.mikephil.charting.BuildConfig;

public class BenchmarkApplication extends Application {

    public static final String RESULT = "result";

    @Override
    public void onCreate() {
        super.onCreate();
        
        // Enable strict mode in debug builds
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .build());
            
            StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                    .detectLeakedSqlLiteObjects()
                    .detectLeakedClosableObjects()
                    .penaltyLog()
                    .build());
        }

        // Pre-initialize heavy components
        initializeComponents();
    }

    private void initializeComponents() {
        // Run initialization on background thread
        new Thread(() -> {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
            // Pre-warm the image loader cache
            com.bumptech.glide.Glide.get(this);
        }).start();
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        // Clear image caches when memory is low
        if (level >= TRIM_MEMORY_MODERATE) {
            com.bumptech.glide.Glide.get(this).clearMemory();
        }
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        // Clear all caches
        com.bumptech.glide.Glide.get(this).clearMemory();
    }
}
