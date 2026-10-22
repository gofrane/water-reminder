package com.waterreminder.app

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import java.time.Instant
import java.time.ZonedDateTime

object Reminders {
    const val CHANNEL = "water_reminders"
    const val ACTION = "com.waterreminder.app.REMIND"
    private const val NOTIFICATION = 1
    private fun state(context: Context) = context.getSharedPreferences("reminder_schedule", Context.MODE_PRIVATE)
    private fun manager(context: Context) = context.getSystemService(NotificationManager::class.java)

    fun createChannel(context: Context) {
        manager(context).createNotificationChannel(NotificationChannel(CHANNEL, "Water reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Gentle reminders during your selected hours"
        })
    }

    fun allowed(context: Context): Boolean {
        createChannel(context)
        return (Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            manager(context).areNotificationsEnabled() &&
            manager(context).getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE
    }

    fun scheduledAt(context: Context): Long = state(context).getLong("next", 0)

    private fun alarmIntent(context: Context, time: Long = 0) = PendingIntent.getBroadcast(
        context, 1, Intent(context, ReminderReceiver::class.java).setAction(ACTION).putExtra("due", time),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    fun reconcile(context: Context, store: WaterStore = WaterStore(context), restartInterval: Boolean = false) {
        val state = state(context)
        val now = ZonedDateTime.now()
        val alarms = context.getSystemService(AlarmManager::class.java)
        if (!store.preferences.remindersEnabled || !allowed(context)) {
            alarms.cancel(alarmIntent(context))
            manager(context).cancel(NOTIFICATION)
            state.edit().remove("next").apply()
            return
        }
        var anchor = state.getLong("anchor", 0)
        if (restartInterval || anchor == 0L || anchor > now.toInstant().toEpochMilli()) {
            anchor = now.toInstant().toEpochMilli()
            state.edit().putLong("anchor", anchor).apply()
        }
        val latestDrink = store.drinks.maxOfOrNull { it.timestamp } ?: 0L
        val lastDelivery = state.getLong("lastDelivery", 0).coerceAtMost(now.toInstant().toEpochMilli())
        val base = maxOf(anchor, latestDrink.coerceAtMost(now.toInstant().toEpochMilli()), lastDelivery)
        val reached = Hydration.total(store.drinks, now.toLocalDate()) >= store.preferences.goal
        if (reached) manager(context).cancel(NOTIFICATION)
        val due = ReminderPlan.next(now, store.preferences, reached, Instant.ofEpochMilli(base))!!.toEpochMilli()
        state.edit().putLong("next", due).apply()
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, due, alarmIntent(context, due))
    }

    fun receive(context: Context, intent: Intent) {
        val store = WaterStore(context)
        val now = ZonedDateTime.now()
        if (intent.action == ACTION) {
            val due = intent.getLongExtra("due", 0)
            // Ignore an obsolete broadcast after settings or intake changed.
            if (due == 0L || due != scheduledAt(context)) return
            if (due <= now.toInstant().toEpochMilli() && store.preferences.remindersEnabled && allowed(context) &&
                ReminderPlan.inWindow(now, store.preferences) &&
                Hydration.total(store.drinks, now.toLocalDate()) < store.preferences.goal) {
                val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                val notification = Notification.Builder(context, CHANNEL)
                    .setSmallIcon(R.drawable.ic_water)
                    .setContentTitle("Time for a little water")
                    .setContentText("Take a sip and log your drink.")
                    .setContentIntent(open).setAutoCancel(true)
                    .setCategory(Notification.CATEGORY_REMINDER).build()
                try {
                    manager(context).notify(NOTIFICATION, notification)
                    state(context).edit().putLong("lastDelivery", now.toInstant().toEpochMilli()).apply()
                } catch (_: SecurityException) {
                    // Permission may have changed between the check and notification delivery.
                }
            }
        }
        reconcile(context, store)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Reminders.ACTION, Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED -> Reminders.receive(context, intent)
        }
    }
}
