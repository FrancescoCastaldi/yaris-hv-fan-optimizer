package com.yaris.hvfan.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

sealed class BleConnectionState {
    object Disconnected : BleConnectionState()
    object Scanning : BleConnectionState()
    data class Connecting(val deviceName: String, val address: String) : BleConnectionState()
    data class Connected(val deviceName: String, val address: String) : BleConnectionState()
    data class Ready(val deviceName: String, val address: String) : BleConnectionState()
    data class Error(val message: String) : BleConnectionState()
}

data class DiscoveredBleDevice(
    val name: String,
    val address: String,
    val rssi: Int
)

@SuppressLint("MissingPermission")
class BleManager(private val context: Context) {

    companion object {
        private const val TAG = "BleManager"
        private const val SCAN_PERIOD_MS = 10000L
        private const val COMMAND_TIMEOUT_MS = 3000L
    }

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
    private val bleScanner: BluetoothLeScanner? get() = bluetoothAdapter?.bluetoothLeScanner

    private var bluetoothGatt: BluetoothGatt? = null
    private var writeCharacteristic: BluetoothGattCharacteristic? = null
    private var notifyCharacteristic: BluetoothGattCharacteristic? = null

    private val _connectionState = MutableStateFlow<BleConnectionState>(BleConnectionState.Disconnected)
    val connectionState: StateFlow<BleConnectionState> = _connectionState

    private val _discoveredDevices = MutableStateFlow<List<DiscoveredBleDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<DiscoveredBleDevice>> = _discoveredDevices

    private val _incomingData = MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 64)
    val incomingData: SharedFlow<String> = _incomingData

    private val responseBuffer = StringBuilder()
    private var activeResponseDeferred: CompletableDeferred<String>? = null
    private val commandMutex = Mutex()

    private val mainHandler = Handler(Looper.getMainLooper())
    private var isScanning = false

    val isBluetoothEnabled: Boolean
        get() = bluetoothAdapter?.isEnabled == true

    // --- Scanning ---

    fun startScan() {
        if (!isBluetoothEnabled) {
            _connectionState.value = BleConnectionState.Error("Bluetooth non abilitato")
            return
        }
        if (isScanning) return

        _discoveredDevices.value = emptyList()
        _connectionState.value = BleConnectionState.Scanning
        isScanning = true

        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            bleScanner?.startScan(null, scanSettings, scanCallback)
            mainHandler.postDelayed({
                stopScan()
            }, SCAN_PERIOD_MS)
        } catch (e: Exception) {
            Log.e(TAG, "Errore avvio scansione BLE", e)
            _connectionState.value = BleConnectionState.Error("Errore avvio scansione: ${e.localizedMessage}")
            isScanning = false
        }
    }

    fun stopScan() {
        if (!isScanning) return
        isScanning = false
        try {
            bleScanner?.stopScan(scanCallback)
        } catch (e: Exception) {
            Log.w(TAG, "Errore stop scansione", e)
        }
        if (_connectionState.value is BleConnectionState.Scanning) {
            _connectionState.value = BleConnectionState.Disconnected
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.let { res ->
                val device = res.device
                val name = device.name ?: res.scanRecord?.deviceName ?: "Dispositivo Sconosciuto"
                val address = device.address
                val rssi = res.rssi

                val currentList = _discoveredDevices.value.toMutableList()
                val existingIndex = currentList.indexOfFirst { it.address == address }
                val newEntry = DiscoveredBleDevice(name, address, rssi)

                if (existingIndex >= 0) {
                    currentList[existingIndex] = newEntry
                } else {
                    currentList.add(newEntry)
                }
                _discoveredDevices.value = currentList
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "Scan fallito con codice $errorCode")
            isScanning = false
            _connectionState.value = BleConnectionState.Error("Scansione fallita: codice $errorCode")
        }
    }

    private var lastTargetMac: String? = null
    private var isManualDisconnect = false
    private val reconnectRunnable = Runnable {
        lastTargetMac?.let { mac ->
            if (!isManualDisconnect && _connectionState.value is BleConnectionState.Disconnected) {
                Log.i(TAG, "Tentativo di auto-connessione periodica a $mac...")
                connect(mac)
            }
        }
    }

    fun connect(macAddress: String) {
        stopScan()
        isManualDisconnect = false
        lastTargetMac = macAddress
        mainHandler.removeCallbacks(reconnectRunnable)

        if (!isBluetoothEnabled) {
            _connectionState.value = BleConnectionState.Error("Bluetooth disattivato")
            scheduleReconnect()
            return
        }

        val device = try {
            bluetoothAdapter?.getRemoteDevice(macAddress)
        } catch (e: Exception) {
            _connectionState.value = BleConnectionState.Error("MAC Address non valido: $macAddress")
            return
        }

        if (device == null) {
            _connectionState.value = BleConnectionState.Error("Dispositivo non trovato")
            scheduleReconnect()
            return
        }

        internalDisconnect()

        val deviceName = device.name ?: "OBD Device"
        _connectionState.value = BleConnectionState.Connecting(deviceName, macAddress)
        Log.i(TAG, "Connessione a $deviceName ($macAddress)...")

        bluetoothGatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } else {
            device.connectGatt(context, false, gattCallback)
        }
    }

    private fun scheduleReconnect() {
        if (!isManualDisconnect && lastTargetMac != null) {
            mainHandler.removeCallbacks(reconnectRunnable)
            mainHandler.postDelayed(reconnectRunnable, 3500L)
        }
    }

    fun disconnect() {
        isManualDisconnect = true
        mainHandler.removeCallbacks(reconnectRunnable)
        internalDisconnect()
    }

    private fun internalDisconnect() {
        try {
            bluetoothGatt?.disconnect()
            bluetoothGatt?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Errore disconnessione", e)
        } finally {
            bluetoothGatt = null
            writeCharacteristic = null
            notifyCharacteristic = null
            _connectionState.value = BleConnectionState.Disconnected
        }
    }

    // --- GATT Callback ---

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            val device = gatt?.device
            val name = device?.name ?: "OBD Device"
            val addr = device?.address ?: ""

            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "GATT errore di stato: $status")
                internalDisconnect()
                _connectionState.value = BleConnectionState.Error("Disconnesso ($status). Riconnessione in corso...")
                scheduleReconnect()
                return
            }

            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.i(TAG, "GATT Connesso a $name. Richiesta MTU 247...")
                    mainHandler.removeCallbacks(reconnectRunnable)
                    _connectionState.value = BleConnectionState.Connected(name, addr)
                    gatt?.requestMtu(247)
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.i(TAG, "GATT Disconnesso.")
                    internalDisconnect()
                    scheduleReconnect()
                }
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
            Log.i(TAG, "MTU negoziato: $mtu, status=$status. Avvio Service Discovery...")
            gatt?.discoverServices()
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS || gatt == null) {
                Log.e(TAG, "Service Discovery fallito: $status")
                _connectionState.value = BleConnectionState.Error("Service discovery fallito")
                return
            }

            Log.i(TAG, "Servizi BLE scoperti. Cerco caratteristiche OBD...")
            var foundWrite: BluetoothGattCharacteristic? = null
            var foundNotify: BluetoothGattCharacteristic? = null

            for (service in gatt.services) {
                for (ch in service.characteristics) {
                    val props = ch.properties
                    val canWrite = (props and (BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) != 0
                    val canNotify = (props and (BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_INDICATE)) != 0

                    if (canWrite && (foundWrite == null || BleGattAttributes.WRITE_CHARACTERISTICS.contains(ch.uuid))) {
                        foundWrite = ch
                    }
                    if (canNotify && (foundNotify == null || BleGattAttributes.NOTIFY_CHARACTERISTICS.contains(ch.uuid))) {
                        foundNotify = ch
                    }
                }
            }

            if (foundWrite != null && foundNotify != null) {
                writeCharacteristic = foundWrite
                notifyCharacteristic = foundNotify
                Log.i(TAG, "Caratteristiche OBD trovate! Write: ${foundWrite.uuid}, Notify: ${foundNotify.uuid}")

                enableNotification(gatt, foundNotify)
                val name = gatt.device.name ?: "OBD Device"
                val addr = gatt.device.address
                _connectionState.value = BleConnectionState.Ready(name, addr)
            } else {
                Log.e(TAG, "Caratteristiche GATT OBD compatibili non trovate.")
                _connectionState.value = BleConnectionState.Error("Nessun profilo seriale OBD BLE compatibile trovato")
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?) {
            val bytes = characteristic?.value ?: return
            val str = String(bytes, Charsets.US_ASCII)
            handleIncomingChunk(str)
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            val str = String(value, Charsets.US_ASCII)
            handleIncomingChunk(str)
        }
    }

    private fun enableNotification(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        gatt.setCharacteristicNotification(characteristic, true)
        val descriptor = characteristic.getDescriptor(BleGattAttributes.CLIENT_CHARACTERISTIC_CONFIG)
        if (descriptor != null) {
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            gatt.writeDescriptor(descriptor)
        }
    }

    private fun handleIncomingChunk(chunk: String) {
        synchronized(responseBuffer) {
            responseBuffer.append(chunk)
            if (chunk.contains(">") || responseBuffer.contains(">")) {
                val fullResponse = responseBuffer.toString()
                responseBuffer.setLength(0)
                activeResponseDeferred?.complete(fullResponse)
            }
        }
    }

    // --- Command Execution ---

    suspend fun sendCommand(command: String): String = commandMutex.withLock {
        val gatt = bluetoothGatt ?: throw IllegalStateException("BLE non connesso")
        val writeCh = writeCharacteristic ?: throw IllegalStateException("Caratteristica Write non disponibile")

        synchronized(responseBuffer) {
            responseBuffer.setLength(0)
        }

        val deferred = CompletableDeferred<String>()
        activeResponseDeferred = deferred

        val cmdBytes = (command.trim() + "\r").toByteArray(Charsets.US_ASCII)
        writeCh.value = cmdBytes
        writeCh.writeType = if ((writeCh.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) {
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        } else {
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        }

        val success = gatt.writeCharacteristic(writeCh)
        if (!success) {
            activeResponseDeferred = null
            throw RuntimeException("Fallita scrittura su BLE per comando: $command")
        }

        return withTimeoutOrNull(COMMAND_TIMEOUT_MS) {
            deferred.await()
        } ?: run {
            activeResponseDeferred = null
            synchronized(responseBuffer) {
                val partial = responseBuffer.toString()
                responseBuffer.setLength(0)
                if (partial.isNotBlank()) partial else "TIMEOUT"
            }
        }
    }
}
