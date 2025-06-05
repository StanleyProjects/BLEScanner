package sp.ax.blescanner

import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlin.coroutines.CoroutineContext

internal object MockEnvironment {
    var main: CoroutineContext = UnconfinedTestDispatcher()
    val default: CoroutineContext = UnconfinedTestDispatcher()
    var scanner: BLEScanner? = null
}
