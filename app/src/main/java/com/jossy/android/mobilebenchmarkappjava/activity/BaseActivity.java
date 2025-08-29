package com.jossy.android.mobilebenchmarkappjava.activity;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

public abstract class BaseActivity extends AppCompatActivity {
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        overridePendingTransition(0, 0);
        if (getWindow() != null) {
            getWindow().setWindowAnimations(0);
            getWindow().setFlags(
                android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
            );
            getWindow().setFlags(
                android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            );
        }
        
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_DISPLAY);
    }

    @Override
    public void startActivity(Intent intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
        super.startActivity(intent);
        overridePendingTransition(0, 0);
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(0, 0);
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        overridePendingTransition(0, 0);
    }

    @Override
    protected void onPause() {
        super.onPause();
        overridePendingTransition(0, 0);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (getWindow() != null) {
            getWindow().setWindowAnimations(0);
        }
    }
}
