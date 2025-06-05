package sp.ax.blescanner

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectIndexed
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import kotlin.time.Duration.Companion.seconds

@RunWith(RobolectricTestRunner::class)
internal class RealBLEScannerTest {
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
                launch(CoroutineName("errors")) {
                    scanner.errors.take(1).collect { error ->
                        error("Error $error is unexpected!")
                    }
                }.cancel {
                    assertEquals("before start", BLEScanner.State.Stopped, scanner.states.value)
                    launch(CoroutineName("before start")) {
                        scanner.states.takeWhile { state ->
                            state != BLEScanner.State.Started
                        }.collect()
                    }.join {
                        scanner.start()
                    }
                    assertEquals("after start", BLEScanner.State.Started, scanner.states.value)
                }
            }
        }
    }

    @Test
    fun startStopTest() {
        runTest(timeout = 6.seconds) {
            val application = RuntimeEnvironment.getApplication()
            val bm = application.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            Shadows.shadowOf(bm.adapter).setState(BluetoothAdapter.STATE_ON)
            check(bm.adapter.isEnabled)
            Shadows.shadowOf(application).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
            check(application.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
            onRealBLEScanner(context = application) { scanner ->
                launch(CoroutineName("errors")) {
                    scanner.errors.take(1).collect { error ->
                        error("Error $error is unexpected!")
                    }
                }.cancel {
                    assertEquals("before start", BLEScanner.State.Stopped, scanner.states.value)
                    launch(CoroutineName("before start")) {
                        scanner.states.takeWhile { state ->
                            state != BLEScanner.State.Started
                        }.collect()
                    }.join {
                        scanner.start()
                    }
                    assertEquals("after start", BLEScanner.State.Started, scanner.states.value)
                    launch(CoroutineName("after start")) {
                        scanner.states.takeWhile { state ->
                            state != BLEScanner.State.Stopped
                        }.collect()
                    }.join {
                        scanner.stop()
                    }
                }
            }
        }
    }

    @Test
    fun devicesTest() {
        runTest(timeout = 6.seconds) {
            val application = RuntimeEnvironment.getApplication()
            val devices = (1..3).map { number ->
                BLEDevice(
                    name = "name:$number",
                    address = "address:$number",
                    bytes = byteArrayOf(number.toByte()),
                )
            }
            onRealBLEScanner(context = application) { scanner ->
                launch(CoroutineName("errors")) {
                    scanner.errors.take(1).collect { error ->
                        error("Error $error is unexpected!")
                    }
                }.cancel {
                    assertEquals("before start", BLEScanner.State.Stopped, scanner.states.value)
                    launch(CoroutineName("start")) {
                        scanner.states.takeWhile { state ->
                            state != BLEScanner.State.Started
                        }.collect()
                    }.join {
                        launch(CoroutineName("devices")) {
                            scanner.devices.take(devices.size).collectIndexed { index, actual ->
                                if (index !in devices.indices) error("Index $index is unexpected!")
                                val expected = devices[index]
                                assertEquals(expected.name, actual.name)
                                assertEquals(expected.address, actual.address)
                                assertTrue(expected.bytes.contentEquals(actual.bytes))
                            }
                        }.join {
                            scanner.start()
                        }
                    }
                    assertEquals("after start", BLEScanner.State.Started, scanner.states.value)
                    launch(CoroutineName("stop")) {
                        scanner.states.takeWhile { state ->
                            state != BLEScanner.State.Stopped
                        }.collect()
                    }.join {
                        scanner.stop()
                    }
                }
            }
        }
    }
}
