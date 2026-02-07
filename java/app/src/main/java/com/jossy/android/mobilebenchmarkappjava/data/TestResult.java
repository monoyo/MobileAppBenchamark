package com.jossy.android.mobilebenchmarkappjava.data;

import java.io.Serializable;

public class TestResult implements Serializable {
    private String testName;
    private long executionTime;
    private String details;
    private boolean success;

    public TestResult(String testName, long executionTime, String details, boolean success) {
        this.testName = testName;
        this.executionTime = executionTime;
        this.details = details;
        this.success = success;
    }

    public String getTestName() {
        return testName;
    }

    public long getExecutionTime() {
        return executionTime;
    }

    public String getDetails() {
        return details;
    }

    public boolean isSuccess() {
        return success;
    }
}
