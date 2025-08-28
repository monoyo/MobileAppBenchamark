package com.jossy.android.mobilebenchmarkappkotlin

import android.app.Application
import android.content.pm.ApplicationInfo
import android.os.StrictMode
import com.bumptech.glide.Glide

class BenchmarkApplication : Application() {
    companion object {
        const val RESULT = "result"
    }

    override fun onCreate() {
        super.onCreate()

        val isDebuggable = (applicationInfo?.flags ?: 0) and ApplicationInfo.FLAG_DEBUGGABLE != 0

        if (isDebuggable) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .build()
            )

            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .detectLeakedSqlLiteObjects()
                    .detectLeakedClosableObjects()
                    .penaltyLog()
                    .build()
            )
        }

        Thread {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
            Glide.get(this)
        }.start()
    }
}
