package sp.ax.blescanner

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build

internal fun Context.getBroadcast(state: BLEScanner.State): Intent {
    val broadcast = Intent(BLEScannerService.BLEScannerStatesAction)
    broadcast.setPackage(packageName) // https://stackoverflow.com/a/76920719/4398606
    broadcast.putExtra("state", state.name)
    return broadcast
}

internal fun Context.getBroadcast(error: Throwable): Intent {
    val broadcast = Intent(BLEScannerService.BLEScannerErrorsAction)
    broadcast.setPackage(packageName) // https://stackoverflow.com/a/76920719/4398606
    broadcast.putExtra("error", error)
    return broadcast
}

internal fun Context.getBroadcast(device: BLEDevice): Intent {
    val broadcast = Intent(BLEScannerService.BLEScannerDevicesAction)
    broadcast.setPackage(packageName) // https://stackoverflow.com/a/76920719/4398606
    broadcast.putExtra("name", device.name)
    broadcast.putExtra("address", device.address)
    broadcast.putExtra("bytes", device.bytes)
    return broadcast
}

internal fun register(
    context: Context,
    receivers: BroadcastReceiver,
    filters: IntentFilter,
    exported: Boolean = false,
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.registerReceiver(
            receivers,
            filters,
            if (exported) Context.RECEIVER_EXPORTED else Context.RECEIVER_NOT_EXPORTED,
        )
    } else {
        context.registerReceiver(receivers, filters)
    }
}
