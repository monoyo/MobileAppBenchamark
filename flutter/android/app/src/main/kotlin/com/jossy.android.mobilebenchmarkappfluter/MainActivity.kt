package com.jossy.android.mobilebenchmarkappflutter

import android.os.Build
import android.os.Bundle
import io.flutter.embedding.android.FlutterActivity

class MainActivity : FlutterActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onResume() {
        super.onResume()
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val window = window
                val display = windowManager.defaultDisplay
                val supportedModes = display.supportedModes
                val currentMode = display.mode

                // Filter for modes with same resolution as current, then find max refresh rate
                val bestMode = supportedModes.filter { 
                    it.physicalWidth == currentMode.physicalWidth && 
                    it.physicalHeight == currentMode.physicalHeight 
                }.maxByOrNull { it.refreshRate }

                if (bestMode != null && bestMode.refreshRate > currentMode.refreshRate) {
                    val layoutParams = window.attributes
                    layoutParams.preferredDisplayModeId = bestMode.modeId
                    layoutParams.preferredRefreshRate = bestMode.refreshRate
                    window.attributes = layoutParams
                }
            }
        } catch (e: Exception) {
            // Log error if needed
        }
    }
}
