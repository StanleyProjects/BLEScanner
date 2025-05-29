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

    enum class Event {
        OnStop,
        OnStart,
    }

    val states: StateFlow<State>
    val events: SharedFlow<Event>
    val errors: SharedFlow<Throwable>

    fun start()
    fun stop()
}
