package sp.ax.blescanner

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface BLEScanner {
    enum class State {
        Started,
        Starting,
        Stopping,
        Stopped,
    }

    val states: StateFlow<State>
    val errors: SharedFlow<Throwable>

    fun start()
    fun stop()
}
