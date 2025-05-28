package sp.sample.blescanner

import android.app.Application
import kotlinx.coroutines.Dispatchers

internal class App : Application() {
    override fun onCreate() {
        super.onCreate()
        _contexts = Contexts(
            main = Dispatchers.Main,
        )
    }

    companion object {
        private var _contexts: Contexts? = null
        val contexts: Contexts get() {
            return checkNotNull(_contexts) { "No contexts!" }
        }
    }
}
