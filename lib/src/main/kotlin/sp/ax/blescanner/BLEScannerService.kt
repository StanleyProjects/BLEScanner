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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

abstract class BLEScannerService(
    main: CoroutineContext,
    private val scanner: BLEScanner,
    protected val channel: NotificationChannel,
) : Service() {
    private val job = SupervisorJob()
    protected val coroutineScope = CoroutineScope(main + job)
    private val N_ID: Int = System.currentTimeMillis().toInt()
    protected val states: StateFlow<BLEScanner.State> get() = scanner.states

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(channel.id) == null) {
            nm.createNotificationChannel(channel)
        }
        coroutineScope.launch {
            scanner.states.drop(1).collect { state ->
                sendBroadcast(getBroadcast(state = state))
                when (state) {
                    BLEScanner.State.Started -> {
                        val notification = onStartNotification()
                        nm.notify(N_ID, notification)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            startForeground(N_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
                        } else {
                            startForeground(N_ID, notification)
                        }
                    }
                    BLEScanner.State.Stopped -> {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        nm.cancel(N_ID)
                        stopSelf()
                    }
                    else -> {
                        // noop
                    }
                }
            }
        }
        coroutineScope.launch {
            scanner.errors.collect { error ->
                sendBroadcast(getBroadcast(error = error))
            }
        }
        coroutineScope.launch {
            scanner.devices.collect { device ->
                sendBroadcast(getBroadcast(device = device))
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            BLEScannerStartAction -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    sendBroadcast(getBroadcast(error = SecurityException("no permission: ${Manifest.permission.POST_NOTIFICATIONS}")))
                } else {
                    scanner.start()
                }
            }
            BLEScannerStopAction -> scanner.stop()
            BLEScannerStatesAction -> {
                sendBroadcast(getBroadcast(state = scanner.states.value))
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }

    protected abstract fun onStartNotification(): Notification

    protected fun notify(notification: Notification) {
        if (scanner.states.value == BLEScanner.State.Stopped) return
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(N_ID, notification)
    }

    companion object {
        const val BLEScannerStatesAction = "sp.ax.blescanner.BLEScannerStatesAction"
        const val BLEScannerErrorsAction = "sp.ax.blescanner.BLEScannerErrorsAction"
        const val BLEScannerDevicesAction = "sp.ax.blescanner.BLEScannerDevicesAction"
        const val BLEScannerStartAction = "sp.ax.blescanner.BLEScannerStartAction"
        const val BLEScannerStopAction = "sp.ax.blescanner.BLEScannerStopAction"
    }
}
