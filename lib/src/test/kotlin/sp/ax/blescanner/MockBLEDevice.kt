package sp.ax.blescanner

internal fun mockBLEDevice(
    name: String = "mock:name",
    address: String = "mock:address",
    bytes: ByteArray = byteArrayOf(3, 2, 1),
): BLEDevice {
    return BLEDevice(
        name = name,
        address = address,
        bytes = bytes,
    )
}
