package sp.ax.blescanner

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface BLEScanner {
    enum class State {
        Stopped,
        Starting,
        Stopping,
        Started,
    }

    val states: StateFlow<State>
    val errors: SharedFlow<Throwable>
    val devices: SharedFlow<BLEDevice>

    fun start()
    fun stop()
}
