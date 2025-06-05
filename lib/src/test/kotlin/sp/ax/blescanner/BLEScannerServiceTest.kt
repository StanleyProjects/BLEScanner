package sp.ax.blescanner

import android.Manifest
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectIndexed
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.time.Duration.Companion.seconds

@RunWith(RobolectricTestRunner::class)
internal class BLEScannerServiceTest {
    @Test
    fun statesTest() {
        runTest(timeout = 6.seconds) {
            onMockScanner { scanner ->
                onService<MockScannerService>(scanner = scanner) { context, controller, intent ->
                    launch {
                        BLEScannerReceivers.states(context = context).take(1).collectIndexed { index, state ->
                            when (index) {
                                0 -> assertEquals(BLEScanner.State.Stopped, state)
                                else -> error("Index $index is unexpected!")
                            }
                        }
                    }.join {
                        intent.action = BLEScannerService.BLEScannerStatesAction
                        controller.startCommand(intent)
                    }
                }
            }
        }
    }

    private inline fun <reified T : BLEScannerService> onService(
        scanner: BLEScanner,
        main: CoroutineContext = MockEnvironment.main,
        context: Context = RuntimeEnvironment.getApplication(),
        block: (context: Context, controller: ServiceController<T>, intent: Intent) -> Unit,
    ) {
        MockEnvironment.scanner = scanner
        MockEnvironment.main = main
        val controller = Robolectric.buildService(T::class.java)
        controller.create()
        val intent = Intent(context, T::class.java)
        block(context, controller, intent)
    }

    private fun <T : Service> ServiceController<T>.startCommand(intent: Intent, flags: Int = 0, startId: Int = 0) {
        withIntent(intent).startCommand(flags, startId)
    }

    @Config(sdk = [Build.VERSION_CODES.S])
    @Test
    fun startTest() {
        runTest(StandardTestDispatcher(), timeout = 6.seconds) {
            onMockScanner() { scanner ->
                onService<MockScannerService>(scanner = scanner, main = StandardTestDispatcher()) { context, controller, intent ->
                    launch(CoroutineName("BLEScannerServiceTest:startTest:errors")) {
                        BLEScannerReceivers.errors(context = context).take(1).collect { error ->
                            error("Error $error is unexpected!")
                        }
                    }.cancel {
                        launch(CoroutineName("BLEScannerServiceTest:startTest:states")) {
                            BLEScannerReceivers.states(context = context).takeWhile { state ->
                                state != BLEScanner.State.Started
                            }.collect()
                        }.join {
                            intent.action = BLEScannerService.BLEScannerStartAction
                            controller.startCommand(intent)
                        }
                    }
                }
            }
        }
    }

    @Config(sdk = [Build.VERSION_CODES.TIRAMISU])
    @Test
    fun startTestApi33() {
        runTest(timeout = 10.seconds) {
            val application = RuntimeEnvironment.getApplication()
            val shadow = Shadows.shadowOf(application)
            shadow.grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
            check(application.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
            onMockScanner { scanner ->
                onService<MockScannerService>(scanner = scanner, context = application) { context, controller, intent ->
                    launch(CoroutineName("BLEScannerServiceTest:startTest:errors")) {
                        BLEScannerReceivers.errors(context = context).take(1).collect { error ->
                            error("Error $error is unexpected!")
                        }
                    }.cancel {
                        launch(CoroutineName("BLEScannerServiceTest:startTest:states")) {
                            BLEScannerReceivers.states(context = context).takeWhile { state ->
                                state != BLEScanner.State.Started
                            }.collect()
                        }.join {
                            intent.action = BLEScannerService.BLEScannerStartAction
                            controller.startCommand(intent)
                        }
                    }
                }
            }
        }
    }

    @Test
    fun stopTest() {
        runTest(timeout = 10.seconds) {
            onMockScanner { scanner ->
                onService<MockScannerService>(scanner = scanner) { context, controller, intent ->
                    launch {
                        BLEScannerReceivers.states(context = context).take(5).collectIndexed { index, state ->
                            when (index) {
                                0 -> {
                                    assertEquals(BLEScanner.State.Stopped, state)
                                    intent.action = BLEScannerService.BLEScannerStartAction
                                    controller.startCommand(intent)
                                }
                                1 -> assertEquals(BLEScanner.State.Starting, state)
                                2 -> {
                                    assertEquals(BLEScanner.State.Started, state)
                                    delay(1.seconds)
                                    intent.action = BLEScannerService.BLEScannerStopAction
                                    controller.startCommand(intent)
                                }
                                3 -> assertEquals(BLEScanner.State.Stopping, state)
                                4 -> assertEquals(BLEScanner.State.Stopped, state)
                                else -> error("Index $index is unexpected!")
                            }
                        }
                    }.join {
                        intent.action = BLEScannerService.BLEScannerStatesAction
                        controller.startCommand(intent)
                    }
                }
            }
        }
    }

    @Test
    fun devicesTest() {
        runTest(timeout = 10.seconds) {
            val devices = (1..3).map { number ->
                BLEDevice(name = "name$number", address = "address$number", byteArrayOf(number.toByte()))
            }
            onMockScanner(devices = devices) { scanner ->
                onService<MockScannerService>(scanner = scanner) { context, controller, intent ->
                    launch {
                        BLEScannerReceivers.states(context = context).take(3).collectIndexed { index, state ->
                            when (index) {
                                0 -> {
                                    assertEquals(BLEScanner.State.Stopped, state)
                                    intent.action = BLEScannerService.BLEScannerStartAction
                                    controller.startCommand(intent)
                                }
                                1 -> assertEquals(BLEScanner.State.Starting, state)
                                2 -> assertEquals(BLEScanner.State.Started, state)
                                else -> error("Index $index is unexpected!")
                            }
                        }
                    }.join {
                        launch {
                            BLEScannerReceivers.devices(context = context).take(devices.size).collectIndexed { index, actual ->
                                if (index !in devices.indices) error("Index $index is unexpected!")
                                val expected = devices[index]
                                assertEquals(expected.name, actual.name)
                                assertEquals(expected.address, actual.address)
                                assertTrue(expected.bytes.contentEquals(actual.bytes))
                            }
                        }.join {
                            intent.action = BLEScannerService.BLEScannerStatesAction
                            controller.startCommand(intent)
                        }
                    }
                }
            }
        }
    }

    @Config(sdk = [Build.VERSION_CODES.TIRAMISU])
    @Test
    fun permissionsTest() {
        runTest(timeout = 10.seconds) {
            val application = RuntimeEnvironment.getApplication()
            check(application.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            onMockScanner { scanner ->
                onService<MockScannerService>(scanner = scanner, context = application) { context, controller, intent ->
                    launch {
                        BLEScannerReceivers.states(context = context).take(1).collectIndexed { index, state ->
                            when (index) {
                                0 -> {
                                    assertEquals(BLEScanner.State.Stopped, state)
                                    intent.action = BLEScannerService.BLEScannerStartAction
                                    controller.startCommand(intent)
                                }
                                else -> error("Index $index is unexpected!")
                            }
                        }
                    }.join {
                        launch {
                            BLEScannerReceivers.errors(context = context).take(1).collectIndexed { index, actual ->
                                when (index) {
                                    0 -> {
                                        check(actual is SecurityException)
                                        assertEquals(actual.message, "no permission: ${Manifest.permission.POST_NOTIFICATIONS}")
                                    }
                                    else -> error("Index $index is unexpected!")
                                }
                            }
                        }.join {
                            intent.action = BLEScannerService.BLEScannerStatesAction
                            controller.startCommand(intent)
                        }
                    }
                }
            }
        }
    }

    @Test
    fun realScannerTest() {
        runTest(timeout = 6.seconds) {
            val application = RuntimeEnvironment.getApplication()
            val bm = application.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            Shadows.shadowOf(bm.adapter).setState(BluetoothAdapter.STATE_ON)
            check(bm.adapter.isEnabled)
//            Shadows.shadowOf(application).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
//            check(application.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
            onRealBLEScanner(context = application) { scanner ->
                onService<MockScannerService>(scanner = scanner, context = application) { context, controller, intent ->
                    launch {
                        BLEScannerReceivers.errors(context = context).collect { actual ->
                            error("Error $actual is unexpected!")
                        }
                    }.cancel {
                        intent.action = BLEScannerService.BLEScannerStartAction
                        controller.startCommand(intent)
                    }
//                    launch {
//                        BLEScannerReceivers.errors(context = context).collect { actual ->
//                            error("Error $actual is unexpected!")
//                        }
//                    }.cancel {
//                        launch {
//                            BLEScannerReceivers.states(context = context).take(1).collectIndexed { index, state ->
//                                TODO("$index:$state")
//                                state != BLEScanner.State.Started
//                            }
//                        }.join {
//                            intent.action = BLEScannerService.BLEScannerStartAction
//                            controller.startCommand(intent)
//                        }
//                    }
                    launch {
                        BLEScannerReceivers.states(context = context).take(1).collectIndexed { index, state ->
                            TODO("$index:$state")
                            state != BLEScanner.State.Started
                        }
                    }.join {
                        intent.action = BLEScannerService.BLEScannerStartAction
                        controller.startCommand(intent)
                    }
                }
            }
        }
    }
}
