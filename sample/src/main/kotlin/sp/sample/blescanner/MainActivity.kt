package sp.sample.blescanner

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import sp.ax.blescanner.BLEScanner

internal class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scanner: BLEScanner? = null // todo
        val context: Context = this
        val intent = Intent(context, ScannerService::class.java)
//        intent.action = "stop" // todo
        context.startService(intent)
    }
}
