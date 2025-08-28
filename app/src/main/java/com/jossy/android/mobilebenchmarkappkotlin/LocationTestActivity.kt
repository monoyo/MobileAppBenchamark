package com.jossy.android.mobilebenchmarkappkotlin

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.catch
import com.jossy.android.mobilebenchmarkappkotlin.data.TestResult
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch

class LocationTestActivity : ComponentActivity() {
    private lateinit var locationTest: LocationBenchmarkTest
    private lateinit var resultTextView: TextView
    private lateinit var mapImageView: ImageView
    private lateinit var progressBar: ProgressBar
    private lateinit var singleLocationButton: Button
    private lateinit var continuousLocationButton: Button
    private var isContinuousLocationActive = false
    private var autoMode: Boolean = false

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val hasLocationPermissions = permissions.entries.all { it.value }
        if (hasLocationPermissions) {
            enableLocationButtons()
            // If activity launched in auto mode, start the single location test immediately
            if (autoMode) startSingleLocationTest()
        } else {
            showError("Brak uprawnień do lokalizacji")
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_location_test)
    // Check if this activity was started in automatic mode by the benchmark suite
    autoMode = intent?.getBooleanExtra("auto_mode", false) == true

    locationTest = LocationBenchmarkTest(this)
        setupViews()
        checkPermissionsAndSetup()
    }

    private fun setupViews() {
        resultTextView = findViewById(R.id.resultTextView)
        mapImageView = findViewById(R.id.mapImageView)
        progressBar = findViewById(R.id.progressBar)
        singleLocationButton = findViewById(R.id.singleLocationButton)
        continuousLocationButton = findViewById(R.id.continuousLocationButton)

        singleLocationButton.setOnClickListener { startSingleLocationTest() }
        continuousLocationButton.setOnClickListener { toggleContinuousLocationTest() }
    }

    private fun checkPermissionsAndSetup() {
        when {
            hasLocationPermissions() -> enableLocationButtons()
            else -> requestLocationPermissions()
        }
    }

    private fun hasLocationPermissions(): Boolean {
        return REQUIRED_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestLocationPermissions() {
        locationPermissionRequest.launch(REQUIRED_PERMISSIONS)
    }

    private fun enableLocationButtons() {
        singleLocationButton.isEnabled = true
        continuousLocationButton.isEnabled = true
        // If started in automatic mode, immediately begin the single-location test
        if (autoMode) {
            singleLocationButton.post { startSingleLocationTest() }
        }
    }

    private fun startSingleLocationTest() {
        showLoading(true)
        clearPreviousResults()

        lifecycleScope.launch {
            try {
                val result = locationTest.getCurrentLocation()
                displayResult(result)
                sendResultAndFinish(result)
            } catch (e: Exception) {
                showError("Błąd: ${e.message}")
            } finally {
                showLoading(false)
            }
        }
    }

    private fun toggleContinuousLocationTest() {
        if (isContinuousLocationActive) {
            stopContinuousLocationTest()
        } else {
            startContinuousLocationTest()
        }
    }

    private fun startContinuousLocationTest() {
        showLoading(true)
        clearPreviousResults()
        isContinuousLocationActive = true
        continuousLocationButton.text = "Stop"

        lifecycleScope.launch {
            locationTest.getLocationUpdates()
                .take(10) // Limit do 10 aktualizacji
                .catch { e -> showError("Błąd: ${e.message}") }
                .collect { result ->
                    displayResult(result)
                    if (result.accuracy <= 20) { // Jeśli dokładność jest wystarczająca
                        sendResultAndFinish(result)
                        stopContinuousLocationTest()
                    }
                }
        }
    }

    private fun stopContinuousLocationTest() {
        isContinuousLocationActive = false
        continuousLocationButton.text = "Start Continuous Test"
        showLoading(false)
    }

    private fun displayResult(result: LocationTestResult) {
        val resultText = """
            Czas uzyskania lokalizacji: ${result.elapsedTimeMs} ms
            Szerokość: ${result.latitude}
            Długość: ${result.longitude}
            Dokładność: ${result.accuracy} m
            Dostawca: ${result.provider}
            ${result.altitude?.let { "Wysokość: $it m" } ?: ""}
            ${result.speed?.let { "Prędkość: $it m/s" } ?: ""}
            ${result.bearing?.let { "Kierunek: $it stopni" } ?: ""}
        """.trimIndent()

        resultTextView.text = resultText
        locationTest.loadStaticMap(mapImageView, result)
    }

    private fun sendResultAndFinish(result: LocationTestResult) {
        val benchmarkResult = TestResult(
            testName = "Location Test",
            executionTime = result.elapsedTimeMs,
            details = "Accuracy: ${result.accuracy}m, Provider: ${result.provider}",
            isSuccessful = true
        )
        setResult(RESULT_OK, Intent().apply {
            putExtra(BenchmarkApplication.RESULT, benchmarkResult)
        })
        
        if (!isContinuousLocationActive) {
            finish()
        }
    }

    private fun showLoading(isLoading: Boolean) {
        progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        singleLocationButton.isEnabled = !isLoading
        continuousLocationButton.isEnabled = !isLoading
    }

    private fun showError(message: String) {
        resultTextView.text = message
    }

    private fun clearPreviousResults() {
        resultTextView.text = ""
        mapImageView.setImageDrawable(null)
    }

    companion object {
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    }
}
