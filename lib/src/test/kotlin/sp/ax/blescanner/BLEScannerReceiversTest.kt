package sp.ax.blescanner

import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectIndexed
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowApplication
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.seconds

@RunWith(RobolectricTestRunner::class)
internal class BLEScannerReceiversTest {
    @Test
    fun startTest() {
        runTest(timeout = 6.seconds) {
            val context: Context = MockContext(RuntimeEnvironment.getApplication())
            launch(CoroutineName("start")) {
                BLEScannerReceivers.states(context = context).take(1).collectIndexed { index, state ->
                    System.err.println("$index:$state") // todo
                    when (index) {
                        0 -> assertEquals(BLEScanner.State.Started, state)
                        else -> error("Index $index is unexpected!")
                    }
                }
            }.join {
                val broadcast = Intent(BLEScannerService.BLEScannerStatesAction)
                broadcast.setPackage(context.packageName) // https://stackoverflow.com/a/76920719/4398606
                broadcast.putExtra("state", BLEScanner.State.Started.name)
                context.sendBroadcast(broadcast)
            }
        }
    }

    private class MockContext(base: Context) : ContextWrapper(base) {
        private val receivers = mutableMapOf<String, BroadcastReceiver>()

        override fun registerReceiver(
            receiver: BroadcastReceiver?,
            filter: IntentFilter?,
            flags: Int
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
            filter: IntentFilter?
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

    @Test
    fun fooTest() {
        runTest(timeout = 6.seconds) {
            val context: Context = RuntimeEnvironment.getApplication()
            val intentState = AtomicReference<Intent?>(null)
            val receivers = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    System.err.println("[BLEScannerReceiversTest]:fooTest:onReceive: $intent") // todo
                    intentState.set(intent)
                }
            }
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
            val broadcast = Intent(BLEScannerService.BLEScannerStatesAction)
            broadcast.setPackage(context.packageName) // https://stackoverflow.com/a/76920719/4398606
            broadcast.putExtra("state", BLEScanner.State.Started.name)
            context.sendBroadcast(broadcast)
            System.err.println("[BLEScannerReceiversTest]:fooTest:sendBroadcast: $broadcast") // todo
            launch {
                while (true) {
                    val intent = intentState.get()
                    if (intent != null) {
                        System.err.println("[BLEScannerReceiversTest]:fooTest:intent: $intent") // todo
                        break
                    }
                    delay(1.seconds)
                }
            }.join {
                context.unregisterReceiver(receivers)
            }
        }
    }

    @Test
    fun barTest() {
        runTest(timeout = 6.seconds) {
            onMockScanner { scanner ->
                MockEnvironment.scanner = scanner
                val controller = Robolectric.buildService(MockScannerService::class.java)
                controller.create()
                val service = controller.get()
                launch(CoroutineName("start")) {
                    BLEScannerReceivers.states(context = service).take(3).collectIndexed { index, state ->
                        System.err.println("$index:$state") // todo
                        when (index) {
                            0 -> TODO("$index:$state")
                            0 -> assertEquals(BLEScanner.State.Starting, state)
                            1 -> TODO("$index:$state")
                            1 -> assertEquals(BLEScanner.State.Stopped, state)
                            else -> error("Index $index is unexpected!")
                        }
                    }
                }.join {
                    val broadcast = Intent(BLEScannerService.BLEScannerStatesAction)
                    broadcast.setPackage(service.packageName) // https://stackoverflow.com/a/76920719/4398606
                    broadcast.putExtra("state", BLEScanner.State.Started.name)
                    service.sendBroadcast(broadcast)
                }
            }
        }
    }
}
