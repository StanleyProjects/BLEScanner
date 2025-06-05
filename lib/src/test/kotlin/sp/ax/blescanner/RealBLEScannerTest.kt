package sp.ax.blescanner

import android.Manifest
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanRecord
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.toCollection
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
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
    private fun before(application: Application): BluetoothManager {
        val bm = application.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        Shadows.shadowOf(bm.adapter).setState(BluetoothAdapter.STATE_ON)
        check(bm.adapter.isEnabled)
        Shadows.shadowOf(application).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        check(application.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
        return bm
    }

    @Test
    fun startTest() {
        runTest(timeout = 6.seconds) {
            val application = RuntimeEnvironment.getApplication()
            before(application = application)
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
            before(application = application)
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

    private inline fun <reified T : Any> Class<out Any>.invoke(name: String, argument: Any): T {
        val method = methods.single { it.name == name }
        if (method == null) TODO()
        return method.invoke(null, argument) as T
    }

    @Test
    fun devicesTest() {
        runBlocking {
            withTimeout(6.seconds) {
                val application = RuntimeEnvironment.getApplication()
                val bm = before(application = application)
                val devices = (0..5).map { number ->
                    BLEDevice(
                        name = "name:$number",
                        address = "AA:BB:CC:DD:EE:F" + ('A'.code + number).toChar(),
                        bytes = byteArrayOf(number.toByte()),
                    )
                }.sortedBy { it.address }
                onRealBLEScanner(
                    main = coroutineContext,
                    context = application,
                ) { scanner ->
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
                                scanner.devices.take(devices.size).toCollection(ArrayList())
                                    .sortedBy { it.address }.forEachIndexed { index, actual ->
                                    if (index !in devices.indices) error("Index $index is unexpected!")
                                    val expected = devices[index]
                                    assertEquals(expected.name, actual.name)
                                    assertEquals(expected.address, actual.address)
                                    assertTrue(expected.bytes.contentEquals(actual.bytes))
                                }
                            }.join {
                                scanner.start()
                                devices.forEach { device ->
                                    val record: ScanRecord =
                                        ScanRecord::class.java.invoke<ScanRecord>(
                                            "parseFromBytes",
                                            device.bytes
                                        )
                                    val bd = bm.adapter.getRemoteDevice(device.address)
                                    Shadows.shadowOf(bd).setName(device.name)
                                    val result = ScanResult(bd, record, 1, 1)
                                    Shadows.shadowOf(bm.adapter.bluetoothLeScanner)
                                        .addScanResult(result)
                                }
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
}
