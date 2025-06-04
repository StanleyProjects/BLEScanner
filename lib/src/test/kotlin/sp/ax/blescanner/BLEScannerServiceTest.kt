package sp.ax.blescanner

import android.Manifest
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectIndexed
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.withIndex
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
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
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@RunWith(RobolectricTestRunner::class)
internal class BLEScannerServiceTest {
    @Test
    fun statesTest() {
        runTest(timeout = 6.seconds) {
            onScanner { scanner ->
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

    private suspend fun TestScope.onScanner(
        main: CoroutineContext = MockEnvironment.main,
        default: CoroutineContext = MockEnvironment.default,
        defaultState: BLEScanner.State = BLEScanner.State.Stopped,
        devices: List<BLEDevice> = emptyList(),
        block: suspend (BLEScanner) -> Unit,
    ) {
        val job = SupervisorJob()
        val scanner = MockScanner(
            coroutineScope = CoroutineScope(main + job),
            default = default,
            defaultState = defaultState,
            expected = devices,
        )
        block(scanner)
        job.cancel()
    }

    private suspend fun TestScope.onRealScanner(
        coroutineContext: CoroutineContext = this.coroutineContext,
        main: CoroutineContext = MockEnvironment.main,
        default: CoroutineContext = MockEnvironment.default,
        context: Context = RuntimeEnvironment.getApplication(),
        timeout: Duration = 5.seconds,
        block: suspend (BLEScanner) -> Unit,
    ) {
        val job = SupervisorJob()
        val scanner = RealBLEScanner(
            coroutineScope = CoroutineScope(coroutineContext + job + main),
            default = default,
            context = context,
            timeout = timeout,
        )
        block(scanner)
        job.cancel()
    }

    private inline fun <reified T : BLEScannerService> TestScope.onService(
        scanner: BLEScanner,
        context: Context = RuntimeEnvironment.getApplication(),
        block: (context: Context, controller: ServiceController<T>, intent: Intent) -> Unit,
    ) {
        MockEnvironment.scanner = scanner
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
        runTest(timeout = 6.seconds) {
            onScanner(
                main = coroutineContext,
                default = coroutineContext,
//                default = StandardTestDispatcher(testScheduler),
            ) { scanner ->
                onService<MockScannerService>(scanner = scanner) { context, controller, intent ->
                    launch(CoroutineName("states")) {
                        BLEScannerReceivers.states(context = context).take(3).collectIndexed { index, state ->
                            when (index) {
                                0 -> {
                                    assertEquals(BLEScanner.State.Stopped, state)
                                    intent.action = BLEScannerService.BLEScannerStartAction
                                    controller.startCommand(intent)
                                    testScheduler.advanceUntilIdle()
                                }
                                1 -> TODO("$index:$state")
                                1 -> assertEquals(BLEScanner.State.Starting, state)
                                2 -> TODO("$index:$state")
                                2 -> assertEquals(BLEScanner.State.Started, state)
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

    @Config(sdk = [Build.VERSION_CODES.TIRAMISU])
    @Test
    fun startTestApi33() {
        runTest(timeout = 10.seconds) {
            val application = RuntimeEnvironment.getApplication()
            val shadow = Shadows.shadowOf(application)
            shadow.grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
            check(application.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
            onScanner { scanner ->
                onService<MockScannerService>(scanner = scanner, context = application) { context, controller, intent ->
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
                        intent.action = BLEScannerService.BLEScannerStatesAction
                        controller.startCommand(intent)
                    }
                }
            }
        }
    }

    @Test
    fun stopTest() {
        runTest(timeout = 10.seconds) {
            onScanner { scanner ->
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
            onScanner(devices = devices) { scanner ->
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
            onScanner { scanner ->
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
//        val cc = EmptyCoroutineContext
        val cc = UnconfinedTestDispatcher()
//        val cc = StandardTestDispatcher(TestCoroutineScheduler())
//        runTest(MockEnvironment.main, timeout = 6.seconds) {
        runTest(cc, timeout = 6.seconds) {
            val application = RuntimeEnvironment.getApplication()
            val bm = application.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            Shadows.shadowOf(bm.adapter).setState(BluetoothAdapter.STATE_ON)
            check(bm.adapter.isEnabled)
//            Shadows.shadowOf(application).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
//            check(application.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
            onRealScanner(
                context = application,
                main = cc,
                default = cc + testScheduler,
            ) { scanner ->
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
