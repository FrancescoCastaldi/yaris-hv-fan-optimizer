package com.yaris.sniffer

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.yaris.sniffer.bluetooth.BridgeBluetoothManager
import com.yaris.sniffer.server.BridgeLogger
import com.yaris.sniffer.server.BridgeServer
import com.yaris.sniffer.ui.SnifferScreen
import com.yaris.sniffer.ui.theme.YarisObdBridgeTheme

class MainActivity : ComponentActivity() {

    private lateinit var bluetoothManager: BridgeBluetoothManager
    private lateinit var logger: BridgeLogger
    private lateinit var server: BridgeServer

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // Permissions evaluated
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        bluetoothManager = BridgeBluetoothManager(this)
        logger = BridgeLogger(this)
        server = BridgeServer(bluetoothManager, logger)

        checkAndRequestPermissions()

        setContent {
            YarisObdBridgeTheme {
                SnifferScreen(
                    btManager = bluetoothManager,
                    server = server,
                    logger = logger,
                    onRequestPermissions = { checkAndRequestPermissions() }
                )
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }

        if (permissions.isNotEmpty()) {
            permissionLauncher.launch(permissions.toTypedArray())
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        server.cleanup()
        bluetoothManager.cleanup()
        logger.close()
    }
}
