package sp.ax.blescanner

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter

internal class MockApplication : Application() {
    private val receivers = mutableMapOf<String, BroadcastReceiver>()

    override fun getBaseContext(): Context {
        return this // todo
    }

    override fun registerReceiver(
        receiver: BroadcastReceiver?,
        filter: IntentFilter?,
        flags: Int,
    ): Intent? {
        if (filter == null) TODO()
        if (filter.countActions() != 1) TODO()
        val action = filter.actionsIterator().next()
        if (receiver == null) TODO()
        receivers[action] = receiver
        return null
    }

    override fun registerReceiver(
        receiver: BroadcastReceiver?,
        filter: IntentFilter?,
    ): Intent? {
        if (filter == null) TODO()
        if (filter.countActions() != 1) TODO()
        val action = filter.actionsIterator().next()
        if (receiver == null) TODO()
        receivers[action] = receiver
        return null
    }

    override fun sendBroadcast(intent: Intent?) {
        if (intent == null) TODO()
        if (intent.getPackage() != packageName) TODO()
        val receiver = receivers[intent.action] ?: TODO()
        receiver.onReceive(this, intent)
    }

    override fun unregisterReceiver(receiver: BroadcastReceiver?) {
        for ((action, actual) in receivers) {
            if (actual === receiver) {
                receivers.remove(action)
                return
            }
        }
        TODO()
    }
}
