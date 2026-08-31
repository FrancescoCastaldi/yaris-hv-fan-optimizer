package com.yaris.hvfan.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

enum class BluetoothTransportType {
    BLE,
    CLASSIC_SPP,
    AUTO
}

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
    val rssi: Int,
    val isBonded: Boolean = false,
    val transportType: BluetoothTransportType = BluetoothTransportType.AUTO
)

@SuppressLint("MissingPermission")
class BleManager(private val context: Context) {

    companion object {
        private const val TAG = "BleManager"
        private const val SCAN_PERIOD_MS = 10000L
        private const val COMMAND_TIMEOUT_MS = 3500L
        
        // Standard Serial Port Profile (SPP) UUID for Bluetooth Classic ELM327 / OBD-II
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
    private val bleScanner: BluetoothLeScanner? get() = bluetoothAdapter?.bluetoothLeScanner

    // BLE GATT Objects
    private var bluetoothGatt: BluetoothGatt? = null
    private var writeCharacteristic: BluetoothGattCharacteristic? = null
    private var notifyCharacteristic: BluetoothGattCharacteristic? = null

    // Classic Bluetooth SPP Objects
    private var bluetoothSocket: BluetoothSocket? = null
    private var socketInputStream: InputStream? = null
    private var socketOutputStream: OutputStream? = null
    private var socketReaderJob: Job? = null

    private val managerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

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
    private var isServicesDiscovered = false
    private var activeTransport: BluetoothTransportType = BluetoothTransportType.AUTO

    val isBluetoothEnabled: Boolean
        get() = bluetoothAdapter?.isEnabled == true

    // --- Scanning (Dual-Stack: BLE + Classic Discovery) ---

    private val classicReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            if (BluetoothDevice.ACTION_FOUND == intent?.action) {
                val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                }

                device?.let { dev ->
                    val name = dev.name ?: "Dispositivo OBD Classic"
                    val address = dev.address
                    val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, 0).toInt()
                    val isBonded = dev.bondState == BluetoothDevice.BOND_BONDED

                    val currentList = _discoveredDevices.value.toMutableList()
                    val existingIndex = currentList.indexOfFirst { it.address == address }

                    if (existingIndex < 0) {
                        currentList.add(
                            DiscoveredBleDevice(
                                name = name,
                                address = address,
                                rssi = rssi,
                                isBonded = isBonded,
                                transportType = BluetoothTransportType.CLASSIC_SPP
                            )
                        )
                        _discoveredDevices.value = currentList
                    }
                }
            }
        }
    }

    private var isReceiverRegistered = false

    fun startScan() {
        if (!isBluetoothEnabled) {
            _connectionState.value = BleConnectionState.Error("Bluetooth non abilitato")
            return
        }
        if (isScanning) return

        // 1. Carica subito tutti i dispositivi associati (Bonded)
        val initialList = mutableListOf<DiscoveredBleDevice>()
        try {
            bluetoothAdapter?.bondedDevices?.forEach { bonded ->
                val bName = bonded.name ?: "Dispositivo Associato"
                val bType = when (bonded.type) {
                    BluetoothDevice.DEVICE_TYPE_CLASSIC -> BluetoothTransportType.CLASSIC_SPP
                    BluetoothDevice.DEVICE_TYPE_LE -> BluetoothTransportType.BLE
                    else -> BluetoothTransportType.AUTO
                }
                initialList.add(
                    DiscoveredBleDevice(
                        name = bName,
                        address = bonded.address,
                        rssi = 0,
                        isBonded = true,
                        transportType = bType
                    )
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Impossibile recuperare dispositivi bonded", e)
        }

        _discoveredDevices.value = initialList
        _connectionState.value = BleConnectionState.Scanning
        isScanning = true

        // 2. Registra receiver per discovery Bluetooth Classic
        try {
            if (!isReceiverRegistered) {
                val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
                context.registerReceiver(classicReceiver, filter)
                isReceiverRegistered = true
            }
            bluetoothAdapter?.startDiscovery()
        } catch (e: Exception) {
            Log.w(TAG, "Errore avvio discovery classic", e)
        }

        // 3. Avvia scansione BLE attiva
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
            isScanning = false
        }
    }

    fun stopScan() {
        if (!isScanning) return
        isScanning = false
        try {
            bleScanner?.stopScan(scanCallback)
            bluetoothAdapter?.cancelDiscovery()
            if (isReceiverRegistered) {
                context.unregisterReceiver(classicReceiver)
                isReceiverRegistered = false
            }
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
                val address = device.address
                val rssi = res.rssi

                // Risoluzione robusta del nome del dispositivo
                var resolvedName = device.name ?: res.scanRecord?.deviceName

                if (resolvedName.isNullOrBlank()) {
                    resolvedName = parseNameFromAdvBytes(res.scanRecord?.bytes)
                }

                if (resolvedName.isNullOrBlank() || resolvedName.equals("Dispositivo Sconosciuto", ignoreCase = true)) {
                    val serviceUuids = res.scanRecord?.serviceUuids
                    val hasObdService = serviceUuids?.any { parcelUuid ->
                        BleGattAttributes.KNOWN_OBD_SERVICES.contains(parcelUuid.uuid)
                    } ?: false

                    resolvedName = if (hasObdService) {
                        "Vgate / OBD-II BLE"
                    } else {
                        "Dispositivo BLE (${address.takeLast(5)})"
                    }
                }

                val currentList = _discoveredDevices.value.toMutableList()
                val existingIndex = currentList.indexOfFirst { it.address == address }
                val isBonded = device.bondState == BluetoothDevice.BOND_BONDED

                if (existingIndex >= 0) {
                    val old = currentList[existingIndex]
                    val betterName = if (old.name.contains("Dispositivo", ignoreCase = true) && !resolvedName.contains("Dispositivo", ignoreCase = true)) {
                        resolvedName
                    } else {
                        old.name
                    }
                    currentList[existingIndex] = DiscoveredBleDevice(
                        name = betterName,
                        address = address,
                        rssi = rssi,
                        isBonded = old.isBonded || isBonded,
                        transportType = BluetoothTransportType.BLE
                    )
                } else {
                    currentList.add(
                        DiscoveredBleDevice(
                            name = resolvedName,
                            address = address,
                            rssi = rssi,
                            isBonded = isBonded,
                            transportType = BluetoothTransportType.BLE
                        )
                    )
                }
                _discoveredDevices.value = currentList
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "Scan BLE fallito con codice $errorCode")
            isScanning = false
        }
    }

    private fun parseNameFromAdvBytes(bytes: ByteArray?): String? {
        if (bytes == null || bytes.isEmpty()) return null
        var ptr = 0
        while (ptr < bytes.size - 2) {
            val length = bytes[ptr].toInt() and 0xFF
            if (length == 0) break
            if (ptr + length >= bytes.size) break
            val type = bytes[ptr + 1].toInt() and 0xFF
            if (type == 0x08 || type == 0x09) { // Shortened or Complete Local Name
                val nameBytes = bytes.copyOfRange(ptr + 2, ptr + 1 + length)
                return String(nameBytes, Charsets.UTF_8).trim()
            }
            ptr += length + 1
        }
        return null
    }

    // --- Connection Orchestration ---

    private var lastTargetMac: String? = null
    private var lastTransportType: BluetoothTransportType = BluetoothTransportType.AUTO
    private var isManualDisconnect = false

    private val reconnectRunnable = Runnable {
        lastTargetMac?.let { mac ->
            if (!isManualDisconnect && _connectionState.value is BleConnectionState.Disconnected) {
                Log.i(TAG, "Tentativo di auto-connessione periodica a $mac...")
                connect(mac, lastTransportType)
            }
        }
    }

    private val serviceDiscoveryFallback = Runnable {
        bluetoothGatt?.let { gatt ->
            if (!isServicesDiscovered) {
                Log.i(TAG, "Avvio fallback Service Discovery su thread principale...")
                gatt.discoverServices()
            }
        }
    }

    fun connect(macAddress: String, transport: BluetoothTransportType = BluetoothTransportType.AUTO) {
        stopScan()
        isManualDisconnect = false
        lastTargetMac = macAddress
        lastTransportType = transport
        isServicesDiscovered = false
        mainHandler.removeCallbacks(reconnectRunnable)
        mainHandler.removeCallbacks(serviceDiscoveryFallback)

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
        Log.i(TAG, "Tentativo connessione a $deviceName ($macAddress) [Transport: $transport]...")

        val shouldTryClassic = when (transport) {
            BluetoothTransportType.CLASSIC_SPP -> true
            BluetoothTransportType.BLE -> false
            BluetoothTransportType.AUTO -> device.type == BluetoothDevice.DEVICE_TYPE_CLASSIC
        }

        if (shouldTryClassic) {
            connectClassicSocket(device)
        } else {
            connectGattClient(device)
        }
    }

    // --- Classic Bluetooth RFCOMM SPP Implementation ---

    private fun connectClassicSocket(device: BluetoothDevice) {
        activeTransport = BluetoothTransportType.CLASSIC_SPP
        managerScope.launch {
            try {
                bluetoothAdapter?.cancelDiscovery()
                val deviceName = device.name ?: "OBD Classic"
                val mac = device.address

                Log.i(TAG, "Apertura socket RFCOMM SPP verso $mac...")
                var socket: BluetoothSocket? = null
                
                try {
                    socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                    socket.connect()
                } catch (e: Exception) {
                    Log.w(TAG, "Tentativo SPP standard fallito, provo fallback reflection porta 1...", e)
                    try {
                        val method = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                        socket = method.invoke(device, 1) as BluetoothSocket
                        socket.connect()
                    } catch (e2: Exception) {
                        Log.e(TAG, "Anche il fallback reflection SPP e' fallito", e2)
                        throw e2
                    }
                }

                bluetoothSocket = socket
                socketInputStream = socket.inputStream
                socketOutputStream = socket.outputStream

                _connectionState.value = BleConnectionState.Connected(deviceName, mac)
                Log.i(TAG, "Socket SPP Connesso con successo! Avvio reader stream...")

                startSocketReader(deviceName, mac)
                delay(150)
                _connectionState.value = BleConnectionState.Ready(deviceName, mac)

            } catch (e: Exception) {
                Log.e(TAG, "Errore connessione Classic SPP", e)
                internalDisconnect()
                
                // Fallback automatico su BLE se il tipo era AUTO
                if (lastTransportType == BluetoothTransportType.AUTO) {
                    Log.i(TAG, "Fallback automatico su BLE GATT...")
                    withContext(Dispatchers.Main) {
                        connectGattClient(device)
                    }
                } else {
                    _connectionState.value = BleConnectionState.Error("Connessione Classic fallita: ${e.localizedMessage}")
                    scheduleReconnect()
                }
            }
        }
    }

    private fun startSocketReader(deviceName: String, mac: String) {
        socketReaderJob?.cancel()
        socketReaderJob = managerScope.launch {
            val buffer = ByteArray(1024)
            val stream = socketInputStream ?: return@launch

            try {
                while (isActive && bluetoothSocket?.isConnected == true) {
                    val bytesRead = stream.read(buffer)
                    if (bytesRead > 0) {
                        val chunk = String(buffer, 0, bytesRead, Charsets.US_ASCII)
                        handleIncomingChunk(chunk)
                    }
                }
            } catch (e: Exception) {
                if (isActive) {
                    Log.w(TAG, "Stream SPP interrotto", e)
                    internalDisconnect()
                    _connectionState.value = BleConnectionState.Error("Connessione interrotta. Riconnessione...")
                    scheduleReconnect()
                }
            }
        }
    }

    // --- BLE GATT Client Implementation ---

    private fun connectGattClient(device: BluetoothDevice) {
        activeTransport = BluetoothTransportType.BLE
        val deviceName = device.name ?: "OBD BLE"
        val macAddress = device.address

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
        mainHandler.removeCallbacks(serviceDiscoveryFallback)
        internalDisconnect()
    }

    private fun internalDisconnect() {
        try {
            socketReaderJob?.cancel()
            socketReaderJob = null

            socketInputStream?.close()
            socketOutputStream?.close()
            bluetoothSocket?.close()

            bluetoothGatt?.disconnect()
            bluetoothGatt?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Errore durante disconnect", e)
        } finally {
            socketInputStream = null
            socketOutputStream = null
            bluetoothSocket = null
            bluetoothGatt = null
            writeCharacteristic = null
            notifyCharacteristic = null
            isServicesDiscovered = false
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

                // Fallback automatico su Classic SPP se AUTO
                if (lastTransportType == BluetoothTransportType.AUTO && device != null) {
                    Log.i(TAG, "GATT status $status, fallback su Classic SPP...")
                    connectClassicSocket(device)
                } else {
                    _connectionState.value = BleConnectionState.Error("Disconnesso ($status). Riconnessione in corso...")
                    scheduleReconnect()
                }
                return
            }

            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.i(TAG, "GATT Connesso a $name ($addr).")
                    mainHandler.removeCallbacks(reconnectRunnable)
                    _connectionState.value = BleConnectionState.Connected(name, addr)
                    
                    gatt?.requestMtu(247)
                    mainHandler.postDelayed(serviceDiscoveryFallback, 250L)
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.i(TAG, "GATT Disconnesso.")
                    internalDisconnect()
                    scheduleReconnect()
                }
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
            Log.i(TAG, "MTU negoziato: $mtu, status=$status. Avvio Service Discovery immediato...")
            mainHandler.removeCallbacks(serviceDiscoveryFallback)
            if (!isServicesDiscovered) {
                gatt?.discoverServices()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            mainHandler.removeCallbacks(serviceDiscoveryFallback)
            isServicesDiscovered = true

            if (status != BluetoothGatt.GATT_SUCCESS || gatt == null) {
                Log.e(TAG, "Service Discovery fallito: $status")
                _connectionState.value = BleConnectionState.Error("Service discovery fallito")
                return
            }

            Log.i(TAG, "Servizi BLE scoperti. Ricerca caratteristiche seriali OBD compatibili...")
            var foundWrite: BluetoothGattCharacteristic? = null
            var foundNotify: BluetoothGattCharacteristic? = null

            // 1. Cerca prima all'interno dei servizi OBD noti
            for (service in gatt.services) {
                if (BleGattAttributes.KNOWN_OBD_SERVICES.contains(service.uuid)) {
                    Log.i(TAG, "Trovato servizio OBD noto: ${service.uuid}")
                    for (ch in service.characteristics) {
                        val props = ch.properties
                        val canWrite = (props and (BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) != 0
                        val canNotify = (props and (BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_INDICATE)) != 0

                        if (canWrite) foundWrite = ch
                        if (canNotify) foundNotify = ch
                    }
                    if (foundWrite != null && foundNotify != null) {
                        break
                    }
                }
            }

            // 2. Fallback: cerca su qualsiasi servizio personalizzato non escluso
            if (foundWrite == null || foundNotify == null) {
                for (service in gatt.services) {
                    if (BleGattAttributes.EXCLUDED_SERVICES.contains(service.uuid)) continue

                    var tempWrite: BluetoothGattCharacteristic? = null
                    var tempNotify: BluetoothGattCharacteristic? = null

                    for (ch in service.characteristics) {
                        val props = ch.properties
                        val canWrite = (props and (BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) != 0
                        val canNotify = (props and (BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_INDICATE)) != 0

                        if (canWrite) tempWrite = ch
                        if (canNotify) tempNotify = ch
                    }

                    if (tempWrite != null && tempNotify != null) {
                        foundWrite = tempWrite
                        foundNotify = tempNotify
                        Log.i(TAG, "Trovato servizio compatibile con canale duplex: ${service.uuid}")
                        break
                    }
                }
            }

            if (foundWrite != null && foundNotify != null) {
                writeCharacteristic = foundWrite
                notifyCharacteristic = foundNotify
                Log.i(TAG, "Caratteristiche OBD collegate! Write: ${foundWrite.uuid}, Notify: ${foundNotify.uuid}")

                enableNotification(gatt, foundNotify)
                val name = gatt.device.name ?: "OBD Device"
                val addr = gatt.device.address

                mainHandler.postDelayed({
                    _connectionState.value = BleConnectionState.Ready(name, addr)
                }, 300L)
            } else {
                Log.e(TAG, "Caratteristiche GATT OBD compatibili non trovate.")
                _connectionState.value = BleConnectionState.Error("Nessun profilo seriale OBD BLE compatibile trovato")
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt?, descriptor: BluetoothGattDescriptor?, status: Int) {
            val name = gatt?.device?.name ?: "OBD Device"
            val addr = gatt?.device?.address ?: ""
            Log.i(TAG, "CCCD descriptor scritto con successo (status=$status). Pronto per comandi OBD.")
            _connectionState.value = BleConnectionState.Ready(name, addr)
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

    // --- Unified Command Dispatcher (SPP Socket or BLE GATT) ---

    suspend fun sendCommand(command: String): String = commandMutex.withLock {
        synchronized(responseBuffer) {
            responseBuffer.setLength(0)
        }

        val deferred = CompletableDeferred<String>()
        activeResponseDeferred = deferred

        val cmdString = command.trim() + "\r"
        val cmdBytes = cmdString.toByteArray(Charsets.US_ASCII)

        // 1. Invia tramite Classic Bluetooth SPP Socket se connesso
        val outStream = socketOutputStream
        if (bluetoothSocket?.isConnected == true && outStream != null) {
            withContext(Dispatchers.IO) {
                outStream.write(cmdBytes)
                outStream.flush()
            }
        } else {
            // 2. Altrimenti invia tramite BLE GATT Characteristic
            val gatt = bluetoothGatt ?: throw IllegalStateException("Nessun canale Bluetooth connesso")
            val writeCh = writeCharacteristic ?: throw IllegalStateException("Caratteristica Write non disponibile")

            writeCh.value = cmdBytes
            writeCh.writeType = if ((writeCh.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0 &&
                (writeCh.properties and BluetoothGattCharacteristic.PROPERTY_WRITE) == 0) {
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            } else {
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            }

            val success = gatt.writeCharacteristic(writeCh)
            if (!success) {
                activeResponseDeferred = null
                throw RuntimeException("Fallita scrittura su BLE per comando: $command")
            }
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


