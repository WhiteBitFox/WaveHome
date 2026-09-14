package com.example.wavehome

import android.content.Context
import android.net.wifi.WifiManager

object HomePresenceResolver {
    fun isAtHome(context: Context): Boolean {
        val appContext = context.applicationContext
        val appPrefs = appContext.getSharedPreferences(APP_PREFS_NAME, Context.MODE_PRIVATE)
        if (appPrefs.getBoolean("is_home", false)) return true

        val settingsPrefs = appContext.getSharedPreferences(
            APP_SETTINGS_PREFS_NAME,
            Context.MODE_PRIVATE
        )
        val configuredSsid = settingsPrefs.getString(PREF_HOME_WIFI_SSID, null)
            ?: appPrefs.getString(PREF_HOME_WIFI_SSID, null)
            ?: return false

        val homeSsid = normalizeSsid(configuredSsid)
        val currentSsid = currentWifiSsid(appContext) ?: return false

        return currentSsid.equals(homeSsid, ignoreCase = true)
    }

    @Suppress("DEPRECATION")
    private fun currentWifiSsid(context: Context): String? {
        return runCatching {
            val wifiManager = context.applicationContext
                .getSystemService(Context.WIFI_SERVICE) as WifiManager
            normalizeSsid(wifiManager.connectionInfo?.ssid)
                ?.takeIf { it.isNotBlank() && it != "<unknown ssid>" }
        }.getOrNull()
    }

    private fun normalizeSsid(value: String?): String? {
        return value
            ?.trim()
            ?.removeSurrounding("\"")
            ?.takeIf { it.isNotBlank() }
    }
}
