package sp.ax.blescanner

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
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

internal suspend fun onRealBLEScanner(
    main: CoroutineContext = MockEnvironment.main,
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
