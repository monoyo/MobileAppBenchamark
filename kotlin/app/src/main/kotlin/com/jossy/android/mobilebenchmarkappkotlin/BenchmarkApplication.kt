package com.jossy.android.mobilebenchmarkappkotlin

import android.app.Application
import android.os.Process
import com.bumptech.glide.Glide

class BenchmarkApplication : Application() {

    companion object {
        const val RESULT = "result"
    }

    override fun onCreate() {
        super.onCreate()
        initializeComponents()
    }

    private fun initializeComponents() {
        Thread {
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
            Glide.get(this@BenchmarkApplication)
        }.start()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_MODERATE) {
            Glide.get(this).clearMemory()
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        Glide.get(this).clearMemory()
    }
}
