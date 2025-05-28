package sp.ax.blescanner

import android.app.Service
import android.content.Intent
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlin.coroutines.CoroutineContext

abstract class BLEScannerService(
    context: CoroutineContext,
) : Service() {
    private val job = SupervisorJob()
    private val coroutineScope = CoroutineScope(context + job)

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }
}
