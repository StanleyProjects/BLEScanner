package sp.ax.blescanner

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

object BLEScannerReceivers {
    fun states(context: Context): Flow<BLEScanner.State> {
        return callbackFlow {
            val receivers = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    val state = intent?.getStringExtra("state")?.let { name ->
                        BLEScanner.State.entries.firstOrNull { it.name == name }
                    } ?: return
                    trySend(state)
                }
            }
            val filters = IntentFilter(BLEScannerService.BLEScannerStatesAction)
            register(context, receivers, filters)
            awaitClose {
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
            register(context, receivers, filters)
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
            register(context, receivers, filters)
            awaitClose {
                context.unregisterReceiver(receivers)
            }
        }
    }

    private fun register(context: Context, receivers: BroadcastReceiver, filters: IntentFilter) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(
                receivers,
                filters,
                Context.RECEIVER_NOT_EXPORTED,
            )
        } else {
            context.registerReceiver(receivers, filters)
        }
    }
}
