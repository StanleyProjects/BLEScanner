package sp.sample.blescanner

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import sp.ax.blescanner.BLEScanner
import sp.ax.blescanner.BLEScannerException
import sp.ax.blescanner.BLEScannerReceivers
import sp.ax.blescanner.start
import sp.ax.blescanner.states
import sp.ax.blescanner.stop
import java.util.Locale

private fun getPermissions(): Array<String> {
    val permissions = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        permissions += Manifest.permission.POST_NOTIFICATIONS
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        permissions += Manifest.permission.BLUETOOTH_CONNECT
        permissions += Manifest.permission.BLUETOOTH_SCAN
    }
    return permissions.toTypedArray()
}

@Composable
internal fun MainScreen() {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val state = remember { BLEScannerReceivers.states(context = context) }
        .collectAsStateWithLifecycle(null, minActiveState = Lifecycle.State.RESUMED)
        .value
    val permissions = remember { getPermissions() }
    val permissionsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { output ->
        println("[MainScreen]:permissions: " + output.map { (k, v) -> "$k: $v" })
        val isGranted = permissions.all { permission ->
            context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
        }
        if (isGranted) start<ScannerService>(context = context)
    }
    val btLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { output ->
        if (output.resultCode == Activity.RESULT_OK) start<ScannerService>(context = context)
    }
    val gpsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { _ ->
        val lm = context.getSystemService(LocationManager::class.java)
        if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            start<ScannerService>(context = context)
        }
    }
    LaunchedEffect(Unit) {
        val errors = BLEScannerReceivers.errors(context = context)
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            errors.collect { error ->
                when (error) {
                    is SecurityException -> {
                        permissionsLauncher.launch(permissions)
                    }
                    is BLEScannerException -> {
                        when (error.type) {
                            BLEScannerException.Type.BTDisabled -> {
                                val isGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
                                } else {
                                    true
                                }
                                if (isGranted) {
                                    btLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                                } else {
                                    permissionsLauncher.launch(permissions)
                                }
                            }
                            BLEScannerException.Type.GPSDisabled -> {
                                gpsLauncher.launch(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                            }
                        }
                    }
                    else -> {
                        // todo
                    }
                }
            }
        }
    }
    LaunchedEffect(Unit) {
        val devices = BLEScannerReceivers.devices(context = context)
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            devices.collect { device ->
                val n1 = String.format(Locale.US, "%02x", device.bytes[13].toInt() and 0xff)
                val n2 = device.bytes[18].toInt() and 0xFF shl 8 or (device.bytes[19].toInt() and 0xFF)
                println("[MainScreen]:device: ${device.address} [13: $n1, $n2] ${device.name}")
            }
        }
    }
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
