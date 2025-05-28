package sp.ax.blescanner

import android.Manifest
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.CoroutineContext

class RealBLEScanner(
    private val coroutineScope: CoroutineScope,
    private val default: CoroutineContext,
    private val context: Context,
) : BLEScanner {
    private val _started = MutableStateFlow<Boolean?>(false)
    override val started = _started.asStateFlow()

    private val _errors = MutableSharedFlow<Throwable>()
    override val errors = _errors.asSharedFlow()

    private val mutex = Mutex()
    private val scanCallback = AtomicReference<InternalScanCallback?>(null)

    private val scanSettings = ScanSettings
        .Builder()
        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
        .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
        .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
        .setNumOfMatches(ScanSettings.MATCH_NUM_ONE_ADVERTISEMENT)
        .setReportDelay(0L)
        .build()
    private val scanFilters = listOf(ScanFilter.Builder().build())

    private inner class InternalScanCallback : ScanCallback() {
        // todo
    }

    private fun startScan(callback: ScanCallback) {
        val bm = context.getSystemService(BluetoothManager::class.java)
        val adapter = bm.adapter ?: TODO("RealBLEScanner:startScan:no adapter!")
        if (!adapter.isEnabled) TODO("RealBLEScanner:startScan:adapter disabled!")
        val lm = context.getSystemService(LocationManager::class.java)
        if (!lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) TODO("RealBLEScanner:startScan:gps disabled!")
        if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            TODO("RealBLEScanner:startScan:no gps permission!")
        }
        val scanner = adapter.bluetoothLeScanner ?: TODO("RealBLEScanner:startScan:no scanner!")
        scanner.startScan(scanFilters, scanSettings, callback)
    }

    private suspend fun start(callback: InternalScanCallback) {
        if (started.value != false) return // todo
        _started.value = null
        try {
            startScan(callback = callback)
        } catch (error: Throwable) {
            _started.value = false
            _errors.emit(error)
            return
        }
        scanCallback.set(callback)
        _started.value = true
    }

    override fun start() {
        coroutineScope.launch {
            val callback = InternalScanCallback()
            mutex.withLock {
                withContext(default) {
                    start(callback = callback)
                }
            }
        }
    }

    private fun stopScan(callback: InternalScanCallback) {
        val bm = context.getSystemService(BluetoothManager::class.java)
        val adapter = bm.adapter ?: TODO("RealBLEScanner:stopScan:no adapter!")
        if (!adapter.isEnabled) TODO("RealBLEScanner:stopScan:adapter disabled!")
        val scanner = adapter.bluetoothLeScanner ?: TODO("RealBLEScanner:stopScan:no scanner!")
        scanner.stopScan(callback)
    }

    private suspend fun stopScan() {
        if (started.value != true) return // todo
        _started.value = null
        val callback = scanCallback.getAndSet(null)
        if (callback == null) {
            // todo
        } else {
            try {
                stopScan(callback = callback)
            } catch (error: Throwable) {
                _errors.emit(error)
            }
        }
        _started.value = false
    }

    override fun stop() {
        coroutineScope.launch {
            mutex.withLock {
                withContext(default) {
                    stopScan()
                }
            }
        }
    }
}
