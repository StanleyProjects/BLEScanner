package sp.ax.blescanner

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectIndexed
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import kotlin.time.Duration.Companion.seconds

@RunWith(RobolectricTestRunner::class)
internal class BLEScannerServiceTest {
    @Test
    fun testIntent() {
        runTest(timeout = 10.seconds) {
            val context: Context = RuntimeEnvironment.getApplication()
            val intent = Intent(context, MockScannerService::class.java)
            intent.action = BLEScannerService.BLEScannerStatesAction
            val controller = Robolectric.buildService(MockScannerService::class.java, intent)
            controller.create()
            val job = launch {
                BLEScannerReceivers.states(context = context).take(1).collectIndexed { index, state ->
                    when (index) {
                        0 -> assertEquals(BLEScanner.State.Stopped, state)
                        else -> TODO("BLEScannerServiceTest:testIntent($index)")
                    }
                }
            }
            delay(1.seconds)
            controller.startCommand(0, 0)
            job.join()
            controller.destroy()
        }
    }
}
