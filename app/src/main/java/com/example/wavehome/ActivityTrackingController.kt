package com.example.wavehome

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.wavehome.receiver.MoveMoveReceiver
import com.example.wavehome.util.Constants
import com.example.wavehome.util.MoveMoveUtil
import com.google.android.gms.location.ActivityRecognition

object ActivityTrackingController {
    fun enable(context: Context): Boolean {
        val appContext = context.applicationContext
        if (!hasPermission(appContext)) return false

        ActivityRecognition.getClient(appContext)
            .requestActivityTransitionUpdates(
                MoveMoveUtil.getActivityTransitionRequest(),
                getActivityTransitionPendingIntent(appContext)
            )

        appContext.getSharedPreferences(APP_SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean("is_tracking_enabled", true)
            .apply()

        return true
    }

    fun disable(context: Context) {
        val appContext = context.applicationContext
        ActivityRecognition.getClient(appContext)
            .removeActivityTransitionUpdates(getActivityTransitionPendingIntent(appContext))
            .addOnSuccessListener {
                getActivityTransitionPendingIntent(appContext).cancel()
            }

        appContext.getSharedPreferences(APP_SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean("is_tracking_enabled", false)
            .apply()
    }

    private fun hasPermission(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACTIVITY_RECOGNITION
                ) == PackageManager.PERMISSION_GRANTED
    }

    private fun getActivityTransitionPendingIntent(context: Context): PendingIntent {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        return PendingIntent.getBroadcast(
            context,
            Constants.REQUEST_CODE_INTENT_ACTIVITY_TRANSITION,
            Intent(context, MoveMoveReceiver::class.java),
            flags
        )
    }
}
