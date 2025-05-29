package sp.ax.blescanner

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

abstract class BLEScannerService(
    main: CoroutineContext,
    private val scanner: BLEScanner,
    private val channel: NotificationChannel,
) : Service() {
    private val job = SupervisorJob()
    private val coroutineScope = CoroutineScope(main + job)
    private val N_ID: Int = System.currentTimeMillis().toInt()

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(channel.id) == null) {
            nm.createNotificationChannel(channel)
        }
        coroutineScope.launch {
            scanner.states.drop(1).collect { state ->
                val broadcast = Intent("scanner:state")
                broadcast.setPackage(packageName) // https://stackoverflow.com/a/76920719/4398606
                broadcast.putExtra("state", state.name)
                sendBroadcast(broadcast)
                when (state) {
                    BLEScanner.State.Started -> {
                        val notification = onStartNotification(channel = channel)
                        nm.notify(N_ID, notification)
                        startForeground(N_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
                    }
                    BLEScanner.State.Starting -> {
                        // noop
                    }
                    BLEScanner.State.Stopping -> {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                    }
                    BLEScanner.State.Stopped -> {
                        stopSelf()
                    }
                }
            }
        }
        coroutineScope.launch {
            scanner.errors.collect { error ->
                val broadcast = Intent("scanner:errors")
                broadcast.setPackage(packageName) // https://stackoverflow.com/a/76920719/4398606
                broadcast.putExtra("name", error::class.java.name)
                broadcast.putExtra("message", error.message)
                if (error is BLEScannerException) {
                    broadcast.putExtra("type", error.type.name)
                }
                sendBroadcast(broadcast)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        println("[BLEScannerService]:onStartCommand($intent)") // todo
        when (intent?.action) {
            "start" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    val error = SecurityException("no permission: ${Manifest.permission.POST_NOTIFICATIONS}")
                    val broadcast = Intent("scanner:errors")
                    broadcast.setPackage(packageName) // https://stackoverflow.com/a/76920719/4398606
                    broadcast.putExtra("name", error::class.java.name)
                    broadcast.putExtra("message", error.message)
                    sendBroadcast(broadcast)
                } else {
                    scanner.start()
                }
            }
            "stop" -> scanner.stop()
            "state" -> {
                val broadcast = Intent("scanner:state")
                broadcast.setPackage(packageName) // https://stackoverflow.com/a/76920719/4398606
                broadcast.putExtra("state", scanner.states.value.name)
                sendBroadcast(broadcast)
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }

    protected abstract fun onStartNotification(channel: NotificationChannel): Notification
}
