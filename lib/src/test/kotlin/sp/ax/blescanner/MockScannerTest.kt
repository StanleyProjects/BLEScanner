package sp.ax.blescanner

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
import kotlin.time.Duration.Companion.seconds

internal class MockScannerTest {
    @Test
    fun startTest() {
        runTest(timeout = 6.seconds) {
            onMockScanner { scanner ->
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
            onMockScanner { scanner ->
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
                        scanner.start()
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
