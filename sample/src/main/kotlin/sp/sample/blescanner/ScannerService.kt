package sp.sample.blescanner

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import sp.ax.blescanner.BLEScannerService

internal class ScannerService : BLEScannerService(
    context = App.contexts.main,
    scanner = App.scanner,
    channel = NotificationChannel(
        "a539c7d9-eaee-438f-aeb0-f966a7f2348e",
        "${BuildConfig.APPLICATION_ID}:scanner",
        NotificationManager.IMPORTANCE_HIGH,
    ),
) {
    override fun onStartNotification(channel: NotificationChannel): Notification {
        val context: Context = this
        val intent = Intent(context, ScannerService::class.java)
        intent.action = "stop"
        val stopIntent = PendingIntent.getService(context, 1, intent, PendingIntent.FLAG_IMMUTABLE)
        val action = NotificationCompat.Action.Builder(-1, "stop", stopIntent)
            .build()
        return NotificationCompat.Builder(context, channel.id)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentText("scanner:text")
            .setAutoCancel(false)
            .setOngoing(false)
            .addAction(action)
            .build()
    }
}
