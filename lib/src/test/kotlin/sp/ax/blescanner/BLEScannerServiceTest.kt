package sp.ax.blescanner

import android.app.Service
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectIndexed
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.android.controller.ServiceController
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@RunWith(RobolectricTestRunner::class)
internal class BLEScannerServiceTest {
    @Test
    fun statesTest() {
        runTest(timeout = 10.seconds) {
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

    private suspend fun Job.join(delay: Duration = 2.seconds, preJoin: () -> Unit) {
        delay(delay)
        preJoin()
        join()
    }

    private suspend fun TestScope.onScanner(
        devices: List<BLEDevice> = emptyList(),
        coroutineContext: CoroutineContext = this.coroutineContext,
        default: CoroutineContext = MockEnvironment.default,
        defaultState: BLEScanner.State = BLEScanner.State.Stopped,
        block: suspend (BLEScanner) -> Unit,
    ) {
        val job = SupervisorJob()
        val scanner = MockScanner(
            coroutineScope = CoroutineScope(coroutineContext + job + default),
            defaultState = defaultState,
            expected = devices,
        )
        block(scanner)
        job.cancel()
    }

    private inline fun <reified T : BLEScannerService> onService(
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

    @Test
    fun startTest() {
        runTest(timeout = 10.seconds) {
            onScanner { scanner ->
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
                    val job = launch {
                        BLEScannerReceivers.devices(context = context).take(devices.size).collectIndexed { index, actual ->
                            if (index !in devices.indices) error("Index $index is unexpected!")
                            val expected = devices[index]
                            assertEquals(expected.name, actual.name)
                            assertEquals(expected.address, actual.address)
                            assertTrue(expected.bytes.contentEquals(actual.bytes))
                        }
                    }
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
                    job.join()
                }
            }
        }
    }
}
