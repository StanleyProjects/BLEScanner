package sp.ax.blescanner

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

class RealBLEScanner(
    private val coroutineScope: CoroutineScope,
    private val default: CoroutineContext,
) : BLEScanner {
    private val _started = MutableStateFlow<Boolean?>(false)
    override val started = _started.asStateFlow()

    override fun start() {
        coroutineScope.launch {
            _started.value = null
            withContext(default) {
                delay(1_000)
            }
            _started.value = true
        }
    }

    override fun stop() {
        coroutineScope.launch {
            _started.value = null
            withContext(default) {
                delay(1_000)
            }
            _started.value = false
        }
    }
}
