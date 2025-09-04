package com.jossy.android.mobilebenchmarkappkotlin

import android.app.Application
import android.os.Process.setThreadPriority
import coil.Coil

class BenchmarkApplication : Application() {
    companion object {
        const val RESULT = "result"
    }

    override fun onCreate() {
        super.onCreate()
        Thread {
            setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
            Coil.imageLoader(this)
        }.start()
    }
}
