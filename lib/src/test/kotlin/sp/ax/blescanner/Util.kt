package sp.ax.blescanner

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
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

@Deprecated(message = "TestScope.onMockScanner")
internal suspend fun onMockScannerOld(
    main: CoroutineContext = MockEnvironment.main,
    default: CoroutineContext = MockEnvironment.default,
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

internal suspend fun TestScope.onMockScanner(
    defaultState: BLEScanner.State = BLEScanner.State.Stopped,
    devices: List<BLEDevice> = emptyList(),
    block: suspend (BLEScanner) -> Unit,
) {
    val job = SupervisorJob()
    val main = StandardTestDispatcher(testScheduler, "main")
    val default = StandardTestDispatcher(testScheduler, "default")
    val scanner = MockScanner(
        coroutineScope = CoroutineScope(main + job),
        default = default + job,
        defaultState = defaultState,
        expected = devices,
    )
    block(scanner)
    job.cancel()
}

@Deprecated(message = "TestScope.onRealBLEScanner")
internal suspend fun onRealBLEScannerOld(
    main: CoroutineContext = EmptyCoroutineContext,
    default: CoroutineContext = MockEnvironment.default,
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

internal suspend fun TestScope.onRealBLEScanner(
    context: Context,
    timeout: Duration = 10.seconds,
    block: suspend (BLEScanner) -> Unit,
) {
    val job = SupervisorJob()
    val main = StandardTestDispatcher(testScheduler, "main")
    val default = StandardTestDispatcher(testScheduler, "default")
    val scanner = RealBLEScanner(
        coroutineScope = CoroutineScope(main + job),
        default = default + job,
        context = context,
        timeout = timeout,
    )
    block(scanner)
    job.cancel()
}
