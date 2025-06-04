package sp.ax.blescanner

import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlin.coroutines.CoroutineContext

internal object MockEnvironment {
    val main: CoroutineContext = UnconfinedTestDispatcher()
    val default: CoroutineContext = main
    var scanner: BLEScanner? = null
}
