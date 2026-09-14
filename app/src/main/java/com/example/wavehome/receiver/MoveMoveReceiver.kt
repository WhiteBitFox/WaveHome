package com.example.wavehome.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.wavehome.ActivityDatabase
import com.example.wavehome.ActivityLog
import com.example.wavehome.AppDatabase
import com.example.wavehome.UserStatusResolver
import com.example.wavehome.util.Constants
import com.example.wavehome.util.MoveMoveUtil
import com.google.android.gms.location.ActivityTransitionResult
import io.karn.notify.Notify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MoveMoveReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (ActivityTransitionResult.hasResult(intent)) {
            val result = ActivityTransitionResult.extractResult(intent)
            result?.let {

                // --- CZĘŚĆ 1: ZAPIS DO BAZY DANYCH W TLE ---
                // goAsync() informuje system, że będziemy robić coś w tle i żeby nie zabijał
                // tego Receivera od razu po zakończeniu funkcji onReceive.
                val pendingResult = goAsync()

                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        // Inicjalizacja bazy
                        val database = ActivityDatabase.getDatabase(context)
                        val dao = database.activityLogDao()

                        result.transitionEvents.forEach { event ->
                            val rawActivityName = MoveMoveUtil.toActivityString(event.activityType)
                            val activityName = UserStatusResolver.correctActivityForContext(
                                context,
                                rawActivityName
                            )
                            val transitionName = MoveMoveUtil.toTransitionType(event.transitionType)

                            val log = ActivityLog(
                                activityType = activityName,
                                transitionType = transitionName,
                                userStatus = UserStatusResolver.fromActivityTransition(
                                    activityName,
                                    transitionName
                                )
                            )
                            dao.insertLog(log)
                        }
                    } finally {
                        // Ważne: musimy poinformować system, że skończyliśmy pracę w tle
                        pendingResult.finish()
                    }
                }

                // --- CZĘŚĆ 2: GŁÓWNY WĄTEK ---
                result.transitionEvents.forEach { event ->
                    val rawActivityString = MoveMoveUtil.toActivityString(event.activityType)
                    val activityString = UserStatusResolver.correctActivityForContext(
                        context,
                        rawActivityString
                    )

                    // Info for debugging purposes
                    val info =
                        "Transition: " + activityString +
                                " (" + MoveMoveUtil.toTransitionType(event.transitionType) + ")" + "   " +
                                SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())

                    // Powiadomienie
                    Notify
                        .with(context)
                        .content {
                            title = "Activity Detected"
                            text = "I can see you are in $activityString state"
                        }
                        .show(id = Constants.ACTIVITY_TRANSITION_NOTIFICATION_ID)

                    // Toast
                    Toast.makeText(context, info, Toast.LENGTH_LONG).show()

                    // --- CZĘŚĆ 3: AKTUALIZACJA INTERFEJSU  ---
                    val localIntent = Intent("user-activity-update")
                    localIntent.putExtra("activity_info", activityString)
                    LocalBroadcastManager.getInstance(context).sendBroadcast(localIntent)
                }
            }
        }
    }
}
