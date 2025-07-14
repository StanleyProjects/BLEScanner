package sp.sample.blescanner

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.launch
import sp.ax.blescanner.BLEScanner
import sp.ax.blescanner.BLEScannerService

internal class ScannerService : BLEScannerService(
    main = App.contexts.main,
    scanner = App.scanner,
    channel = NotificationChannel(
        "a539c7d9-eaee-438f-aeb0-f966a7f2348e",
        "${BuildConfig.APPLICATION_ID}:scanner",
        NotificationManager.IMPORTANCE_HIGH,
    ),
) {
    override fun onCreate() {
        super.onCreate()
        coroutineScope.launch {
            App.themes.collect { theme ->
                val notification = buildNotification(
                    channel = channel,
                    theme = theme,
                    state = states.value,
                )
                notify(notification)
            }
        }
        coroutineScope.launch {
            states.collect { state ->
                println("[ScannerService]:state: $state") // todo
                val notification = buildNotification(
                    channel = channel,
                    theme = App.themes.value,
                    state = state,
                )
                notify(notification)
            }
        }
    }

    override fun onStartNotification(): Notification {
        return buildNotification(
            channel = channel,
            theme = App.themes.value,
            state = states.value,
        )
    }

    private fun buildNotification(
        channel: NotificationChannel,
        theme: Theme,
        state: BLEScanner.State,
    ): Notification {
        val context: Context = this
        val builder = NotificationCompat.Builder(context, channel.id)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentText(state.name)
            .setAutoCancel(false)
            .setOngoing(false)
            .setColor(theme.background.toArgb())
            .setColorized(true)
        when (state) {
            BLEScanner.State.Started -> {
                val intent = Intent(context, ScannerService::class.java)
                intent.action = BLEScannerStopAction
                val stopIntent = PendingIntent.getService(context, 1, intent, PendingIntent.FLAG_IMMUTABLE)
                val action = NotificationCompat.Action.Builder(-1, "stop", stopIntent).build()
                builder.addAction(action)
            }
            else -> {
                // noop
            }
        }
        return builder.build()
    }
}
