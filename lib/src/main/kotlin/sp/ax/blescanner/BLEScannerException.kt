package sp.ax.blescanner

class BLEScannerException(val type: Type) : Exception(type.name) {
    enum class Type {
        BTDisabled,
        GPSDisabled,
    }

    override fun toString(): String {
        return "BLEScannerException($type)"
    }
}
