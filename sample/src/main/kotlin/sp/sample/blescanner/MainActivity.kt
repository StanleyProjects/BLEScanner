package sp.sample.blescanner

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import sp.ax.blescanner.BLEScanner
import sp.ax.blescanner.BLEScannerException
import sp.ax.blescanner.BLEScannerService

internal class MainActivity : ComponentActivity() {
    private var button: TextView? = null

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
            val context: Context = this
            val intent = Intent(context, ScannerService::class.java)
            intent.action = "start"
            startService(intent)
        }
    }

    private val gpsLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { _ ->
        val context: Context = this
        val lm = context.getSystemService(LocationManager::class.java)
        if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            val intent = Intent(context, ScannerService::class.java)
            intent.action = "start"
            startService(intent)
        }
    }

    private fun onStarted(started: Boolean?) {
        val context: Context = this
        val button = button ?: TODO()
        when (started) {
            true -> {
                button.setOnClickListener { _ ->
                    val intent = Intent(context, ScannerService::class.java)
                    intent.action = "stop"
                    startService(intent)
                }
                button.text = "stop"
            }
            false -> {
                button.setOnClickListener { _ ->
                    val intent = Intent(context, ScannerService::class.java)
                    intent.action = "start"
                    startService(intent)
                }
                button.text = "start"
            }
            null -> {
                button.setOnClickListener(null)
                button.text = "..."
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val context: Context = this
        val root = FrameLayout(context).also {
            it.layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }
        Button(context).also {
            it.layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER_VERTICAL,
            )
            button = it
            root.addView(it)
        }
        setContentView(root)
        val states = BLEScannerService.getStatesReceivers(context = context)
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                states.collect { state ->
                    val started = when (state) {
                        BLEScanner.State.Started -> true
                        else -> false
                    }
                    onStarted(started = started)
                }
            }
        }
        val errors = BLEScannerService.getErrorsReceivers(context = context)
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                errors.collect { error ->
                    when (error) {
                        is SecurityException -> requestPermissions()
                        is BLEScannerException -> {
                            when (error.type) {
                                BLEScannerException.Type.BTDisabled -> {
                                    if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                                        requestPermissions()
                                    } else {
                                        btLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                                    }
                                }
                                BLEScannerException.Type.GPSDisabled -> {
                                    gpsLauncher.launch(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                                }
                            }
                        }
                        else -> {
                            val message = """
                                scanner error: $error
                                message: ${error.message}
                                cause: ${error.cause}
                            """.trimIndent()
                            showToast(message = message)
                            println(message) // todo
                        }
                    }
                }
            }
        }
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                val intent = Intent(context, ScannerService::class.java)
                intent.action = "state"
                startService(intent)
            }
        }
    }
}
