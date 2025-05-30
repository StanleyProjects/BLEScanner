package sp.sample.blescanner

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.produceState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import sp.ax.blescanner.BLEScanner
import sp.ax.blescanner.BLEScannerException
import sp.ax.blescanner.BLEScannerReceivers
import sp.ax.blescanner.BLEScannerService
import sp.ax.blescanner.start
import sp.ax.blescanner.states
import sp.ax.blescanner.stop

internal class MainActivity : ComponentActivity() {
    private fun requestPermissions() {
        val permissions = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions += Manifest.permission.POST_NOTIFICATIONS
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions += Manifest.permission.BLUETOOTH_CONNECT
            permissions += Manifest.permission.BLUETOOTH_SCAN
        }
        requestPermissions(permissions.toTypedArray(), 1)
    }

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

    @Composable
    private fun MainScreen() {
        val lifecycleOwner = LocalLifecycleOwner.current
        val context = LocalContext.current
        val state = remember { BLEScannerReceivers.states(context = context) }
            .collectAsStateWithLifecycle(null, minActiveState = Lifecycle.State.RESUMED)
            .value
        LaunchedEffect(Unit) {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                states<ScannerService>(context = context)
            }
        }
        Box(
            modifier = Modifier.fillMaxSize(),
        ) {
            val enabled = state == BLEScanner.State.Started || state == BLEScanner.State.Stopped
            val text = when (state) {
                BLEScanner.State.Started -> "stop"
                BLEScanner.State.Stopped -> "start"
                else -> "..."
            }
            BasicText(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clickable(enabled = enabled) {
                        when (state) {
                            BLEScanner.State.Started -> stop<ScannerService>(context = context)
                            BLEScanner.State.Stopped -> start<ScannerService>(context = context)
                            else -> {
                                // noop
                            }
                        }
                    }
                    .wrapContentSize()
                    .align(Alignment.Center),
                text = text,
            )
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
