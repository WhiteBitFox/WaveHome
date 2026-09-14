package com.example.wavehome

import android.content.Context

object UserStatusResolver {
    const val STATUS_SLEEPING = "spi"
    const val STATUS_WALKING = "chodzi"
    const val STATUS_DRIVING = "jedzie"
    const val STATUS_RUNNING = "biega"
    const val STATUS_STILL = "stoi"
    const val STATUS_UNKNOWN = "nieznany"

    fun resolve(activityLog: ActivityLog?, sleep: SleepSnapshot?): String {
        if (sleep?.isSleepingNow == true) return STATUS_SLEEPING
        return activityLog?.userStatus
            ?: fromActivityTransition(activityLog?.activityType, activityLog?.transitionType)
            ?: STATUS_UNKNOWN
    }

    fun fromActivityTransition(activityType: String?, transitionType: String?): String? {
        if (activityType.isNullOrBlank()) return null
        if (transitionType == "EXIT") return null

        val normalized = activityType.uppercase()
        return when {
            "IN VEHICLE" in normalized -> STATUS_DRIVING
            "WALKING" in normalized -> STATUS_WALKING
            "RUNNING" in normalized -> STATUS_RUNNING
            "STILL" in normalized -> STATUS_STILL
            else -> STATUS_UNKNOWN
        }
    }

    fun correctActivityForContext(context: Context, activityType: String): String {
        val normalized = activityType.uppercase()
        return if ("IN VEHICLE" in normalized && HomePresenceResolver.isAtHome(context)) {
            "STILL"
        } else {
            activityType
        }
    }
}
