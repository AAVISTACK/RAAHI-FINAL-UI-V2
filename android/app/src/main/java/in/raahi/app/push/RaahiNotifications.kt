package `in`.raahi.app.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import `in`.raahi.app.MainActivity
import `in`.raahi.app.R

private const val CHANNEL_ID = "raahi_default"
private const val CHANNEL_NAME = "Raahi Updates"

object RaahiNotifications {

    /** Called once from RaahiApp.onCreate — channels must exist before the first
     * notification is posted, and creating one that already exists is a safe no-op. */
    fun createChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Job status, SOS alerts, and other real-time updates"
        }
        manager.createNotificationChannel(channel)
    }

    /** Posts a real notification via NotificationManager — this is what was missing before:
     * onMessageReceived only logged the payload, so a message arriving while the app was in
     * the foreground (where the OS does NOT auto-display notification-payload messages,
     * unlike when backgrounded) was silently dropped from the user's point of view. */
    fun show(context: Context, title: String, body: String, data: Map<String, String>) {
        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            data.forEach { (key, value) -> putExtra(key, value) }
        }
        val pendingIntent = PendingIntent.getActivity(
            context, System.currentTimeMillis().toInt(), tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
