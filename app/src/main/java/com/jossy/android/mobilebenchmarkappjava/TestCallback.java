package com.jossy.android.mobilebenchmarkappjava;

import com.jossy.android.mobilebenchmarkappjava.data.TestResult;

public interface TestCallback {
    void onAllTestsCompleted();
    void onTestStarted(String testName);}
