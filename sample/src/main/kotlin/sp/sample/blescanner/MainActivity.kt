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
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import sp.ax.blescanner.BLEScanner
import sp.ax.blescanner.BLEScannerException

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

    private val filters = IntentFilter().also {
        it.addAction("scanner:state")
        it.addAction("scanner:errors")
    }
    private val receivers = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            println("[MainActivity]:receivers:onReceive($intent)")
            when (intent?.action) {
                "scanner:state" -> {
                    val started = when (intent.getStringExtra("state")) {
                        BLEScanner.State.Started.name -> true
                        else -> false
                    }
                    onStarted(started = started)
                }
                "scanner:errors" -> {
                    when (val name = intent.getStringExtra("name")) {
                        SecurityException::class.java.name -> {
                            requestPermissions()
                        }
                        BLEScannerException::class.java.name -> {
                            when (BLEScannerException.Type.valueOf(intent.getStringExtra("type")!!)) {
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
                                scanner error:
                                name: $name
                                message: ${intent.getStringExtra("message")}
                            """.trimIndent()
                            showToast(message = message)
                            println(message) // todo
                        }
                    }
                }
            }
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

    private val intents = callbackFlow<Intent> {
        val context: Context = this@MainActivity
        val receivers = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                println("[MainActivity]:intents:onReceive($intent)")
                if (intent != null) trySendBlocking(intent)
            }
        }
        println("[MainActivity]:registerReceiver($receivers)")
        ContextCompat.registerReceiver(
            context,
            receivers,
            filters,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        awaitClose {
            context.unregisterReceiver(receivers)
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
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                intents.collect { intent ->
                    println("[MainActivity]:intents:onEach($intent)")
                    when (intent.action) {
                        "scanner:state" -> {
                            val started = when (intent.getStringExtra("state")) {
                                BLEScanner.State.Started.name -> true
                                else -> false
                            }
                            onStarted(started = started)
                        }
                        "scanner:errors" -> {
                            when (val name = intent.getStringExtra("name")) {
                                SecurityException::class.java.name -> {
                                    requestPermissions()
                                }
                                BLEScannerException::class.java.name -> {
                                    when (BLEScannerException.Type.valueOf(intent.getStringExtra("type")!!)) {
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
                                        scanner error:
                                        name: $name
                                        message: ${intent.getStringExtra("message")}
                                    """.trimIndent()
                                    showToast(message = message)
                                    println(message) // todo
                                }
                            }
                        }
                    }
                }
            }
        }
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                println("[MainActivity]:lifecycle:state: ${lifecycle.currentState}")
                val intent = Intent(context, ScannerService::class.java)
                intent.action = "state"
                startService(intent)
            }
        }
    }

//    override fun onPause() {
//        println("[MainActivity]:onPause")
//        super.onPause()
//        unregisterReceiver(receivers)
//    }

//    override fun onResume() {
//        println("[MainActivity]:onResume")
//        super.onResume()
//        val context: Context = this
//        ContextCompat.registerReceiver(
//            context,
//            receivers,
//            filters,
//            ContextCompat.RECEIVER_NOT_EXPORTED,
//        )
//        val intent = Intent(context, ScannerService::class.java)
//        intent.action = "state"
//        startService(intent)
//    }
}
