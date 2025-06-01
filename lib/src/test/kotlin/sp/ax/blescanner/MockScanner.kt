package sp.ax.blescanner

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

internal class MockScanner(
    private val coroutineScope: CoroutineScope,
    defaultState: BLEScanner.State = BLEScanner.State.Stopped,
) : BLEScanner {
    private val _states = MutableStateFlow<BLEScanner.State>(defaultState)
    override val states = _states.asStateFlow()
    private val _errors = MutableSharedFlow<Throwable>()
    override val errors = _errors.asSharedFlow()
    private val _devices = MutableSharedFlow<BLEDevice>()
    override val devices = _devices.asSharedFlow()

    override fun start() {
        if (_states.value != BLEScanner.State.Stopped) TODO("MockScanner:start(${_states.value})")
        _states.value = BLEScanner.State.Starting
        _states.value = BLEScanner.State.Started
        coroutineScope.launch {
            var index = 0
            while (_states.value == BLEScanner.State.Started) {
                delay(250.milliseconds)
                _devices.emit(mockBLEDevice(name = "device:$index", address = "address:$index"))
                index = index.plus(1) % 10
            }
        }
    }

    override fun stop() {
        if (_states.value != BLEScanner.State.Started) TODO("MockScanner:start(${_states.value})")
        _states.value = BLEScanner.State.Stopping
        _states.value = BLEScanner.State.Stopped
    }
}
