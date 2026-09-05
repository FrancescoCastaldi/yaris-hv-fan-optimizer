package com.yaris.hvfan

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.yaris.hvfan.ble.BleConnectionState
import com.yaris.hvfan.data.AppPreferences
import com.yaris.hvfan.obd.ObdLiveState
import com.yaris.hvfan.service.FanControlForegroundService
import com.yaris.hvfan.ui.DashboardScreen
import com.yaris.hvfan.ui.DevicePickerSheet
import com.yaris.hvfan.ui.theme.DarkBackground
import com.yaris.hvfan.ui.theme.YarisHvFanTheme

class MainActivity : ComponentActivity() {

    private lateinit var appPreferences: AppPreferences
    private var serviceBinder: FanControlForegroundService.LocalBinder? = null
    private var isBound = false

    private var showDevicePicker by mutableStateOf(false)

    private val enableBtLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            checkAndAutoConnect()
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            serviceBinder = binder as? FanControlForegroundService.LocalBinder
            isBound = true
            checkAndAutoConnect()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            serviceBinder = null
            isBound = false
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            promptEnableBluetoothIfDisabled()
            checkAndAutoConnect()
        }
    }

    override fun onResume() {
        super.onResume()
        promptEnableBluetoothIfDisabled()
        checkAndAutoConnect()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appPreferences = AppPreferences(this)

        startAndBindService()
        checkPermissionsAndStart()

        setContent {
            YarisHvFanTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = DarkBackground
                ) {
                    val service = serviceBinder?.service
                    val connState by service?.bleManager?.connectionState?.collectAsState(initial = BleConnectionState.Disconnected)
                        ?: remember { mutableStateOf(BleConnectionState.Disconnected) }

                    val liveState by service?.obdController?.liveState?.collectAsState(initial = ObdLiveState())
                        ?: remember { mutableStateOf(ObdLiveState()) }

                    val discoveredDevices by service?.bleManager?.discoveredDevices?.collectAsState(initial = emptyList())
                        ?: remember { mutableStateOf(emptyList()) }

                    DashboardScreen(
                        connectionState = connState,
                        liveState = liveState,
                        savedDeviceName = appPreferences.savedDeviceName,
                        savedDeviceMac = appPreferences.savedMacAddress,
                        onOpenDevicePicker = {
                            service?.bleManager?.startScan()
                            showDevicePicker = true
                        },
                        onDisconnect = {
                            val stopIntent = Intent(this@MainActivity, FanControlForegroundService::class.java).apply {
                                action = FanControlForegroundService.ACTION_STOP
                            }
                            startService(stopIntent)
                        },
                        onReconnect = {
                            appPreferences.savedMacAddress?.let { mac ->
                                val transport = try {
                                    com.yaris.hvfan.ble.BluetoothTransportType.valueOf(appPreferences.savedTransportType)
                                } catch (e: Exception) {
                                    com.yaris.hvfan.ble.BluetoothTransportType.AUTO
                                }
                                service?.bleManager?.connect(mac, transport)
                            }
                        },
                        onThresholdChanged = { temp ->
                            appPreferences.targetTempThreshold = temp
                            service?.obdController?.setTargetThreshold(temp)
                        },
                        onForcedFanToggle = { forced ->
                            appPreferences.forcedFanSpeed = if (forced) 6 else 0
                            service?.obdController?.setForcedFan(forced)
                        },
                        onAutoCoolingToggle = { enabled ->
                            service?.obdController?.setAutoCoolingEnabled(enabled)
                        },
                        onAutoCoolingTriggerChanged = { temp ->
                            service?.obdController?.setAutoCoolingTriggerTemp(temp)
                        },
                        onAutoCoolingHysteresisChanged = { hyst ->
                            service?.obdController?.setAutoCoolingHysteresis(hyst)
                        },
                        onAutoCoolingTargetSpeedChanged = { speed ->
                            service?.obdController?.setAutoCoolingTargetSpeed(speed)
                        },
                        onReadEcuCoding = {
                            service?.obdController?.readEcuCustomizations()
                        },
                        onApplyEcuCoding = { updatedState ->
                            service?.obdController?.applyEcuCustomization(updatedState)
                        },
                        onRestoreFactoryEcuCoding = {
                            service?.obdController?.restoreFactorySettings()
                        }
                    )

                    if (showDevicePicker) {
                        DevicePickerSheet(
                            devices = discoveredDevices,
                            isScanning = connState is BleConnectionState.Scanning,
                            onStartScan = { service?.bleManager?.startScan() },
                            onDeviceSelected = { selected ->
                                appPreferences.savedMacAddress = selected.address
                                appPreferences.savedDeviceName = selected.name
                                appPreferences.savedTransportType = selected.transportType.name
                                showDevicePicker = false
                                service?.bleManager?.connect(selected.address, selected.transportType)
                            },
                            onDismiss = {
                                service?.bleManager?.stopScan()
                                showDevicePicker = false
                            }
                        )
                    }
                }
            }
        }
    }

    private fun checkPermissionsAndStart() {
        val requiredPermissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                requiredPermissions.add(Manifest.permission.BLUETOOTH_SCAN)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                requiredPermissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                requiredPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requiredPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (requiredPermissions.isNotEmpty()) {
            permissionLauncher.launch(requiredPermissions.toTypedArray())
        } else {
            promptEnableBluetoothIfDisabled()
            checkAndAutoConnect()
        }
    }

    private fun promptEnableBluetoothIfDisabled() {
        val btManager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = btManager?.adapter
        if (adapter != null && !adapter.isEnabled) {
            try {
                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                enableBtLauncher.launch(enableBtIntent)
            } catch (e: Exception) {
                // Ignore if security restriction prevents intent
            }
        }
    }

    private fun startAndBindService() {
        val serviceIntent = Intent(this, FanControlForegroundService::class.java).apply {
            action = FanControlForegroundService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun checkAndAutoConnect() {
        val savedMac = appPreferences.savedMacAddress
        if (savedMac == null) {
            // First time launch: show device picker
            showDevicePicker = true
        } else {
            // Auto connect is handled by ForegroundService or reconnect
            serviceBinder?.service?.bleManager?.connect(savedMac)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
    }
}
