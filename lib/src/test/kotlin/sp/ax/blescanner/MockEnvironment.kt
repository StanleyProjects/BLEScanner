package sp.ax.blescanner

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlin.coroutines.CoroutineContext

internal object MockEnvironment {
    val coroutineScope: CoroutineScope = TestScope()
    val main: CoroutineContext = UnconfinedTestDispatcher()
    val scanner: BLEScanner = MockScanner(
        coroutineScope = coroutineScope,
        defaultState = BLEScanner.State.Stopped,
    )
}
