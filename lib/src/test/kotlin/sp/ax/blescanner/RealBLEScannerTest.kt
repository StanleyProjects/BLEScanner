package sp.ax.blescanner

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectIndexed
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration.Companion.seconds

@RunWith(RobolectricTestRunner::class)
internal class RealBLEScannerTest {
    @Test
    fun statesTest() {
        runTest(timeout = 10.seconds) {
            val application = RuntimeEnvironment.getApplication()
            onRealBLEScanner(context = application) { scanner ->
                assertEquals(BLEScanner.State.Stopped, scanner.states.value)
            }
        }
    }

    @Test
    fun startTest() {
        runTest(timeout = 6.seconds) {
            val application = RuntimeEnvironment.getApplication()
            val bm = application.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            Shadows.shadowOf(bm.adapter).setState(BluetoothAdapter.STATE_ON)
            check(bm.adapter.isEnabled)
            Shadows.shadowOf(application).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
            check(application.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
            onRealBLEScanner(context = application) { scanner ->
                launch {
                    scanner.errors.take(1).collect { error ->
                        error("Error $error is unexpected!")
                    }
                }.cancel {
                    launch {
                        scanner.states.take(3).collectIndexed { index, state ->
                            when (index) {
                                0 -> assertEquals("state: $index", BLEScanner.State.Stopped, state)
                                1 -> assertEquals("state: $index", BLEScanner.State.Starting, state)
                                2 -> assertEquals("state: $index", BLEScanner.State.Started, state)
                                else -> error("Index $index is unexpected!")
                            }
                        }
                    }.join {
                        scanner.start()
                    }
                }
            }
        }
    }
}
