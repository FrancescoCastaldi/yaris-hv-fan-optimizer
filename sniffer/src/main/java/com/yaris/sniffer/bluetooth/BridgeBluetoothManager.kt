package com.yaris.sniffer.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothSocket
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

sealed class SnifferConnectionState {
    object Disconnected : SnifferConnectionState()
    object Scanning : SnifferConnectionState()
    data class Connecting(val deviceName: String, val address: String) : SnifferConnectionState()
    data class Connected(val deviceName: String, val address: String, val isBle: Boolean) : SnifferConnectionState()
    data class Error(val message: String) : SnifferConnectionState()
}

data class SnifferDiscoveredDevice(
    val name: String,
    val address: String,
    val isBonded: Boolean,
    val isBle: Boolean
)

@SuppressLint("MissingPermission")
class BridgeBluetoothManager(private val context: Context) {

    companion object {
        private const val TAG = "BridgeBtManager"
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        val KNOWN_OBD_SERVICES = listOf(
            UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb"),
            UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb"),
            UUID.fromString("0000fee0-0000-1000-8000-00805f9b34fb"),
            UUID.fromString("e7810a71-73ae-499d-8c15-faa9aef0c3f2")
        )
    }

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private val _connectionState = MutableStateFlow<SnifferConnectionState>(SnifferConnectionState.Disconnected)
    val connectionState: StateFlow<SnifferConnectionState> = _connectionState.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<SnifferDiscoveredDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<SnifferDiscoveredDevice>> = _discoveredDevices.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())

    private var socket: BluetoothSocket? = null
    private var inStream: InputStream? = null
    private var outStream: OutputStream? = null
    private var socketJob: Job? = null

    private var gatt: BluetoothGatt? = null
    private var writeChar: BluetoothGattCharacteristic? = null
    private var notifyChar: BluetoothGattCharacteristic? = null

    private val responseBuffer = StringBuilder()
    private var activeDeferred: CompletableDeferred<String>? = null
    private val sendMutex = Mutex()

    private var isClassicReceiverRegistered = false
    private var isScanning = false

    val isBluetoothEnabled: Boolean
        get() = bluetoothAdapter?.isEnabled == true

    val isConnected: Boolean
        get() = _connectionState.value is SnifferConnectionState.Connected

    val connectedDeviceName: String?
        get() = when (val s = _connectionState.value) {
            is SnifferConnectionState.Connected -> "${s.deviceName} (${s.address})"
            else -> null
        }

    private val classicReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            if (BluetoothDevice.ACTION_FOUND == intent?.action) {
                val dev: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                }
                dev?.let {
                    val name = it.name ?: "OBD Device"
                    addDiscovered(SnifferDiscoveredDevice(name, it.address, it.bondState == BluetoothDevice.BOND_BONDED, false))
                }
            }
        }
    }

    private val bleScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.device?.let { dev ->
                val name = dev.name ?: result.scanRecord?.deviceName ?: "Dispositivo BLE"
                addDiscovered(SnifferDiscoveredDevice(name, dev.address, dev.bondState == BluetoothDevice.BOND_BONDED, true))
            }
        }
    }

    private fun addDiscovered(device: SnifferDiscoveredDevice) {
        val list = _discoveredDevices.value.toMutableList()
        val idx = list.indexOfFirst { it.address == device.address }
        if (idx < 0) {
            list.add(device)
            _discoveredDevices.value = list.sortedWith(
                compareByDescending<SnifferDiscoveredDevice> { it.isBonded }
                    .thenBy { it.name }
            )
        }
    }

    fun startScan() {
        if (!isBluetoothEnabled) {
            _connectionState.value = SnifferConnectionState.Error("Bluetooth non abilitato")
            return
        }
        if (isScanning) return
        isScanning = true
        _connectionState.value = SnifferConnectionState.Scanning

        val list = mutableListOf<SnifferDiscoveredDevice>()
        try {
            bluetoothAdapter?.bondedDevices?.forEach { dev ->
                val name = dev.name ?: "Dispositivo Associato"
                val isBle = dev.type == BluetoothDevice.DEVICE_TYPE_LE
                list.add(SnifferDiscoveredDevice(name, dev.address, isBonded = true, isBle = isBle))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Errore lettura bonded devices", e)
        }
        _discoveredDevices.value = list

        try {
            if (!isClassicReceiverRegistered) {
                val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
                context.registerReceiver(classicReceiver, filter)
                isClassicReceiverRegistered = true
            }
            bluetoothAdapter?.startDiscovery()
        } catch (e: Exception) {
            Log.w(TAG, "Errore discovery classic", e)
        }

        try {
            val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
            bluetoothAdapter?.bluetoothLeScanner?.startScan(null, settings, bleScanCallback)
        } catch (e: Exception) {
            Log.w(TAG, "Errore scan BLE", e)
        }

        mainHandler.postDelayed({ stopScan() }, 10000L)
    }

    fun stopScan() {
        if (!isScanning) return
        isScanning = false
        if (_connectionState.value is SnifferConnectionState.Scanning) {
            _connectionState.value = SnifferConnectionState.Disconnected
        }
        try {
            bluetoothAdapter?.cancelDiscovery()
            if (isClassicReceiverRegistered) {
                context.unregisterReceiver(classicReceiver)
                isClassicReceiverRegistered = false
            }
            bluetoothAdapter?.bluetoothLeScanner?.stopScan(bleScanCallback)
        } catch (e: Exception) {
            Log.w(TAG, "Errore stop scan", e)
        }
    }

    fun connect(address: String, isBle: Boolean) {
        stopScan()
        disconnect()

        val adapter = bluetoothAdapter ?: run {
            _connectionState.value = SnifferConnectionState.Error("Bluetooth non supportato")
            return
        }

        val device = try {
            adapter.getRemoteDevice(address)
        } catch (e: Exception) {
            _connectionState.value = SnifferConnectionState.Error("Indirizzo non valido: $address")
            return
        }

        val name = device.name ?: address
        _connectionState.value = SnifferConnectionState.Connecting(name, address)

        if (isBle) {
            connectBle(device, name, address)
        } else {
            connectClassic(device, name, address)
        }
    }

    private fun connectClassic(device: BluetoothDevice, name: String, address: String) {
        scope.launch {
            try {
                bluetoothAdapter?.cancelDiscovery()
                val s = device.createRfcommSocketToServiceRecord(SPP_UUID)
                socket = s
                withContext(Dispatchers.IO) {
                    s.connect()
                }
                inStream = s.inputStream
                outStream = s.outputStream
                _connectionState.value = SnifferConnectionState.Connected(name, address, isBle = false)
                startSocketReader()
            } catch (e: Exception) {
                Log.w(TAG, "Errore connessione classic SPP, provo fallback BLE", e)
                internalDisconnect()
                // Fallback attempt on BLE
                withContext(Dispatchers.Main) {
                    connectBle(device, name, address)
                }
            }
        }
    }

    private fun startSocketReader() {
        socketJob?.cancel()
        socketJob = scope.launch {
            val buf = ByteArray(1024)
            val stream = inStream ?: return@launch
            try {
                while (isActive && socket?.isConnected == true) {
                    val read = stream.read(buf)
                    if (read == -1) break
                    if (read > 0) {
                        val chunk = String(buf, 0, read, Charsets.ISO_8859_1)
                        handleIncomingChunk(chunk)
                    }
                }
            } catch (e: Exception) {
                if (isActive) {
                    Log.w(TAG, "Socket reader interrotto: ${e.message}")
                    internalDisconnect()
                    _connectionState.value = SnifferConnectionState.Error("Disconnesso dal dispositivo")
                }
            }
        }
    }

    private fun connectBle(device: BluetoothDevice, name: String, address: String) {
        val callback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(g: BluetoothGatt?, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    g?.requestMtu(247)
                    g?.discoverServices()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    internalDisconnect()
                    _connectionState.value = SnifferConnectionState.Disconnected
                }
            }

            override fun onServicesDiscovered(g: BluetoothGatt?, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS && g != null) {
                    var foundW: BluetoothGattCharacteristic? = null
                    var foundN: BluetoothGattCharacteristic? = null

                    for (svc in g.services) {
                        for (ch in svc.characteristics) {
                            val p = ch.properties
                            val w = (p and (BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) != 0
                            val n = (p and (BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_INDICATE)) != 0
                            if (w && foundW == null) foundW = ch
                            if (n && foundN == null) foundN = ch
                        }
                    }

                    if (foundW != null && foundN != null) {
                        writeChar = foundW
                        notifyChar = foundN
                        g.setCharacteristicNotification(foundN, true)
                        val desc = foundN.getDescriptor(CCCD_UUID)
                        if (desc != null) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                g.writeDescriptor(desc, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                            } else {
                                @Suppress("DEPRECATION")
                                desc.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                @Suppress("DEPRECATION")
                                g.writeDescriptor(desc)
                            }
                        }
                        _connectionState.value = SnifferConnectionState.Connected(name, address, isBle = true)
                    } else {
                        _connectionState.value = SnifferConnectionState.Error("Caratteristiche BLE OBD non trovate")
                    }
                }
            }

            override fun onCharacteristicChanged(g: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?) {
                val bytes = characteristic?.value ?: return
                handleIncomingChunk(String(bytes, Charsets.ISO_8859_1))
            }

            override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
                handleIncomingChunk(String(value, Charsets.ISO_8859_1))
            }
        }

        gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
        } else {
            device.connectGatt(context, false, callback)
        }
    }

    private fun handleIncomingChunk(chunk: String) {
        synchronized(responseBuffer) {
            responseBuffer.append(chunk)
            if (responseBuffer.contains(">")) {
                val complete = responseBuffer.toString()
                activeDeferred?.complete(complete)
            }
        }
    }

    suspend fun sendCommand(rawCmd: String, timeoutMs: Long = 4000L): String = sendMutex.withLock {
        val deferred = CompletableDeferred<String>()
        synchronized(responseBuffer) {
            responseBuffer.setLength(0)
            activeDeferred = deferred
        }

        val toSend = if (rawCmd.endsWith("\r")) rawCmd else "$rawCmd\r"
        val bytes = toSend.toByteArray(Charsets.US_ASCII)

        try {
            if (socket?.isConnected == true && outStream != null) {
                withContext(Dispatchers.IO) {
                    outStream?.write(bytes)
                    outStream?.flush()
                }
            } else if (gatt != null && writeChar != null) {
                val g = gatt!!
                val ch = writeChar!!
                val writeType = if ((ch.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0 &&
                    (ch.properties and BluetoothGattCharacteristic.PROPERTY_WRITE) == 0) {
                    BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                } else {
                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                }
                ch.writeType = writeType
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    g.writeCharacteristic(ch, bytes, writeType)
                } else {
                    @Suppress("DEPRECATION")
                    ch.value = bytes
                    @Suppress("DEPRECATION")
                    g.writeCharacteristic(ch)
                }
            } else {
                throw IllegalStateException("Nessun canale Bluetooth connesso")
            }

            return withTimeoutOrNull(timeoutMs) {
                deferred.await()
            } ?: run {
                synchronized(responseBuffer) {
                    val partial = responseBuffer.toString()
                    responseBuffer.setLength(0)
                    if (partial.isNotBlank()) partial else "TIMEOUT"
                }
            }
        } finally {
            synchronized(responseBuffer) {
                if (activeDeferred === deferred) activeDeferred = null
            }
        }
    }

    fun disconnect() {
        internalDisconnect()
        _connectionState.value = SnifferConnectionState.Disconnected
    }

    private fun internalDisconnect() {
        try {
            socketJob?.cancel()
            socketJob = null
            inStream?.close()
            outStream?.close()
            socket?.close()
            socket = null

            gatt?.disconnect()
            gatt?.close()
            gatt = null
            writeChar = null
            notifyChar = null
        } catch (e: Exception) {
            Log.w(TAG, "Errore durante disconnect", e)
        }
    }

    fun cleanup() {
        stopScan()
        disconnect()
        scope.cancel()
    }
}
