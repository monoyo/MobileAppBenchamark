package com.jossy.android.mobilebenchmarkappkotlin.activity

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.jossy.android.mobilebenchmarkappkotlin.BenchmarkApplication
import com.jossy.android.mobilebenchmarkappkotlin.R
import com.jossy.android.mobilebenchmarkappkotlin.data.TestResult

class BenchmarkSuiteActivity : AppCompatActivity() {
    companion object {
        private const val PERMISSION_REQUEST_CODE = 123
        private const val TEST_ITERATIONS = 30
        private const val ALL_TESTS = 6
        private const val TEST_ACTIVITY_REQUEST_CODE = 456
    }

    private lateinit var currentTestInfo: TextView
    private lateinit var testResults: TextView
    private lateinit var testProgress: ProgressBar
    private lateinit var startTestsButton: Button
    private lateinit var exportResultsButton: Button

    private val allResults = mutableListOf<TestResult>()
    private var currentIteration = 0
    private var currentTestIndex = 0
    private var isRunning = false
    private val handler = Handler(Looper.getMainLooper())
    private var resultBuilder = StringBuilder()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_benchmark_suite)

        currentTestInfo = findViewById(R.id.currentTestInfo)
        testResults = findViewById(R.id.testResults)
        testProgress = findViewById(R.id.testProgress)
        startTestsButton = findViewById(R.id.startTestsButton)
        exportResultsButton = findViewById(R.id.exportResultsButton)

        startTestsButton.setOnClickListener {
            if (!isRunning) startTestSuite()
        }

        exportResultsButton.setOnClickListener { exportResults() }

        testProgress.max = TEST_ITERATIONS * 4
        Log.d("BenchmarkSuiteActivity", "onCreate completed, UI initialized")
    }

    private fun startTestSuite() {
        if (checkPermissions()) {
            isRunning = true
            currentIteration = 0
            currentTestIndex = 0
            allResults.clear()
            resultBuilder = StringBuilder()
            testResults.text = ""
            startTestsButton.text = "Running..."
            startTestsButton.isEnabled = false
            runNextTest()
        } else {
            requestPermissions()
        }
    }

    private fun runNextTest() {
        if (currentTestIndex < ALL_TESTS) {
            if (currentIteration < TEST_ITERATIONS) {
                val testName = getTestName(currentTestIndex)
                onTestStarted(testName)
                startSpecificTest(currentTestIndex)
                currentIteration++
            } else {
                currentTestIndex++
                currentIteration = 0
                if (currentTestIndex < ALL_TESTS) runNextTest()
            }
        } else {
            onAllTestsCompleted()
        }
    }

    private fun getTestName(index: Int): String =
        when (index) {
            0 -> "UI Test"
            1 -> "CPU Test"
            2 -> "RAM Test"
            3 -> "Image Loading Test"
            4 -> "API Test"
            5 -> "Location Test"
            else -> "Unknown Test"
        }

    private fun startSpecificTest(index: Int) {
        val intent =
            when (index) {
                0 -> Intent(this, UITestActivity::class.java)
                1 -> Intent(this, CPUTestActivity::class.java)
                2 -> Intent(this, RAMTestActivity::class.java)
                3 -> Intent(this, ImageLoadingActivity::class.java)
                4 -> Intent(this, ApiTestActivity::class.java)
                5 -> Intent(this, LocationTestActivity::class.java)
                else -> return
            }
        intent.putExtra("auto_mode", true)
        intent.putExtra("callback_activity", BenchmarkSuiteActivity::class.java.name)
        startActivityForResult(intent, TEST_ACTIVITY_REQUEST_CODE)
    }

    fun onTestCompleted(result: TestResult) {
        allResults.add(result)
        updateProgress()
        appendResult(result)
        handler.postDelayed({ runNextTest() }, 1000)
    }

    private fun onAllTestsCompleted() {
        isRunning = false
        startTestsButton.text = "Start Tests"
        startTestsButton.isEnabled = true
        currentTestInfo.text = "All tests completed!"
        calculateAndDisplayAverages()
    }

    private fun onTestStarted(testName: String) {
        currentTestInfo.text = "Running: $testName (Iteration ${currentIteration + 1}/$TEST_ITERATIONS)"
    }

    private fun updateProgress() {
        val progress = (currentIteration * 4) + currentTestIndex
        testProgress.progress = progress
    }

    private fun appendResult(result: TestResult) {
        val resultText = "[Iteration ${currentIteration + 1}] ${result.testName}: ${result.executionTime}ms - ${result.details}\n"
        resultBuilder.append(resultText)
        testResults.text = resultBuilder.toString()
    }

    private fun calculateAndDisplayAverages() {
        val averages = StringBuilder("\nAverage Results:\n")
        for (i in 0..3) {
            val testName = getTestName(i)
            var sum: Long = 0
            var count = 0
            for (result in allResults) {
                if (result.testName == testName) {
                    sum += result.executionTime
                    count++
                }
            }
            if (count > 0) {
                val avg = sum.toDouble() / count
                averages.append("$testName Average: ${"%.2f".format(avg).replace(',', '.')}ms\n")
            }
        }
        resultBuilder.append(averages)
        testResults.text = resultBuilder.toString()
    }

    private fun exportResults() {
        checkAndRequestStoragePermissions()

        try {
            val timestamp = System.currentTimeMillis().toString()
            val fileName = "benchmark_results_$timestamp.txt"

            openFileOutput(fileName, Context.MODE_PRIVATE).use { output ->
                output.write(resultBuilder.toString().encodeToByteArray())
            }

            Toast.makeText(this, "Results exported to $fileName", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Error exporting results: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkPermissions(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), PERMISSION_REQUEST_CODE)
    }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?,
    ) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                if (Environment.isExternalStorageManager()) {
                    startTestSuite()
                } else {
                    Toast
                        .makeText(
                            this,
                            "Permissions required to run tests",
                            Toast.LENGTH_SHORT,
                        ).show()
                }
            }
        } else if (requestCode == TEST_ACTIVITY_REQUEST_CODE && resultCode == RESULT_OK) {
            val result = data?.getSerializableExtra(BenchmarkApplication.RESULT) as? TestResult
            if (result != null) onTestCompleted(result) else Log.e("BenchmarkSuiteActivity", "No TestResult received from test activity")
        }
    }

    // Launcher do obsługi wyniku zapytania o standardowe uprawnienia
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                // Uprawnienie przyznane (dla Android < 11 lub dla READ_EXTERNAL_STORAGE w Android 11+)
                showToast("Uprawnienie do odczytu pamięci przyznane.")
                // Tutaj możesz wykonać operacje wymagające dostępu do pamięci
                performStorageOperation()
            } else {
                // Uprawnienie odrzucone
                showToast("Uprawnienie do odczytu pamięci odrzucone.")
                // Możesz poinformować użytkownika o konsekwencjach lub pokazać dialog wyjaśniający
            }
        }

    // Launcher do obsługi wyniku zapytania o zarządzanie wszystkimi plikami (Android 11+)
    private val requestManageStorageLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (Environment.isExternalStorageManager()) {
                    // Uprawnienie "Zarządzaj wszystkimi plikami" przyznane
                    showToast("Uprawnienie 'Zarządzaj wszystkimi plikami' przyznane.")
                    // Tutaj możesz wykonać operacje wymagające tego specjalnego dostępu
                    performStorageOperationRequiringManageAccess()
                } else {
                    // Uprawnienie "Zarządzaj wszystkimi plikami" odrzucone
                    showToast("Uprawnienie 'Zarządzaj wszystkimi plikami' odrzucone.")
                    // Poinformuj użytkownika o konieczności tego uprawnienia
                }
            }
        }

    private fun checkAndRequestStoragePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) { // Android 11 (API 30) i nowsze
            if (Environment.isExternalStorageManager()) {
                // Aplikacja ma już uprawnienie "Zarządzaj wszystkimi plikami"
                showToast("Aplikacja ma już uprawnienie 'Zarządzaj wszystkimi plikami'.")
                performStorageOperationRequiringManageAccess()
            } else {
                // Aplikacja nie ma uprawnienia "Zarządzaj wszystkimi plikami"
                // Należy poprosić użytkownika o jego przyznanie przez specjalny ekran systemowy
                showToast("Proszę o uprawnienie 'Zarządzaj wszystkimi plikami'.")
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.addCategory("android.intent.category.DEFAULT")
                    intent.data =
                        Uri.parse(String.format("package:%s", applicationContext.packageName))
                    requestManageStorageLauncher.launch(intent)
                } catch (e: Exception) {
                    val intent = Intent()
                    intent.action = Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION
                    requestManageStorageLauncher.launch(intent)
                    showToast("Nie można otworzyć ustawień, spróbuj ręcznie.")
                }
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) { // Android 6 (API 23) do Android 10 (API 29)
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                ) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE, // Zazwyczaj potrzebujesz obu
                    ) == PackageManager.PERMISSION_GRANTED -> {
                    // Uprawnienia już przyznane
                    showToast("Uprawnienia do pamięci już przyznane.")
                    performStorageOperation()
                }

                shouldShowRequestPermissionRationale(Manifest.permission.READ_EXTERNAL_STORAGE) ||
                    shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE) -> {
                    // Wyjaśnij użytkownikowi, dlaczego potrzebujesz tych uprawnień
                    // np. pokaż dialog
                    showToast("Potrzebujemy dostępu do pamięci, aby zapisać/odczytać pliki.")
                    // Następnie poproś o uprawnienia
                    requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE) // Możesz też prosić o WRITE_EXTERNAL_STORAGE
                    // Lub użyj requestMultiplePermissions jeśli potrzebujesz obu na raz:
                    // requestMultiplePermissionsLauncher.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE))
                }

                else -> {
                    // Bezpośrednio poproś o uprawnienie
                    requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                    // Lub użyj requestMultiplePermissions
                }
            }
        } else { // Poniżej Android 6 (API 23)
            // Uprawnienia są przyznawane podczas instalacji aplikacji
            showToast("Uprawnienia do pamięci przyznane (starsza wersja Androida).")
            performStorageOperation()
        }
    }

    private fun performStorageOperation() {
        // Tutaj umieść kod, który wykonuje operacje na pamięci
        // np. odczyt/zapis plików w publicznych katalogach (Downloads, Documents, Pictures)
        // lub w katalogu specyficznym dla aplikacji (getExternalFilesDir, getExternalCacheDir)
        showToast("Wykonywanie operacji na pamięci...")
        // Pamiętaj, że WRITE_EXTERNAL_STORAGE jest w dużej mierze przestarzałe dla Android 10+
        // i dla Android 11+ nawet READ_EXTERNAL_STORAGE ma ograniczone działanie bez MANAGE_EXTERNAL_STORAGE
    }

    private fun performStorageOperationRequiringManageAccess() {
        // Tutaj umieść kod, który wymaga pełnego dostępu do zarządzania plikami (Android 11+)
        // np. dostęp do plików poza katalogami specyficznymi dla aplikacji i poza MediaStore
        showToast("Wykonywanie operacji na pamięci z uprawnieniem 'Zarządzaj wszystkimi plikami'...")
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    // Opcjonalnie: Launcher do obsługi wyniku zapytania o wiele uprawnień na raz (przydatne dla READ i WRITE)
    private val requestMultiplePermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val readGranted = permissions[Manifest.permission.READ_EXTERNAL_STORAGE] ?: false
            val writeGranted =
                permissions[Manifest.permission.WRITE_EXTERNAL_STORAGE]
                    ?: false // Jeśli również o nie prosisz

            if (readGranted && writeGranted) { // Sprawdź wszystkie potrzebne
                showToast("Uprawnienia do odczytu i zapisu pamięci przyznane.")
                performStorageOperation()
            } else if (readGranted) {
                showToast("Uprawnienie do odczytu pamięci przyznane, ale do zapisu nie.")
                // Możesz obsłużyć ten przypadek osobno
            } else {
                showToast("Jedno lub więcej uprawnień do pamięci odrzucone.")
                // Poinformuj użytkownika
            }
        }

    // Dodaj to do swojego AndroidManifest.xml:
    // Dla Androida 10 (API 29) i starszych, jeśli chcesz zapisywać do pamięci zewnętrznej (poza katalogiem aplikacji):
    // <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" android:maxSdkVersion="29" />
    // Dla wszystkich wersji, jeśli chcesz odczytywać z pamięci zewnętrznej (poza katalogiem aplikacji):
    // <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
    // Dla Androida 11 (API 30) i nowszych, jeśli potrzebujesz szerokiego dostępu do zarządzania plikami:
    // <uses-permission android:name="android.permission.MANAGE_EXTERNAL_STORAGE" />
    // Pamiętaj, aby dodać android:requestLegacyExternalStorage="true" w tagu <application> w manifeście,
    // jeśli chcesz tymczasowo korzystać ze starszego modelu pamięci na Androidzie 10, ale staraj się tego unikać.
}
