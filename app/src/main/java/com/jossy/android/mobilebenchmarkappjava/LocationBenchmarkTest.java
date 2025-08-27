package com.jossy.android.mobilebenchmarkappkotlin;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;

import androidx.core.app.ActivityCompat;

public class LocationBenchmarkTest {

    private final Context context;
    private LocationManager locationManager;
    private long startTime;

    public LocationBenchmarkTest(Context context) {
        this.context = context;
        this.locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
    }

    @SuppressLint("MissingPermission")
    public void startTest() {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) 
                != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) 
                != PackageManager.PERMISSION_GRANTED) {
            Log.e("LocationBenchmark", "Brak uprawnień do lokalizacji!");
            return;
        }

        startTime = SystemClock.elapsedRealtime();

        locationManager.requestSingleUpdate(
                LocationManager.GPS_PROVIDER,
                new LocationListener() {
                    @Override
                    public void onLocationChanged(Location location) {
                        long elapsed = SystemClock.elapsedRealtime() - startTime;
                        Log.i("LocationBenchmark", "Czas uzyskania lokalizacji: " + elapsed + " ms");
                        Log.i("LocationBenchmark", "Szerokość: " + location.getLatitude() + 
                                                   ", Długość: " + location.getLongitude() + 
                                                   ", Dokładność: " + location.getAccuracy() + " m");
                        locationManager.removeUpdates(this);
                    }

                    @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
                    @Override public void onProviderEnabled(String provider) {}
                    @Override public void onProviderDisabled(String provider) {}
                },
                null
        );
    }
}
