package sp.ax.blescanner

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.onFailure
import kotlinx.coroutines.channels.onSuccess
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.internal.ChannelFlow
import kotlin.time.Duration.Companion.milliseconds

object BLEScannerReceivers {
    fun states(context: Context): Flow<BLEScanner.State> {
        return callbackFlow {
            System.err.println("[BLEScannerReceivers:states]:flow...") // todo
            val receivers = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    System.err.println("[BLEScannerReceivers:states]:intent: ${intent?.action} state: ${intent?.getStringExtra("state")}") // todo
                    val state = intent?.getStringExtra("state")?.let { name ->
                        BLEScanner.State.entries.firstOrNull { it.name == name }
                    } ?: return
                    trySend(state)
                        .onSuccess { value ->
                            System.err.println("[BLEScannerReceivers:states]:success:send: $value") // todo
                        }
                        .onFailure { error ->
                            System.err.println("[BLEScannerReceivers:states]:failure:send: $error") // todo
                        }
                }
            }
            val timeStart = System.currentTimeMillis()
            val filters = IntentFilter(BLEScannerService.BLEScannerStatesAction)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(
                    receivers,
                    filters,
                    Context.RECEIVER_NOT_EXPORTED,
                )
            } else {
                context.registerReceiver(receivers, filters)
            }
            System.err.println("[BLEScannerReceivers:states]:register:receivers: $receivers") // todo
            awaitClose {
                val timeNow = System.currentTimeMillis()
                System.err.println("[BLEScannerReceivers:states]:close(${(timeNow - timeStart).milliseconds})...") // todo
                context.unregisterReceiver(receivers)
            }
        }
    }

    fun errors(context: Context): Flow<Throwable> {
        return callbackFlow {
            val receivers = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    val error = intent?.getSerializableExtra("error") as? Throwable ?: return
                    trySend(error)
                }
            }
            val filters = IntentFilter(BLEScannerService.BLEScannerErrorsAction)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(
                    receivers,
                    filters,
                    Context.RECEIVER_NOT_EXPORTED,
                )
            } else {
                context.registerReceiver(receivers, filters)
            }
            awaitClose {
                context.unregisterReceiver(receivers)
            }
        }
    }

    fun devices(context: Context): Flow<BLEDevice> {
        return callbackFlow {
            val receivers = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    val device = BLEDevice(
                        name = intent?.getStringExtra("name") ?: return,
                        address = intent.getStringExtra("address") ?: return,
                        bytes = intent.getByteArrayExtra("bytes") ?: return,
                    )
                    trySend(device)
                }
            }
            val filters = IntentFilter(BLEScannerService.BLEScannerDevicesAction)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(
                    receivers,
                    filters,
                    Context.RECEIVER_NOT_EXPORTED,
                )
            } else {
                context.registerReceiver(receivers, filters)
            }
            awaitClose {
                context.unregisterReceiver(receivers)
            }
        }
    }
}
