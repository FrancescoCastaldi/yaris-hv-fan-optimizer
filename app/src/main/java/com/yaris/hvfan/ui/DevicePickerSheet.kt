package com.yaris.hvfan.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.yaris.hvfan.ble.DiscoveredBleDevice
import com.yaris.hvfan.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicePickerSheet(
    devices: List<DiscoveredBleDevice>,
    isScanning: Boolean,
    onStartScan: () -> Unit,
    onDeviceSelected: (DiscoveredBleDevice) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDark,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Seleziona Adattatore OBD",
                    style = Typography.headlineMedium,
                    color = TextPrimary
                )
                IconButton(onClick = onStartScan) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Ricarica",
                        tint = AccentCyan
                    )
                }
            }

            Text(
                text = if (isScanning) "Scansione in corso..." else "Tocca il tuo dongle OBD per accoppiarlo:",
                style = Typography.bodyMedium,
                color = TextSecondary,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (isScanning && devices.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = AccentCyan)
                }
            } else if (devices.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Nessun dispositivo BLE trovato.\nAssicurati che il quadro dell'auto sia acceso.",
                        style = Typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 350.dp)
                ) {
                    items(devices) { device ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable { onDeviceSelected(device) },
                            color = CardBackground,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Bluetooth,
                                    contentDescription = null,
                                    tint = if (device.isBonded) SuccessGreen else AccentCyan,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = device.name,
                                            style = Typography.bodyLarge,
                                            color = TextPrimary
                                        )
                                        if (device.isBonded) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                color = SuccessGreen.copy(alpha = 0.2f),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = "ASSOCIATO",
                                                    style = Typography.labelSmall,
                                                    color = SuccessGreen,
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            color = if (device.transportType == com.yaris.hvfan.ble.BluetoothTransportType.CLASSIC_SPP) Color(0xFFE040FB).copy(alpha = 0.2f) else AccentCyan.copy(alpha = 0.2f),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = if (device.transportType == com.yaris.hvfan.ble.BluetoothTransportType.CLASSIC_SPP) "CLASSIC SPP" else "BLE 4.0/5.0",
                                                style = Typography.labelSmall,
                                                color = if (device.transportType == com.yaris.hvfan.ble.BluetoothTransportType.CLASSIC_SPP) Color(0xFFE040FB) else AccentCyan,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = if (device.isBonded && device.rssi == 0) {
                                            "${device.address} • Memoria Bluetooth Android"
                                        } else {
                                            "${device.address} (${device.rssi} dBm)"
                                        },
                                        style = Typography.labelSmall,
                                        color = TextSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
