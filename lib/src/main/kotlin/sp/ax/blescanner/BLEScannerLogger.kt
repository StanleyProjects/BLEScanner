package sp.ax.blescanner

interface BLEScannerLogger {
    fun warning(message: String)
    fun debug(message: String)
    fun info(message: String)
}
