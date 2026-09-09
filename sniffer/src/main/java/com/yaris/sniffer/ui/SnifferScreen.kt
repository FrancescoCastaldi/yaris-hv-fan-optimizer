package com.yaris.sniffer.ui

import android.content.Intent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yaris.sniffer.bluetooth.BridgeBluetoothManager
import com.yaris.sniffer.bluetooth.SnifferConnectionState
import com.yaris.sniffer.server.BridgeLogger
import com.yaris.sniffer.server.BridgeServer
import com.yaris.sniffer.server.BridgeServerState
import com.yaris.sniffer.ui.theme.AccentBlue
import com.yaris.sniffer.ui.theme.AccentCyan
import com.yaris.sniffer.ui.theme.CardBackground
import com.yaris.sniffer.ui.theme.CardBorder
import com.yaris.sniffer.ui.theme.CardBorderActive
import com.yaris.sniffer.ui.theme.DarkBackground
import com.yaris.sniffer.ui.theme.RacingRed
import com.yaris.sniffer.ui.theme.SuccessGreen
import com.yaris.sniffer.ui.theme.SurfaceDark
import com.yaris.sniffer.ui.theme.TerminalBackground
import com.yaris.sniffer.ui.theme.TerminalText
import com.yaris.sniffer.ui.theme.TextMuted
import com.yaris.sniffer.ui.theme.TextPrimary
import com.yaris.sniffer.ui.theme.TextSecondary
import com.yaris.sniffer.ui.theme.WarningOrange

@Composable
fun SnifferScreen(
    btManager: BridgeBluetoothManager,
    server: BridgeServer,
    logger: BridgeLogger,
    onRequestPermissions: () -> Unit
) {
    val context = LocalContext.current
    val btState by btManager.connectionState.collectAsState()
    val discoveredDevices by btManager.discoveredDevices.collectAsState()
    val serverState by server.serverState.collectAsState()
    val isRecording by logger.isRecording.collectAsState()
    val txCount by logger.txCount.collectAsState()
    val rxCount by logger.rxCount.collectAsState()
    val recentLogs by logger.recentLogs.collectAsState()

    var showDeviceList by remember { mutableStateOf(false) }
    var showOfflineWarningDialog by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

    LaunchedEffect(recentLogs.size) {
        if (recentLogs.isNotEmpty()) {
            listState.animateScrollToItem(recentLogs.size - 1)
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "recBlink")
    val recAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "recAlpha"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(16.dp)
    ) {
        // --- Top Bar ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "YARIS OBD BRIDGE",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(AccentCyan.copy(alpha = 0.2f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("v1.0.0", color = AccentCyan, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                Text(
                    text = "MITM TCP Bridge su 127.0.0.1:35000",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }

            // REC Blinking Badge
            if (isRecording) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(RacingRed.copy(alpha = 0.2f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(RacingRed)
                            .alpha(recAlpha)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "REC",
                        color = RacingRed,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // --- Bluetooth Adapter Card ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (btManager.isConnected) Icons.Default.BluetoothConnected else Icons.Default.Bluetooth,
                            contentDescription = null,
                            tint = if (btManager.isConnected) SuccessGreen else AccentBlue,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "ADATTATORE OBD",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextSecondary
                        )
                    }

                    Row {
                        OutlinedButton(
                            onClick = {
                                onRequestPermissions()
                                btManager.startScan()
                                showDeviceList = true
                            },
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp), tint = AccentCyan)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Cerca", fontSize = 12.sp, color = AccentCyan)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                val statusText = when (val s = btState) {
                    is SnifferConnectionState.Connected -> "Connesso a: ${s.deviceName} (${s.address})"
                    is SnifferConnectionState.Connecting -> "Connessione in corso a ${s.deviceName}..."
                    is SnifferConnectionState.Scanning -> "Scansione dispositivi Bluetooth in corso..."
                    is SnifferConnectionState.Error -> "Errore: ${s.message}"
                    is SnifferConnectionState.Disconnected -> "Nessun adattatore collegato"
                }
                val statusColor = when (btState) {
                    is SnifferConnectionState.Connected -> SuccessGreen
                    is SnifferConnectionState.Connecting, is SnifferConnectionState.Scanning -> WarningOrange
                    is SnifferConnectionState.Error -> RacingRed
                    else -> TextMuted
                }

                Text(statusText, fontSize = 13.sp, color = statusColor, fontWeight = FontWeight.Medium)

                if (btManager.isConnected) {
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = { btManager.disconnect() },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = RacingRed),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Disconnetti Adattatore", fontSize = 12.sp)
                    }
                }

                // Discovered Devices List Drawer
                if (showDeviceList && discoveredDevices.isNotEmpty() && !btManager.isConnected) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Dispositivi trovati / associati:", fontSize = 11.sp, color = TextMuted)
                    Spacer(modifier = Modifier.height(4.dp))
                    Column(modifier = Modifier.fillMaxWidth()) {
                        discoveredDevices.take(4).forEach { dev ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(SurfaceDark)
                                    .clickable {
                                        btManager.connect(dev.address, dev.isBle)
                                        showDeviceList = false
                                    }
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(dev.name, fontSize = 13.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
                                    Text(dev.address, fontSize = 11.sp, color = TextSecondary)
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (dev.isBle) AccentCyan.copy(0.2f) else AccentBlue.copy(0.2f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        if (dev.isBle) "BLE" else "SPP",
                                        fontSize = 10.sp,
                                        color = if (dev.isBle) AccentCyan else AccentBlue
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // --- Bridge Server & Counters Card ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "TCP BRIDGE SERVER",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextSecondary
                    )

                    val serverStatusDesc = when (val st = serverState) {
                        is BridgeServerState.Listening -> "IN ASCOLTO (:35000)"
                        is BridgeServerState.ClientConnected -> "CLIENT CONNESSO"
                        is BridgeServerState.Error -> "ERRORE"
                        BridgeServerState.Stopped -> "SPENTO"
                    }
                    val serverStatusCol = when (serverState) {
                        is BridgeServerState.ClientConnected -> SuccessGreen
                        is BridgeServerState.Listening -> AccentCyan
                        is BridgeServerState.Error -> RacingRed
                        BridgeServerState.Stopped -> TextMuted
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(serverStatusCol.copy(alpha = 0.2f))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(serverStatusDesc, fontSize = 11.sp, color = serverStatusCol, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (!btManager.isConnected) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(WarningOrange.copy(alpha = 0.15f))
                            .border(1.dp, WarningOrange.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                            .padding(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Bluetooth,
                                contentDescription = null,
                                tint = WarningOrange,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "ATTENZIONE: Nessun adattatore Bluetooth connesso! Per il pass-through reale verso la centralina Toyota, connetti prima l'adattatore OBD. L'avvio senza Bluetooth opererà in emulazione offline mock.",
                                fontSize = 11.sp,
                                color = WarningOrange,
                                lineHeight = 14.sp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Action Buttons for Bridge Server
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            if (server.isRunning) {
                                server.stop()
                                com.yaris.sniffer.service.BridgeForegroundService.stop(context)
                            } else {
                                if (!btManager.isConnected) {
                                    showOfflineWarningDialog = true
                                } else {
                                    onRequestPermissions()
                                    com.yaris.sniffer.service.BridgeForegroundService.start(context)
                                    server.start(35000)
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (server.isRunning) RacingRed else AccentCyan,
                            contentColor = if (server.isRunning) TextPrimary else DarkBackground
                        )
                    ) {
                        Icon(
                            if (server.isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            if (server.isRunning) "Ferma Server Bridge" else "Avvia Server Bridge (127.0.0.1:35000)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Counters: TX / RX
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("FRAME TX >>>", fontSize = 11.sp, color = AccentBlue, fontWeight = FontWeight.SemiBold)
                        Text("$txCount", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    }
                    Box(
                        modifier = Modifier
                            .height(36.dp)
                            .width(1.dp)
                            .background(CardBorder)
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("FRAME RX <<<", fontSize = 11.sp, color = SuccessGreen, fontWeight = FontWeight.SemiBold)
                        Text("$rxCount", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Logging Control Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            if (isRecording) {
                                logger.pauseRecording()
                            } else {
                                logger.startRecording(btManager.connectedDeviceName)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (isRecording) WarningOrange else TextPrimary
                        )
                    ) {
                        Text(if (isRecording) "Pausa Registrazione" else "Avvia Registrazione Log", fontSize = 11.sp)
                    }

                    Button(
                        onClick = {
                            logger.pauseRecording()
                            val shareIntent = logger.createShareIntent()
                            if (shareIntent != null) {
                                context.startActivity(Intent.createChooser(shareIntent, "Condividi Log OBD"))
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SurfaceDark,
                            contentColor = AccentCyan
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorderActive)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Ferma & Condividi Log", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // --- Live Console / Log Monitor ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "MONITOR TRAFFICO REAL-TIME",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextSecondary
            )
            IconButton(
                onClick = { logger.clearUiLogs() },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(Icons.Default.Clear, contentDescription = "Pulisci Schermo", tint = TextMuted, modifier = Modifier.size(16.dp))
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(TerminalBackground)
                .border(1.dp, CardBorder, RoundedCornerShape(8.dp))
                .padding(8.dp)
        ) {
            if (recentLogs.isEmpty()) {
                Text(
                    "In attesa di traffico... Avvia il server e connetti Dr. Prius a 127.0.0.1:35000",
                    fontSize = 12.sp,
                    color = TextMuted,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(recentLogs) { line ->
                        val col = when {
                            line.contains("TX >>>") -> AccentBlue
                            line.contains("RX <<<") -> SuccessGreen
                            line.contains("[SYS]") -> WarningOrange
                            line.contains("● REGISTRAZIONE") -> RacingRed
                            else -> TerminalText
                        }
                        Text(
                            text = line,
                            color = col,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 15.sp
                        )
                    }
                }
            }
        }
    }

    if (showOfflineWarningDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showOfflineWarningDialog = false },
            title = {
                Text(
                    text = "Adattatore Bluetooth Non Connesso",
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            },
            text = {
                Text(
                    text = "Nessun adattatore Bluetooth OBD è attualmente collegato.\n\n" +
                        "Se avvii il Bridge ora, Hybrid Assistant comunicherà solo con il simulatore interno offline (nessun dato reale dalla vettura).\n\n" +
                        "Vuoi prima connettere l'adattatore Bluetooth o procedere comunque in emulazione offline?",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showOfflineWarningDialog = false
                        onRequestPermissions()
                        btManager.startScan()
                        showDeviceList = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentCyan, contentColor = DarkBackground)
                ) {
                    Text("Connetti Bluetooth", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showOfflineWarningDialog = false
                        onRequestPermissions()
                        com.yaris.sniffer.service.BridgeForegroundService.start(context)
                        server.start(35000)
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = WarningOrange)
                ) {
                    Text("Avvia Solo Emulatore Offline")
                }
            },
            containerColor = CardBackground
        )
    }
}
