package com.annales.journal.reminder

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.annales.journal.MainActivity
import com.annales.journal.R
import com.annales.journal.data.JournalStore
import com.annales.journal.data.Settings
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** A soft nudge at 21:30 if today is still unsealed. Inexact on purpose: no exact-alarm permission needed. */
object Reminder {
    private const val CHANNEL = "evening"
    private val AT: LocalTime = LocalTime.of(21, 30)

    fun schedule(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = pending(context)
        am.cancel(pi)
        if (!Settings(context).reminder) return
        var next = LocalDateTime.of(LocalDate.now(), AT)
        if (!next.isAfter(LocalDateTime.now())) next = next.plusDays(1)
        val millis = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        am.setInexactRepeating(AlarmManager.RTC_WAKEUP, millis, AlarmManager.INTERVAL_DAY, pi)
    }

    private fun pending(context: Context) = PendingIntent.getBroadcast(
        context, 0, Intent(context, ReminderReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun notifyIfUnsealed(context: Context) {
        val today = JournalStore(context).load()[LocalDate.now()]
        if (today?.sealed == true) return
        val nm = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.reminder_channel), NotificationManager.IMPORTANCE_DEFAULT))
        }
        val open = PendingIntent.getActivity(
            context, 1, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val text = if (today == null || today.stamps.isEmpty()) "Today is still unsealed. Pick a photo before midnight."
        else "Today is still unsealed. Your ${if (today.stamps.size == 1) "stamp is" else "stamps are"} waiting for the postmark."
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_annales)
            .setColor(0xFF9B2F35.toInt())
            .setContentTitle("Annales")
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(1, n) } // no-op if notifications are off
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = Reminder.notifyIfUnsealed(context)
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) Reminder.schedule(context)
    }
}
