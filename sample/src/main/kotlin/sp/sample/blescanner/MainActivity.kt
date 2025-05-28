package sp.sample.blescanner

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat

internal class MainActivity : ComponentActivity() {
    private var button: TextView? = null

    private val receivers = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            println("[MainActivity]:receivers:onReceive($intent)")
            onStarted(started = intent?.extras?.get("started") as? Boolean)
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
                    if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                        requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
                    } else if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
                    } else {
                        val intent = Intent(context, ScannerService::class.java)
                        intent.action = "start"
                        startService(intent)
                    }
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
    }

    override fun onPause() {
        println("[MainActivity]:onPause")
        super.onPause()
        unregisterReceiver(receivers)
    }

    override fun onResume() {
        println("[MainActivity]:onResume")
        super.onResume()
        val context: Context = this
        ContextCompat.registerReceiver(
            context,
            receivers,
            IntentFilter("scanner:status"),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        val intent = Intent(context, ScannerService::class.java)
        intent.action = "status"
        startService(intent)
    }
}
