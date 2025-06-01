package sp.ax.blescanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

internal class BLEDeviceTest {
    @Test
    fun constructorTest() {
        val name = "foo"
        val address = "bar"
        val bytes = byteArrayOf(3, 2, 1)
        val actual = BLEDevice(
            name = name,
            address = address,
            bytes = bytes,
        )
        assertEquals(name, actual.name)
        assertEquals(address, actual.address)
        assertTrue(bytes.contentEquals(actual.bytes))
    }
}
