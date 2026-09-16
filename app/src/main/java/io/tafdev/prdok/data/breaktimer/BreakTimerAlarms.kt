package io.tafdev.prdok.data.breaktimer

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.tafdev.prdok.MainActivity
import io.tafdev.prdok.R

/** Arranges for the "break is over" notification. An interface so the manager can be tested without Android. */
interface BreakAlarmScheduler {
    fun schedule(active: ActiveBreak)
    fun cancel()
}

/**
 * Hands the end moment to [AlarmManager], which wakes [BreakTimerReceiver] at that time even if
 * the app isn't running. Nothing in the app has to stay alive for the notification to arrive.
 */
class AlarmManagerBreakAlarmScheduler(context: Context) : BreakAlarmScheduler {

    private val context = context.applicationContext
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    override fun schedule(active: ActiveBreak) {
        BreakTimerNotification.createChannel(context)
        val triggerAt = active.endsAt.toEpochMilli()
        val intent = alarmIntent(active.timer.minutes)
        // An inexact alarm may be deferred by many minutes, which defeats a 15-minute timer.
        // The manifest asks for exact alarms, so the fallback only matters if that is ever taken away.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, intent)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, intent)
        }
    }

    override fun cancel() {
        alarmManager.cancel(alarmIntent(minutes = 0))
    }

    /**
     * The system tells PendingIntents apart by their Intent, ignoring the extras, so every call
     * here yields the same one: scheduling again replaces the old alarm (FLAG_UPDATE_CURRENT
     * refreshes the minutes), and cancel finds it without knowing which timer was running.
     */
    private fun alarmIntent(minutes: Int): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, BreakTimerReceiver::class.java).putExtra(EXTRA_MINUTES, minutes),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

private const val EXTRA_MINUTES = "minutes"

/** Registered in the manifest; the system starts it when the alarm goes off. */
class BreakTimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        BreakTimerNotification.post(context, intent.getIntExtra(EXTRA_MINUTES, 0))
    }
}

internal object BreakTimerNotification {
    private const val CHANNEL_ID = "break_timer"
    private const val NOTIFICATION_ID = 1

    /**
     * Since Android 8 every notification belongs to a channel, which the user can mute on its own
     * in the system settings. Creating one that already exists does nothing, so it is safe to call
     * each time. HIGH importance is what makes it pop up over other apps, with sound.
     */
    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.break_timer_channel),
            NotificationManager.IMPORTANCE_HIGH,
        )
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun post(context: Context, minutes: Int) {
        // Permission can be revoked while the alarm is pending; posting without it would throw.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        createChannel(context)
        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(context.getString(R.string.break_timer_finished, minutes))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }
}
