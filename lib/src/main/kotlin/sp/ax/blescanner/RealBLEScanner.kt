package sp.ax.blescanner

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class RealBLEScanner(
    private val coroutineScope: CoroutineScope,
    private val default: CoroutineContext,
    private val context: Context,
    private val timeout: Duration,
    private val logger: BLEScannerLogger,
) : BLEScanner {
    private val _states = MutableStateFlow<BLEScanner.State>(BLEScanner.State.Stopped)
    override val states = _states.asStateFlow()

    private val _errors = MutableSharedFlow<Throwable>()
    override val errors = _errors.asSharedFlow()

    private val _devices = MutableSharedFlow<BLEDevice>()
    override val devices = _devices.asSharedFlow()

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

    private var timeLastResult = Duration.ZERO

    private val receivers = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (context == null) return
            if (intent == null) return
            when (intent.action) {
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    val text = when (state) {
                        BluetoothAdapter.STATE_OFF -> "off"
                        BluetoothAdapter.STATE_ON -> "on"
                        BluetoothAdapter.STATE_TURNING_OFF -> "turning off"
                        BluetoothAdapter.STATE_TURNING_ON -> "turning on"
                        else -> state.toString()
                    }
                    logger.info("bluetooth adapter state: $text")
                    when (state) {
                        BluetoothAdapter.STATE_OFF -> stop()
                    }
                }
                LocationManager.PROVIDERS_CHANGED_ACTION -> {
                    val lm = context.getSystemService(LocationManager::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val name = intent.getStringExtra(LocationManager.EXTRA_PROVIDER_NAME)
                        if (name != LocationManager.GPS_PROVIDER) return
                    }
                    val isLocationEnabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
                    logger.info("gps enabled: $isLocationEnabled")
                    if (!isLocationEnabled) stop()
                }
            }
        }
    }
    private val intentFilters = IntentFilter().also {
        it.addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        it.addAction(LocationManager.PROVIDERS_CHANGED_ACTION)
    }

    private fun onStates(oldState: BLEScanner.State, newState: BLEScanner.State) {
        if (oldState > newState) {
            val message = String.format("%-12S < %s", newState.name, oldState.name.lowercase())
            logger.info(message)
        } else {
            val message = String.format("%-12s > %S", oldState.name.lowercase(), newState.name)
            logger.info(message)
        }
    }

    init {
        coroutineScope.launch {
            withContext(default) {
                var state: BLEScanner.State = _states.value
                _states.drop(1).collect { newState ->
                    val oldState = state
                    state = newState
                    onStates(oldState = oldState, newState = newState)
                    if (oldState < BLEScanner.State.Starting && newState >= BLEScanner.State.Starting) {
                        register(
                            context = context,
                            receivers = receivers,
                            filters = intentFilters,
                        )
                    } else if (oldState > BLEScanner.State.Stopping && newState < BLEScanner.State.Starting) {
                        context.unregisterReceiver(receivers)
                    }
                }
            }
        }
    }

    private inner class InternalScanCallback(val id: UUID) : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            val device = BLEDevice(
                name = result?.device?.name ?: return,
                address = result.device.address ?: return,
                bytes = result.scanRecord?.bytes ?: return,
            )
            timeLastResult = now()
            coroutineScope.launch {
                _devices.emit(device)
            }
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            TODO("RealBLEScanner:InternalScanCallback:onBatchScanResults($results)")
        }

        override fun onScanFailed(errorCode: Int) {
            TODO("RealBLEScanner:InternalScanCallback:onScanFailed($errorCode)")
        }
    }

    private fun now(): Duration {
        return System.currentTimeMillis().milliseconds
    }

    private suspend fun restartByTimeout(callback: InternalScanCallback) {
        val timeDelay = 250.milliseconds
        timeLastResult = now()
        while (true) {
            val currentCallback = scanCallback.get() ?: break
            if (currentCallback.id != callback.id) break
            if (_states.value != BLEScanner.State.Started) break
            val timeNow = now()
            val timeDiff = timeNow - timeLastResult
            if (timeDiff > timeout) {
                logger.warning("I have not heard any devices in $timeout. So I am restarting.")
                mutex.withLock {
                    restartScan()
                    timeLastResult = now()
                }
                continue
            }
            delay(timeDelay)
        }
    }

    private suspend fun restartScan() {
        val state = _states.value
        if (state != BLEScanner.State.Started) {
            logger.debug("restarting cancelled (state: $state)")
            return
        }
        _states.value = BLEScanner.State.Stopping
        val callback = scanCallback.getAndSet(null)
        if (callback == null) {
            logger.debug("restarting cancelled (no callback)")
            return
        }
        try {
            stopScan(callback = callback)
        } catch (error: Throwable) {
            logger.warning("restarting: stopping error: $error")
            _states.value = BLEScanner.State.Stopped
            _errors.emit(error)
            return
        }
        _states.value = BLEScanner.State.Starting
        try {
            startScan(callback = callback)
        } catch (error: Throwable) {
            logger.warning("restarting: starting error: $error")
            _states.value = BLEScanner.State.Stopped
            _errors.emit(error)
            return
        }
        scanCallback.set(callback)
        _states.value = BLEScanner.State.Started
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
        val state = _states.value
        if (state != BLEScanner.State.Stopped) {
            logger.debug("starting cancelled (state: $state)")
            return
        }
        _states.value = BLEScanner.State.Starting
        try {
            startScan(callback = callback)
        } catch (error: Throwable) {
            logger.warning("starting error: $error")
            _states.value = BLEScanner.State.Stopped
            _errors.emit(error)
            return
        }
        scanCallback.set(callback)
        _states.value = BLEScanner.State.Started
    }

    override fun start() {
        coroutineScope.launch {
            val callback = InternalScanCallback(id = UUID.randomUUID()) // todo
            mutex.withLock {
                withContext(default) {
                    start(callback = callback)
                }
            }
            if (_states.value == BLEScanner.State.Started) {
                withContext(default) {
                    restartByTimeout(callback = callback)
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
        val state = _states.value
        if (state != BLEScanner.State.Started) {
            logger.debug("stopping cancelled (state: $state)")
            return
        }
        _states.value = BLEScanner.State.Stopping
        val callback = scanCallback.getAndSet(null)
        if (callback != null) {
            try {
                stopScan(callback = callback)
            } catch (error: Throwable) {
                logger.warning("stopping error: $error")
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
