package com.jossy.android.mobilebenchmarkappjava;

public interface TestCallback {
    void onTestCompleted(TestResult result);
    void onAllTestsCompleted();
    void onTestStarted(String testName);
    void onError(String testName, String error);
}
