package sp.ax.blescanner

import android.Manifest
import android.app.Application
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.flow.collectIndexed
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
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
import kotlin.time.Duration.Companion.seconds

@RunWith(RobolectricTestRunner::class)
internal class BLEScannerServiceTest {
    private inline fun <reified T : BLEScannerService> onService(
        scanner: BLEScanner,
        application: Application = RuntimeEnvironment.getApplication(),
        main: CoroutineContext = MockEnvironment.main,
        block: (context: Context, controller: ServiceController<T>, intent: Intent) -> Unit,
    ) {
        MockEnvironment.scanner = scanner
        MockEnvironment.main = main
        val context: Context = application
        val controller = Robolectric.buildService(T::class.java)
        controller.create()
        val intent = Intent(context, T::class.java)
        block(context, controller, intent)
    }

    private fun <T : Service> ServiceController<T>.startCommand(intent: Intent, flags: Int = 0, startId: Int = 0) {
        withIntent(intent).startCommand(flags, startId)
    }

    private fun before(application: Application) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val shadow = Shadows.shadowOf(application)
            shadow.grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
            check(application.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
        }
    }

    @Config(application = MockApplication::class, minSdk = Build.VERSION_CODES.P)
    @Test
    fun startTest() {
        runTest(timeout = 6.seconds) {
            onMockScanner { scanner ->
                val application = RuntimeEnvironment.getApplication()
                before(application = application)
                onService<MockScannerService>(scanner = scanner, application = application) { context, controller, intent ->
                    launch(CoroutineName("errors")) {
                        BLEScannerReceivers.errors(context = context).take(1).collect { error ->
                            error("Error $error is unexpected!")
                        }
                    }.cancel {
                        assertEquals("before start", BLEScanner.State.Stopped, scanner.states.value)
                        launch(CoroutineName("start")) {
                            BLEScannerReceivers.states(context = context).take(2).collectIndexed { index, state ->
                                when (index) {
                                    0 -> assertEquals(BLEScanner.State.Starting, state)
                                    1 -> assertEquals(BLEScanner.State.Started, state)
                                    else -> error("Index $index is unexpected!")
                                }
                            }
                        }.join {
                            intent.action = BLEScannerService.BLEScannerStartAction
                            controller.startCommand(intent)
                        }
                        assertEquals("after start", BLEScanner.State.Started, scanner.states.value)
                    }
                }
            }
        }
    }

    @Config(application = MockApplication::class, sdk = [Build.VERSION_CODES.S, Build.VERSION_CODES.TIRAMISU])
    @Test
    fun startStopTest() {
        runTest(timeout = 10.seconds) {
            onMockScanner { scanner ->
                val application = RuntimeEnvironment.getApplication()
                before(application = application)
                onService<MockScannerService>(scanner = scanner, application = application) { context, controller, intent ->
                    launch(CoroutineName("errors")) {
                        BLEScannerReceivers.errors(context = context).take(1).collect { error ->
                            error("Error $error is unexpected!")
                        }
                    }.cancel {
                        assertEquals("before start", BLEScanner.State.Stopped, scanner.states.value)
                        launch(CoroutineName("start")) {
                            BLEScannerReceivers.states(context = context).take(2).collectIndexed { index, state ->
                                when (index) {
                                    0 -> assertEquals(BLEScanner.State.Starting, state)
                                    1 -> assertEquals(BLEScanner.State.Started, state)
                                    else -> error("Index $index is unexpected!")
                                }
                            }
                        }.join {
                            intent.action = BLEScannerService.BLEScannerStartAction
                            controller.startCommand(intent)
                        }
                        assertEquals("after start", BLEScanner.State.Started, scanner.states.value)
                        launch(CoroutineName("stop")) {
                            BLEScannerReceivers.states(context = context).take(2).collectIndexed { index, state ->
                                when (index) {
                                    0 -> assertEquals(BLEScanner.State.Stopping, state)
                                    1 -> assertEquals(BLEScanner.State.Stopped, state)
                                    else -> error("Index $index is unexpected!")
                                }
                            }
                        }.join {
                            intent.action = BLEScannerService.BLEScannerStopAction
                            controller.startCommand(intent)
                        }
                        assertEquals("after stop", BLEScanner.State.Stopped, scanner.states.value)
                    }
                }
            }
        }
    }

    @Config(application = MockApplication::class, sdk = [Build.VERSION_CODES.S, Build.VERSION_CODES.TIRAMISU])
    @Test
    fun devicesTest() {
        runTest(timeout = 6.seconds) {
            val devices = (1..3).map { number ->
                BLEDevice(
                    name = "name:$number",
                    address = "address:$number",
                    bytes = byteArrayOf(number.toByte()),
                )
            }
            onMockScanner(devices = devices) { scanner ->
                val application = RuntimeEnvironment.getApplication()
                before(application = application)
                onService<MockScannerService>(scanner = scanner, application = application) { context, controller, intent ->
                    launch(CoroutineName("errors")) {
                        BLEScannerReceivers.errors(context = context).take(1).collect { error ->
                            error("Error $error is unexpected!")
                        }
                    }.cancel {
                        assertEquals("before start", BLEScanner.State.Stopped, scanner.states.value)
                        launch(CoroutineName("start")) {
                            BLEScannerReceivers.states(context = context).take(2).collectIndexed { index, state ->
                                when (index) {
                                    0 -> assertEquals(BLEScanner.State.Starting, state)
                                    1 -> assertEquals(BLEScanner.State.Started, state)
                                    else -> error("Index $index is unexpected!")
                                }
                            }
                        }.join {
                            launch(CoroutineName("devices")) {
                                BLEScannerReceivers.devices(context = context).take(devices.size).collectIndexed { index, actual ->
                                    if (index !in devices.indices) error("Index $index is unexpected!")
                                    val expected = devices[index]
                                    assertEquals(expected.name, actual.name)
                                    assertEquals(expected.address, actual.address)
                                    assertTrue(expected.bytes.contentEquals(actual.bytes))
                                }
                            }.join {
                                intent.action = BLEScannerService.BLEScannerStartAction
                                controller.startCommand(intent)
                            }
                        }
                        assertEquals("after start", BLEScanner.State.Started, scanner.states.value)
                        launch(CoroutineName("stop")) {
                            BLEScannerReceivers.states(context = context).take(2).collectIndexed { index, state ->
                                when (index) {
                                    0 -> assertEquals(BLEScanner.State.Stopping, state)
                                    1 -> assertEquals(BLEScanner.State.Stopped, state)
                                    else -> error("Index $index is unexpected!")
                                }
                            }
                        }.join {
                            intent.action = BLEScannerService.BLEScannerStopAction
                            controller.startCommand(intent)
                        }
                        assertEquals("after stop", BLEScanner.State.Stopped, scanner.states.value)
                    }
                }
            }
        }
    }

    @Config(application = MockApplication::class, sdk = [Build.VERSION_CODES.TIRAMISU])
    @Test
    fun errorsNotificationsTest() {
        runTest(timeout = 6.seconds) {
            onMockScanner { scanner ->
                val application = RuntimeEnvironment.getApplication()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    check(application.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                }
                onService<MockScannerService>(scanner = scanner, application = application) { context, controller, intent ->
                    assertEquals("before start", BLEScanner.State.Stopped, scanner.states.value)
                    launch(CoroutineName("start")) {
                        BLEScannerReceivers.states(context = context).take(1).collect { state ->
                            error("State $state is unexpected!")
                        }
                    }.cancel {
                        launch(CoroutineName("errors")) {
                            BLEScannerReceivers.errors(context = context).take(1).collectIndexed { index, error ->
                                when (index) {
                                    0 -> {
                                        assertTrue(error is SecurityException)
                                        assertEquals("no permission: android.permission.POST_NOTIFICATIONS", error.message)
                                    }
                                    else -> error("Index $index is unexpected!")
                                }
                            }
                        }.join {
                            intent.action = BLEScannerService.BLEScannerStartAction
                            controller.startCommand(intent)
                        }
                    }
                    assertEquals("after start", BLEScanner.State.Stopped, scanner.states.value)
                }
            }
        }
    }
}
