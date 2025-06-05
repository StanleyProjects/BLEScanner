package sp.ax.blescanner

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

internal suspend fun Job.join(delay: Duration = 1.seconds, preJoin: suspend () -> Unit) {
    delay(delay)
    preJoin()
    join()
}

internal suspend fun Job.cancel(delay: Duration = 1.seconds, preCancel: suspend () -> Unit) {
    delay(delay)
    preCancel()
    cancel()
}

internal suspend fun TestScope.onMockScanner(
    main: CoroutineContext = StandardTestDispatcher(testScheduler, "mock:scanner:main"),
    default: CoroutineContext = StandardTestDispatcher(testScheduler, "mock:scanner:default"),
    defaultState: BLEScanner.State = BLEScanner.State.Stopped,
    devices: List<BLEDevice> = emptyList(),
    block: suspend (BLEScanner) -> Unit,
) {
    val job = SupervisorJob()
    val scanner = MockScanner(
        coroutineScope = CoroutineScope(main + job),
        default = default + job,
        defaultState = defaultState,
        expected = devices,
    )
    block(scanner)
    job.cancel()
}

internal suspend fun TestScope.onRealBLEScanner(
    context: Context,
    timeout: Duration = 10.seconds,
    block: suspend (BLEScanner) -> Unit,
) {
    onRealBLEScanner(
        main = StandardTestDispatcher(testScheduler, "real:scanner:main"),
        default = StandardTestDispatcher(testScheduler, "real:scanner:main"),
        context = context,
        timeout = timeout,
        block = block,
    )
}

internal suspend fun onRealBLEScanner(
    main: CoroutineContext,
    default: CoroutineContext = main,
    context: Context,
    timeout: Duration = 10.seconds,
    block: suspend (BLEScanner) -> Unit,
) {
    val job = SupervisorJob()
    val scanner = RealBLEScanner(
        coroutineScope = CoroutineScope(main + job),
        default = default + job,
        context = context,
        timeout = timeout,
    )
    block(scanner)
    job.cancel()
}
