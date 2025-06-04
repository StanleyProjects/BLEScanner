package sp.ax.blescanner

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.seconds

internal class MockScanner(
    private val coroutineScope: CoroutineScope,
    private val default: CoroutineContext,
    defaultState: BLEScanner.State = BLEScanner.State.Stopped,
    private val expected: List<BLEDevice> = emptyList(),
) : BLEScanner {
    private val _states = MutableStateFlow<BLEScanner.State>(defaultState)
    override val states = _states.asStateFlow()
    private val _errors = MutableSharedFlow<Throwable>()
    override val errors = _errors.asSharedFlow()
    private val _devices = MutableSharedFlow<BLEDevice>()
    override val devices = _devices.asSharedFlow()

    override fun start() {
        coroutineScope.launch {
            withContext(default) {
                if (_states.value != BLEScanner.State.Stopped) TODO("MockScanner:start(${_states.value})")
                _states.value = BLEScanner.State.Starting
                _states.value = BLEScanner.State.Started
                for (device in expected) {
                    if (_states.value != BLEScanner.State.Started) break
                    _devices.emit(device)
                }
            }
        }
    }

    override fun stop() {
        coroutineScope.launch {
            withContext(default) {
                if (_states.value != BLEScanner.State.Started) TODO("MockScanner:start(${_states.value})")
                _states.value = BLEScanner.State.Stopping
                _states.value = BLEScanner.State.Stopped
            }
        }
    }
}
