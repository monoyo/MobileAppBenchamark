package com.jossy.android.mobilebenchmarkappjava;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.os.Looper;
import android.util.Log;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

public class LocationTestActivity extends AppCompatActivity {
    private static final int PERMISSION_REQUEST_CODE = 123;
    private static final long UPDATE_INTERVAL = 1000L;
    private static final long FASTEST_UPDATE_INTERVAL = 500L;
    private static final int ACCURACY_THRESHOLD = 20; // meters

    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private TextView locationStatus, accuracyView, timeToFirstFix, coordinates;
    private long startTime;
    private boolean firstFix = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d("LocationTestActivity", "onCreate called");
        setContentView(R.layout.activity_location_test);

        locationStatus = findViewById(R.id.locationStatus);
        accuracyView = findViewById(R.id.accuracyView);
        timeToFirstFix = findViewById(R.id.timeToFirstFix);
        coordinates = findViewById(R.id.coordinates);
        Log.d("LocationTestActivity", "Views initialized");

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        setupLocationCallback();

        if (checkPermissions()) {
            startLocationUpdates();
        } else {
            requestPermissions();
        }
    }

    private void setupLocationCallback() {
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                Location location = locationResult.getLastLocation();
                if (location != null) {
                    updateLocationInfo(location);
                }
            }
        };
    }

    private void updateLocationInfo(Location location) {
        float accuracy = location.getAccuracy();
        accuracyView.setText("Accuracy: " + accuracy + " meters");
        coordinates.setText(String.format("Location: %.6f, %.6f", 
                location.getLatitude(), location.getLongitude()));

        if (firstFix) {
            long ttff = System.currentTimeMillis() - startTime;
            timeToFirstFix.setText("Time to first fix: " + ttff + "ms");
            firstFix = false;
            locationStatus.setText("Location acquired!");

            if (accuracy <= ACCURACY_THRESHOLD) {
                stopLocationUpdates();
                locationStatus.setText("Test completed - Good accuracy achieved");
            }
        }
    }

    private boolean checkPermissions() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) 
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermissions() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                PERMISSION_REQUEST_CODE);
    }

    private void startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) 
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        LocationRequest locationRequest = new LocationRequest.Builder(UPDATE_INTERVAL)
                .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                .setMinUpdateIntervalMillis(FASTEST_UPDATE_INTERVAL)
                .build();

        startTime = System.currentTimeMillis();
        locationStatus.setText("Requesting location updates...");

        fusedLocationClient.requestLocationUpdates(locationRequest,
                locationCallback, Looper.getMainLooper());
    }

    private void stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                         @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startLocationUpdates();
        } else {
            locationStatus.setText("Location permission denied");
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopLocationUpdates();
    }
}
