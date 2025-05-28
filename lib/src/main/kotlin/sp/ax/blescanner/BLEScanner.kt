package sp.ax.blescanner

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface BLEScanner {
    val started: StateFlow<Boolean?>
    val errors: SharedFlow<Throwable>

    fun start()
    fun stop()
}
