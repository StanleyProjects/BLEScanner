package sp.ax.blescanner

import android.content.Context
import android.content.Intent

inline fun <reified T : BLEScannerService> start(context: Context) {
    val intent = Intent(context, T::class.java)
    intent.action = BLEScannerService.BLEScannerStartAction
    context.startService(intent)
}

inline fun <reified T : BLEScannerService> stop(context: Context) {
    val intent = Intent(context, T::class.java)
    intent.action = BLEScannerService.BLEScannerStopAction
    context.startService(intent)
}

inline fun <reified T : BLEScannerService> states(context: Context) {
    val intent = Intent(context, T::class.java)
    intent.action = BLEScannerService.BLEScannerStatesAction
    context.startService(intent)
}
