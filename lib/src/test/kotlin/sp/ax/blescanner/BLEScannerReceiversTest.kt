package sp.ax.blescanner

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.flow.collectIndexed
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import kotlin.time.Duration.Companion.seconds

@RunWith(RobolectricTestRunner::class)
internal class BLEScannerReceiversTest {
    @Test
    fun statesTest() {
        runTest(timeout = 6.seconds) {
            val context: Context = MockContext(RuntimeEnvironment.getApplication())
            val states = BLEScanner.State.entries.toList()
            launch(CoroutineName("states")) {
                BLEScannerReceivers.states(context = context).take(states.size).collectIndexed { index, actual ->
                    if (index !in states.indices) error("Index $index is unexpected!")
                    assertEquals(states[index], actual)
                }
            }.join {
                val broadcast = Intent(BLEScannerService.BLEScannerStatesAction)
                broadcast.setPackage(context.packageName) // https://stackoverflow.com/a/76920719/4398606
                states.forEach { state ->
                    broadcast.putExtra("state", state.name)
                    context.sendBroadcast(broadcast)
                }
            }
        }
    }

    @Test
    fun errorsTest() {
        runTest(timeout = 6.seconds) {
            val context: Context = MockContext(RuntimeEnvironment.getApplication())
            val errors = listOf(
                IllegalStateException("1"),
                IllegalArgumentException("2"),
                IndexOutOfBoundsException("3"),
            )
            launch(CoroutineName("errors")) {
                BLEScannerReceivers.errors(context = context).take(errors.size).collectIndexed { index, actual ->
                    if (index !in errors.indices) error("Index $index is unexpected!")
                    assertEquals(errors[index], actual)
                }
            }.join {
                val broadcast = Intent(BLEScannerService.BLEScannerErrorsAction)
                broadcast.setPackage(context.packageName) // https://stackoverflow.com/a/76920719/4398606
                errors.forEach { error ->
                    broadcast.putExtra("error", error)
                    context.sendBroadcast(broadcast)
                }
            }
        }
    }

    @Test
    fun devicesTest() {
        runTest(timeout = 6.seconds) {
            val context: Context = MockContext(RuntimeEnvironment.getApplication())
            val devices = (1..3).map { number ->
                BLEDevice(
                    name = "name:$number",
                    address = "address:$number",
                    bytes = byteArrayOf(number.toByte()),
                )
            }
            launch(CoroutineName("devices")) {
                BLEScannerReceivers.devices(context = context).take(devices.size).collectIndexed { index, actual ->
                    if (index !in devices.indices) error("Index $index is unexpected!")
                    val expected = devices[index]
                    assertEquals(expected.name, actual.name)
                    assertEquals(expected.address, actual.address)
                    assertTrue(expected.bytes.contentEquals(actual.bytes))
                }
            }.join {
                val broadcast = Intent(BLEScannerService.BLEScannerDevicesAction)
                broadcast.setPackage(context.packageName) // https://stackoverflow.com/a/76920719/4398606
                devices.forEach { device ->
                    broadcast.putExtra("name", device.name)
                    broadcast.putExtra("address", device.address)
                    broadcast.putExtra("bytes", device.bytes)
                    context.sendBroadcast(broadcast)
                }
            }
        }
    }
}
