package sp.sample.blescanner

import android.app.Application
import android.util.Log
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import sp.ax.blescanner.BLEScanner
import sp.ax.blescanner.BLEScannerLogger
import sp.ax.blescanner.RealBLEScanner
import kotlin.time.Duration.Companion.seconds

internal class App : Application() {
    private val job = SupervisorJob()
    private val coroutineScope: CoroutineScope

    init {
        val contexts = Contexts(
            main = Dispatchers.Main,
            default = Dispatchers.Default,
        )
        _contexts = contexts
        coroutineScope = CoroutineScope(contexts.main + job)
    }

    private object Logger : BLEScannerLogger {
        override fun warning(message: String) {
            Log.w("[Scanner]", message)
        }

        override fun debug(message: String) {
            Log.d("[Scanner]", message)
        }

        override fun info(message: String) {
            Log.i("[Scanner]", message)
        }
    }

    override fun onCreate() {
        super.onCreate()
        _scanner = RealBLEScanner(
            coroutineScope = coroutineScope,
            default = contexts.default,
            context = this,
            timeout = 3.seconds,
            logger = Logger,
        )
    }

    companion object {
        private var _contexts: Contexts? = null
        val contexts: Contexts get() {
            return checkNotNull(_contexts) { "No contexts!" }
        }

        private var _scanner: BLEScanner? = null
        val scanner: BLEScanner get() {
            return checkNotNull(_scanner) { "No BLE scanner!" }
        }

        val themes = MutableStateFlow<Theme>(Theme.Light)
    }
}
