package com.nicholaston.callscribe.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.kitsumed.shizucallrecorder.MainActivity
import com.kitsumed.shizucallrecorder.R
import com.kitsumed.shizucallrecorder.data.call.EnrichedCallData

object CallScribeNotificationHelper {
    private const val CHANNEL_CONSENT = "callscribe_consent"
    private const val CHANNEL_MISSED = "callscribe_missed_recordings"
    private const val CONSENT_ID = 20_101
    private const val MISSED_ID = 20_102

    fun showConsentReminder(context: Context, metadata: EnrichedCallData) {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return
        createChannels(context)
        val caller = metadata.callerName ?: metadata.getBestNumber().ifBlank { "this caller" }
        val notification = NotificationCompat.Builder(context, CHANNEL_CONSENT)
            .setSmallIcon(R.drawable.ic_mic)
            .setContentTitle("Remember to get consent")
            .setContentText("Tell $caller the call is being recorded when required by law.")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "Tell $caller the call is being recorded when required by law. " +
                        "CallScribe cannot obtain consent for you.",
                ),
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setTimeoutAfter(30_000)
            .setContentIntent(openAppIntent(context))
            .build()
        NotificationManagerCompat.from(context).notify(CONSENT_ID, notification)
    }

    fun showShizukuNotRunning(context: Context, metadata: EnrichedCallData) {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return
        createChannels(context)
        val caller = metadata.callerName ?: metadata.getBestNumber().ifBlank { "Unknown caller" }
        val notification = NotificationCompat.Builder(context, CHANNEL_MISSED)
            .setSmallIcon(R.drawable.ic_stop)
            .setContentTitle("Call NOT recorded — Shizuku not running")
            .setContentText(caller)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context))
            .build()
        NotificationManagerCompat.from(context).notify(MISSED_ID, notification)
    }

    private fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_CONSENT,
                "Recording consent reminders",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Heads-up reminder to notify callers before recording"
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_MISSED,
                "Missed recordings",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Alerts when a call could not be recorded"
                lockscreenVisibility = NotificationCompat.VISIBILITY_PRIVATE
            },
        )
    }

    private fun openAppIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(
            Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP,
        ),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

}
