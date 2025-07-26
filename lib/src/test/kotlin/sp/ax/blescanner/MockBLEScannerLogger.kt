package sp.ax.blescanner

internal object MockBLEScannerLogger : BLEScannerLogger {
    override fun warning(message: String) {
        println("[Mock]: $message")
    }

    override fun debug(message: String) {
        println("[Mock]: $message")
    }

    override fun info(message: String) {
        println("[Mock]: $message")
    }
}
