package com.jossy.android.mobilebenchmarkappkotlin.extensions

import android.app.Activity.RESULT_OK
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import com.jossy.android.mobilebenchmarkappkotlin.BenchmarkApplication
import com.jossy.android.mobilebenchmarkappkotlin.model.TestResult

fun AppCompatActivity.onEnd(result: TestResult) {
    val resultIntent = Intent()
    resultIntent.putExtra(BenchmarkApplication.RESULT, result)
    setResult(RESULT_OK, intent)
    finish()
}