package sp.sample.blescanner

import android.Manifest
import android.content.Context
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.ComposeView
import sp.ax.blescanner.start

internal class MainActivity : ComponentActivity() {
    private val btLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { output ->
        if (output.resultCode == RESULT_OK) {
            start<ScannerService>(context = this)
        }
    }

    private val gpsLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { _ ->
        val context: Context = this
        val lm = context.getSystemService(LocationManager::class.java)
        if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            start<ScannerService>(context = context)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val view = ComposeView(this)
        setContentView(view)
        view.setContent {
            MainScreen()
        }
    }
}
