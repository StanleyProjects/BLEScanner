package sp.ax.blescanner

import kotlinx.coroutines.flow.StateFlow

interface BLEScanner {
    val started: StateFlow<Boolean?>

    fun start()
    fun stop()
}
