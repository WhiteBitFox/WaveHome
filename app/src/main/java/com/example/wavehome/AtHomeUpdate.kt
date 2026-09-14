package com.example.wavehome

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.location.Location
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices

fun AtHomeUpdate(context: Context, sharedPreferences: SharedPreferences) {
    val savedLat = sharedPreferences.getString("home_lat", null)?.toDoubleOrNull()
    val savedLng = sharedPreferences.getString("home_lng", null)?.toDoubleOrNull()

    // Nie zapisano jeszcze lokalizacji domu
    if (savedLat == null || savedLng == null) {
        return
    }

    val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
        if (location != null) {
            val currentLocation = Location("").apply {
                latitude = location.latitude
                longitude = location.longitude
            }

            val homeLocation = Location("").apply {
                latitude = savedLat
                longitude = savedLng
            }

            val distance = currentLocation.distanceTo(homeLocation) // w metrach (na dole war)
            val isHome = distance < 35 //spr czy git

            sharedPreferences.edit()
                .putBoolean("is_home", isHome)
                .apply()
        }
    }

}
