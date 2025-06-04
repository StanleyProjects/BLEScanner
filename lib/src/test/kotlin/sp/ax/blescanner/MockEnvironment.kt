package sp.ax.blescanner

import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

internal object MockEnvironment {
    val main: CoroutineContext = EmptyCoroutineContext
//    var main: CoroutineContext = EmptyCoroutineContext
//    val main: TestDispatcher = UnconfinedTestDispatcher()
//    val default: CoroutineContext = main
    val default: CoroutineContext = UnconfinedTestDispatcher()
    var scanner: BLEScanner? = null
}
