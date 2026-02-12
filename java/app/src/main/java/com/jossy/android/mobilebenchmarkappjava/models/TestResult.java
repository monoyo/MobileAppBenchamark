package com.jossy.android.mobilebenchmarkappjava.data;

import java.io.Serializable;

public class TestResult implements Serializable {
    private String testName;
    private long executionTimeMs;
    private String details;
    private boolean success;

    public TestResult(String testName, long executionTimeMs, String details, boolean success) {
        this.testName = testName;
        this.executionTimeMs = executionTimeMs;
        this.details = details;
        this.success = success;
    }

    public String getTestName() {
        return testName;
    }

    public long getExecutionTimeMs() {
        return executionTimeMs;
    }

    public String getDetails() {
        return details;
    }

    public boolean isSuccess() {
        return success;
    }
}
