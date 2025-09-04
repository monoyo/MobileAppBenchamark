package com.jossy.android.mobilebenchmarkappjava;

import static android.os.Process.THREAD_PRIORITY_BACKGROUND;
import static android.os.Process.setThreadPriority;

import android.app.Application;

import com.bumptech.glide.Glide;


public class BenchmarkApplication extends Application {

    public static final String RESULT = "result";

    @Override
    public void onCreate() {
        super.onCreate();
        initializeComponents();
    }

    private void initializeComponents() {
        new Thread(() -> {
            setThreadPriority(THREAD_PRIORITY_BACKGROUND);
            Glide.get(this);
        }).start();
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level >= TRIM_MEMORY_MODERATE) {
            Glide.get(this).clearMemory();
        }
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        Glide.get(this).clearMemory();
    }
}
