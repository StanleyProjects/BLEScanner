package sp.ax.blescanner

import android.Manifest
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import kotlinx.coroutines.CoroutineScope
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
    private val _states = MutableStateFlow<BLEScanner.State>(BLEScanner.State.Stopped)
    override val states = _states.asStateFlow()

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
        if (!adapter.isEnabled) {
            throw BLEScannerException(type = BLEScannerException.Type.BTDisabled)
        }
        val lm = context.getSystemService(LocationManager::class.java)
        if (!lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            throw BLEScannerException(type = BLEScannerException.Type.GPSDisabled)
        }
        if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            throw SecurityException("no permission: ${Manifest.permission.ACCESS_FINE_LOCATION}")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                throw SecurityException("no permission: ${Manifest.permission.BLUETOOTH_SCAN}")
            }
        }
        val scanner = adapter.bluetoothLeScanner ?: TODO("RealBLEScanner:startScan:no scanner!")
        scanner.startScan(scanFilters, scanSettings, callback)
    }

    private suspend fun start(callback: InternalScanCallback) {
        if (states.value != BLEScanner.State.Stopped) return // todo
        _states.value = BLEScanner.State.Starting
        try {
            startScan(callback = callback)
        } catch (error: Throwable) {
            _states.value = BLEScanner.State.Stopped
            _errors.emit(error)
            return
        }
        scanCallback.set(callback)
        _states.value = BLEScanner.State.Started
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

    private fun stopScan(callback: ScanCallback) {
        val bm = context.getSystemService(BluetoothManager::class.java)
        val adapter = bm.adapter ?: TODO("RealBLEScanner:stopScan:no adapter!")
        if (!adapter.isEnabled) return // todo
        val scanner = adapter.bluetoothLeScanner ?: TODO("RealBLEScanner:stopScan:no scanner!")
        scanner.stopScan(callback)
    }

    private suspend fun stopScan() {
        if (states.value != BLEScanner.State.Started) return // todo
        _states.value = BLEScanner.State.Stopping
        val callback = scanCallback.getAndSet(null)
        if (callback != null) {
            try {
                stopScan(callback = callback)
            } catch (error: Throwable) {
                _errors.emit(error)
            }
        }
        _states.value = BLEScanner.State.Stopped
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
