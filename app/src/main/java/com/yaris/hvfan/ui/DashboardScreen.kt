package com.yaris.hvfan.ui

import com.yaris.hvfan.BuildConfig

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Intent
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.yaris.hvfan.R
import com.yaris.hvfan.ble.BleConnectionState
import com.yaris.hvfan.obd.*
import com.yaris.hvfan.ui.theme.*
import com.yaris.hvfan.ui.components.*

enum class DashboardTab {
    GR_COCKPIT,
    FAN_CONTROL,
    ECU_CODING
}

@Composable
fun DashboardScreen(
    connectionState: BleConnectionState,
    liveState: ObdLiveState,
    savedDeviceName: String?,
    savedDeviceMac: String?,
    onOpenDevicePicker: () -> Unit,
    onDisconnect: () -> Unit,
    onReconnect: () -> Unit,
    onThresholdChanged: (Int) -> Unit,
    onForcedFanToggle: (Boolean) -> Unit,
    onManualFanLevelChanged: (Int) -> Unit = {},
    onAutoCoolingToggle: (Boolean) -> Unit = {},
    onAutoCoolingTriggerChanged: (Float) -> Unit = {},
    onAutoCoolingHysteresisChanged: (Float) -> Unit = {},
    onAutoCoolingTargetSpeedChanged: (Int) -> Unit = {},
    onReadEcuCoding: () -> Unit = {},
    onApplyEcuCoding: (EcuCustomizationState) -> Unit = {},
    onRestoreFactoryEcuCoding: () -> Unit = {}
) {
    var selectedTab by remember { mutableStateOf(DashboardTab.GR_COCKPIT) }
    var showLogs by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    val isDeviceConnected = (connectionState is BleConnectionState.Ready || connectionState is BleConnectionState.Connected) &&
        liveState.isInitialized

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    Box(
        modifier = Modifier
            .fillMaxSize()
            .carbonFiberBackground()
    ) {
        if (isLandscape) {
            // --- 🏁 OPPO A94 5G MOTORSPORT LANDSCAPE LAYOUT (20:9 DUAL-COLUMN COCKPIT) ---
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 32.dp, end = 16.dp, top = 8.dp, bottom = 8.dp) // 32dp punch-hole safe margin
            ) {
            // 1. VERTICAL COMPACT NAVIGATION SIDEBAR (Left Thumb Ergonomics)
            Surface(
                modifier = Modifier
                    .width(72.dp)
                    .fillMaxHeight(),
                shape = RoundedCornerShape(8.dp),
                color = SurfaceDark,
                border = BorderStroke(1.dp, CardBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 8.dp, horizontal = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // GR Mini Badge
                        GazooRacingLogoBadge()

                        Spacer(modifier = Modifier.height(4.dp))

                        // Tab 1: Cockpit
                        val isGr = selectedTab == DashboardTab.GR_COCKPIT
                        Surface(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { selectedTab = DashboardTab.GR_COCKPIT }
                                .then(
                                    if (isGr) Modifier.border(1.5.dp, GrRedPrimary, RoundedCornerShape(6.dp))
                                    else Modifier
                                ),
                            color = if (isGr) DarkBackground else Color.Transparent,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Default.Speed, contentDescription = null, tint = if (isGr) GrRedPrimary else TextSecondary, modifier = Modifier.size(20.dp))
                                Text("COCKPIT", fontSize = 8.sp, fontWeight = FontWeight.Black, color = if (isGr) TextPrimary else TextSecondary)
                            }
                        }

                        // Tab 2: Ventola
                        val isFan = selectedTab == DashboardTab.FAN_CONTROL
                        Surface(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { selectedTab = DashboardTab.FAN_CONTROL }
                                .then(
                                    if (isFan) Modifier.border(1.5.dp, AccentCyan, RoundedCornerShape(6.dp))
                                    else Modifier
                                ),
                            color = if (isFan) DarkBackground else Color.Transparent,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Default.Air, contentDescription = null, tint = if (isFan) AccentCyan else TextSecondary, modifier = Modifier.size(20.dp))
                                Text("VENTOLA", fontSize = 8.sp, fontWeight = FontWeight.Black, color = if (isFan) TextPrimary else TextSecondary)
                            }
                        }

                        // Tab 3: Codifiche
                        val isEcu = selectedTab == DashboardTab.ECU_CODING
                        Surface(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { selectedTab = DashboardTab.ECU_CODING }
                                .then(
                                    if (isEcu) Modifier.border(1.5.dp, AccentCyan, RoundedCornerShape(6.dp))
                                    else Modifier
                                ),
                            color = if (isEcu) DarkBackground else Color.Transparent,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Default.Tune, contentDescription = null, tint = if (isEcu) AccentCyan else TextSecondary, modifier = Modifier.size(20.dp))
                                Text("CODIFICHE", fontSize = 8.sp, fontWeight = FontWeight.Black, color = if (isEcu) TextPrimary else TextSecondary)
                            }
                        }
                    }

                    // Bottom Quick Actions (OBD & Logs)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { onOpenDevicePicker() },
                            color = DarkBackground,
                            border = BorderStroke(1.dp, CardBorder)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Bluetooth, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(18.dp))
                            }
                        }

                        Surface(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { showLogs = !showLogs },
                            color = DarkBackground,
                            border = BorderStroke(1.dp, CardBorder)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Terminal, contentDescription = null, tint = if (showLogs) AccentCyan else TextMuted, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // 2. MAIN ADAPTIVE DASHBOARD WORKSPACE
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                // Compact Top Telemetry Strip
                Surface(
                    modifier = Modifier.fillMaxWidth().height(42.dp),
                    shape = RoundedCornerShape(6.dp),
                    color = SurfaceDark,
                    border = BorderStroke(1.dp, CardBorder)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "YARIS HV GR",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black,
                                color = TextPrimary,
                                letterSpacing = 0.8.sp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = savedDeviceName ?: "NO DONGLE",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = AccentCyan
                            )
                        }

                        ConnectionBadge(
                            connectionState = connectionState,
                            isInitialized = liveState.isInitialized,
                            hasEcuCommunication = liveState.hasEcuCommunication,
                            isVehicleReady = liveState.isVehicleReady,
                            isStandbyMode = liveState.isStandbyMode,
                            auxiliary12vVoltage = liveState.auxiliary12vVoltage
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Banner Standby / Alert per orientamento Landscape
                if ((connectionState is BleConnectionState.Ready || connectionState is BleConnectionState.Connected) &&
                    (!liveState.hasEcuCommunication || liveState.isStandbyMode || !liveState.isInitialized) && liveState.ecuAlertMessage != null) {
                    val isInitializing = !liveState.isInitialized
                    val isStandby = liveState.isStandbyMode
                    val isReadySync = liveState.isVehicleReady && !liveState.hasEcuCommunication
                    val isNormalProgress = isInitializing || isStandby || isReadySync
                    val bannerBorder = if (isNormalProgress) AccentCyan.copy(alpha = 0.5f) else WarningOrange
                    val bannerColor = if (isNormalProgress) AccentCyan else WarningOrange
                    val bannerTitle = when {
                        isInitializing -> "HANDSHAKE OBD"
                        isStandby -> "MODALITÀ STANDBY"
                        isReadySync -> "SINCRONIZZAZIONE CAN"
                        else -> "CENTRALINA NON RISPONDE"
                    }
                    val bannerIcon = if (isNormalProgress) Icons.Default.HourglassEmpty else Icons.Default.Warning

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                        shape = RoundedCornerShape(4.dp),
                        color = DarkBackground,
                        border = BorderStroke(1.dp, bannerBorder)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = bannerIcon,
                                contentDescription = null,
                                tint = bannerColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "$bannerTitle: ${liveState.ecuAlertMessage}",
                                fontSize = 9.5.sp,
                                color = TextSecondary,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (!isInitializing) {
                                Spacer(modifier = Modifier.width(6.dp))
                                OutlinedButton(
                                    onClick = onReconnect,
                                    shape = RoundedCornerShape(4.dp),
                                    border = BorderStroke(1.dp, bannerBorder),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = bannerColor),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                    modifier = Modifier.height(24.dp)
                                ) {
                                    val btnText = if (isStandby) "SVEGLIA" else if (isReadySync) "SINCRONIZZA" else "RIPROVA"
                                    Text(text = btnText, fontSize = 9.sp, fontWeight = FontWeight.Black)
                                }
                            }
                        }
                    }
                }

                // Split 2-Column Screen Workspace (Cockpit + Fan side-by-side or scrollable tab)
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    if (selectedTab == DashboardTab.GR_COCKPIT) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Left Col: Engine & Speed
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .verticalScroll(rememberScrollState())
                            ) {
                                GrCockpitSection(
                                    liveState = liveState,
                                    isConnected = isDeviceConnected
                                )
                            }

                            // Right Col: Live Battery Pack Denso Matrix & Fan Duty
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .verticalScroll(rememberScrollState())
                            ) {
                                FanManagementSection(
                                    liveState = liveState,
                                    isConnected = isDeviceConnected,
                                    onThresholdChanged = onThresholdChanged,
                                    onForcedFanToggle = onForcedFanToggle,
                                    onAutoCoolingToggle = onAutoCoolingToggle,
                                    onAutoCoolingTriggerChanged = onAutoCoolingTriggerChanged,
                                    onAutoCoolingHysteresisChanged = onAutoCoolingHysteresisChanged,
                                    onAutoCoolingTargetSpeedChanged = onAutoCoolingTargetSpeedChanged,
                                    onManualFanLevelChanged = onManualFanLevelChanged
                                )
                            }
                        }
                    } else if (selectedTab == DashboardTab.FAN_CONTROL) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                        ) {
                            FanManagementSection(
                                liveState = liveState,
                                isConnected = isDeviceConnected,
                                onThresholdChanged = onThresholdChanged,
                                onForcedFanToggle = onForcedFanToggle,
                                onAutoCoolingToggle = onAutoCoolingToggle,
                                onAutoCoolingTriggerChanged = onAutoCoolingTriggerChanged,
                                onAutoCoolingHysteresisChanged = onAutoCoolingHysteresisChanged,
                                onAutoCoolingTargetSpeedChanged = onAutoCoolingTargetSpeedChanged,
                                onManualFanLevelChanged = onManualFanLevelChanged
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                        ) {
                            EcuCodingSection(
                                codingState = liveState.ecuCodingState,
                                isConnected = isDeviceConnected,
                                onRead = onReadEcuCoding,
                                onApply = onApplyEcuCoding,
                                onRestoreFactory = onRestoreFactoryEcuCoding
                            )
                        }
                    }
                }
            }

            // Landscape Diagnostic Log Modal Dialog
            if (showLogs) {
                androidx.compose.ui.window.Dialog(
                    onDismissRequest = { showLogs = false },
                    properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
                ) {
                    val listState = rememberLazyListState()
                    val context = androidx.compose.ui.platform.LocalContext.current
                    LaunchedEffect(liveState.logs.size) {
                        if (liveState.logs.isNotEmpty()) {
                            listState.animateScrollToItem(liveState.logs.size - 1)
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth(0.92f)
                            .fillMaxHeight(0.88f),
                        shape = RoundedCornerShape(8.dp),
                        color = SurfaceDark,
                        border = BorderStroke(1.dp, CardBorder)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(14.dp)
                        ) {
                            // Top Bar
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Terminal,
                                        contentDescription = null,
                                        tint = AccentCyan,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "TERMINALE DIAGNOSTICO CAN / OBD (${liveState.logs.size} EVENTI)",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary,
                                        letterSpacing = 0.5.sp
                                    )
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    OutlinedButton(
                                        onClick = {
                                            val shareIntent = com.yaris.hvfan.data.ObdLogger.createShareIntent()
                                            if (shareIntent != null) {
                                                context.startActivity(Intent.createChooser(shareIntent, "Esporta Log Diagnostico ECU Yaris"))
                                            }
                                        },
                                        shape = RoundedCornerShape(4.dp),
                                        border = BorderStroke(1.dp, AccentCyan),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            containerColor = DarkBackground,
                                            contentColor = AccentCyan
                                        ),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier.height(30.dp)
                                    ) {
                                        Icon(Icons.Default.Share, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("CONDIVIDI LOG", fontSize = 10.sp, fontWeight = FontWeight.Black)
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            val candumpIntent = com.yaris.hvfan.data.ObdLogger.createCanDumpShareIntent()
                                            if (candumpIntent != null) {
                                                context.startActivity(Intent.createChooser(candumpIntent, "Esporta Traccia CAN (candump)"))
                                            }
                                        },
                                        shape = RoundedCornerShape(4.dp),
                                        border = BorderStroke(1.dp, CardBorder),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            containerColor = DarkBackground,
                                            contentColor = TextPrimary
                                        ),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        modifier = Modifier.height(30.dp)
                                    ) {
                                        Icon(Icons.Default.Tune, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(12.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("CANDUMP", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }

                                    OutlinedButton(
                                        onClick = { com.yaris.hvfan.data.ObdLogger.clearLogs() },
                                        shape = RoundedCornerShape(4.dp),
                                        border = BorderStroke(1.dp, CardBorder),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            containerColor = DarkBackground,
                                            contentColor = TextMuted
                                        ),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier.height(30.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = null, tint = TextMuted, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("RESET", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }

                                    IconButton(
                                        onClick = { showLogs = false },
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Chiudi", tint = TextMuted)
                                    }
                                }
                            }

                            // Console Output Area
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .background(Color(0xFF07090C), RoundedCornerShape(4.dp))
                                    .padding(8.dp)
                            ) {
                                if (liveState.logs.isEmpty()) {
                                    Text(
                                        text = "Nessun evento registrato finora. Connettiti all'adattatore OBD per avviare il tracciamento.",
                                        color = TextMuted,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.align(Alignment.Center)
                                    )
                                } else {
                                    LazyColumn(state = listState) {
                                        items(liveState.logs) { logLine ->
                                            val color = when {
                                                logLine.contains("ERR") || logLine.contains("fallit") || logLine.contains("TIMEOUT") || logLine.contains("EXCEPTION") -> DangerRed
                                                logLine.contains("TX >>>") -> Color(0xFF64B5F6)
                                                logLine.contains("RX <<<") -> Color(0xFF81C784)
                                                logLine.contains("✅") || logLine.contains("SUCCESS") -> SuccessGreen
                                                logLine.contains("⚠️") -> WarningOrange
                                                else -> AccentCyan
                                            }
                                            Text(
                                                text = logLine,
                                                color = color,
                                                fontSize = 11.sp,
                                                fontFamily = FontFamily.Monospace,
                                                lineHeight = 15.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    } else {
        // --- 📱 PORTRAIT LAYOUT (Vertical Navigation) ---
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(14.dp)
        ) {
            // --- Top App Header Bar (MoTeC / Bosch Precision Header) ---
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(6.dp),
                color = SurfaceDark,
                border = BorderStroke(1.dp, CardBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GazooRacingLogoBadge()
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "YARIS HV GR v${BuildConfig.VERSION_NAME}",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Black,
                                color = TextPrimary,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = savedDeviceName ?: "NESSUN DONGLE ASSOCIATO",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = if (savedDeviceName != null) AccentCyan else TextMuted
                            )
                        }
                    }

                    ConnectionBadge(
                        connectionState = connectionState,
                        isInitialized = liveState.isInitialized,
                        hasEcuCommunication = liveState.hasEcuCommunication,
                        isVehicleReady = liveState.isVehicleReady,
                        isStandbyMode = liveState.isStandbyMode,
                        auxiliary12vVoltage = liveState.auxiliary12vVoltage
                    )
                }
            }

            // --- Auto-Alert / Standby Status Banner ---
            if ((connectionState is BleConnectionState.Ready || connectionState is BleConnectionState.Connected) &&
                (!liveState.hasEcuCommunication || liveState.isStandbyMode || !liveState.isInitialized) && liveState.ecuAlertMessage != null) {
                Spacer(modifier = Modifier.height(10.dp))
                val isInitializing = !liveState.isInitialized
                val isStandby = liveState.isStandbyMode
                val isReadySync = liveState.isVehicleReady && !liveState.hasEcuCommunication
                val isNormalProgress = isInitializing || isStandby || isReadySync
                val bannerBorder = if (isNormalProgress) AccentCyan.copy(alpha = 0.6f) else WarningOrange
                val bannerColor = if (isNormalProgress) AccentCyan else WarningOrange
                val bannerTitle = when {
                    isInitializing -> "HANDSHAKE OBD IN CORSO"
                    isStandby -> "MODALITÀ STANDBY A BASSO CONSUMO"
                    isReadySync -> "SINCRONIZZAZIONE CAN IN CORSO"
                    else -> "CENTRALINA NON RISPONDE"
                }
                val bannerIcon = if (isNormalProgress) Icons.Default.HourglassEmpty else Icons.Default.Warning
                val buttonText = if (isInitializing) "ATTENDI" else if (isStandby) "SVEGLIA" else if (isReadySync) "SINCRONIZZA" else "RIPROVA"

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(4.dp),
                    color = DarkBackground,
                    border = BorderStroke(1.dp, bannerBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = bannerIcon,
                            contentDescription = null,
                            tint = bannerColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = bannerTitle,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                color = bannerColor,
                                letterSpacing = 0.8.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = liveState.ecuAlertMessage ?: "In attesa di connessione centralina Toyota.",
                                fontSize = 10.sp,
                                color = TextSecondary,
                                lineHeight = 14.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = { if (!isInitializing) onReconnect() },
                            enabled = !isInitializing,
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(1.dp, if (!isInitializing) bannerBorder else TextMuted.copy(alpha = 0.3f)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = bannerColor,
                                disabledContentColor = TextMuted
                            ),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text(text = buttonText, fontSize = 10.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // --- Clean Segmented Tab Control (No neon pills) ---
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp),
                shape = RoundedCornerShape(6.dp),
                color = SurfaceDark,
                border = BorderStroke(1.dp, CardBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Tab 1: GR Cockpit
                    val isGrSelected = selectedTab == DashboardTab.GR_COCKPIT
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { selectedTab = DashboardTab.GR_COCKPIT }
                            .then(
                                if (isGrSelected) Modifier.border(BorderStroke(1.dp, GrRedPrimary), RoundedCornerShape(4.dp))
                                else Modifier
                            ),
                        color = if (isGrSelected) DarkBackground else Color.Transparent,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = null,
                                tint = if (isGrSelected) GrRedPrimary else TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "COCKPIT",
                                fontSize = 11.sp,
                                fontWeight = if (isGrSelected) FontWeight.Black else FontWeight.Bold,
                                color = if (isGrSelected) TextPrimary else TextSecondary,
                                letterSpacing = 0.8.sp
                            )
                        }
                    }

                    // Tab 2: Controllo Ventola & Batteria
                    val isFanSelected = selectedTab == DashboardTab.FAN_CONTROL
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { selectedTab = DashboardTab.FAN_CONTROL }
                            .then(
                                if (isFanSelected) Modifier.border(BorderStroke(1.dp, AccentCyan), RoundedCornerShape(4.dp))
                                else Modifier
                            ),
                        color = if (isFanSelected) DarkBackground else Color.Transparent,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Air,
                                contentDescription = null,
                                tint = if (isFanSelected) AccentCyan else TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "VENTOLA",
                                fontSize = 11.sp,
                                fontWeight = if (isFanSelected) FontWeight.Black else FontWeight.Bold,
                                color = if (isFanSelected) TextPrimary else TextSecondary,
                                letterSpacing = 0.8.sp
                            )
                        }
                    }

                    // Tab 3: Codifiche Centralina ECU
                    val isEcuSelected = selectedTab == DashboardTab.ECU_CODING
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { selectedTab = DashboardTab.ECU_CODING }
                            .then(
                                if (isEcuSelected) Modifier.border(BorderStroke(1.dp, AccentCyan), RoundedCornerShape(4.dp))
                                else Modifier
                            ),
                        color = if (isEcuSelected) DarkBackground else Color.Transparent,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = if (isEcuSelected) AccentCyan else TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "CODIFICHE",
                                fontSize = 11.sp,
                                fontWeight = if (isEcuSelected) FontWeight.Black else FontWeight.Bold,
                                color = if (isEcuSelected) TextPrimary else TextSecondary,
                                letterSpacing = 0.8.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // --- Content Based on Selected Tab ---
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    fadeIn(animationSpec = tween(150)) togetherWith fadeOut(animationSpec = tween(120))
                },
                label = "TabContentTransition"
            ) { tab ->
                when (tab) {
                    DashboardTab.GR_COCKPIT -> {
                        GrCockpitSection(
                            liveState = liveState,
                            isConnected = isDeviceConnected
                        )
                    }
                    DashboardTab.FAN_CONTROL -> {
                        FanManagementSection(
                            liveState = liveState,
                            isConnected = isDeviceConnected,
                            onThresholdChanged = onThresholdChanged,
                            onForcedFanToggle = onForcedFanToggle,
                            onAutoCoolingToggle = onAutoCoolingToggle,
                            onAutoCoolingTriggerChanged = onAutoCoolingTriggerChanged,
                            onAutoCoolingHysteresisChanged = onAutoCoolingHysteresisChanged,
                            onAutoCoolingTargetSpeedChanged = onAutoCoolingTargetSpeedChanged,
                            onManualFanLevelChanged = onManualFanLevelChanged
                        )
                    }
                    DashboardTab.ECU_CODING -> {
                        EcuCodingSection(
                            codingState = liveState.ecuCodingState,
                            isConnected = isDeviceConnected,
                            onRead = onReadEcuCoding,
                            onApply = onApplyEcuCoding,
                            onRestoreFactory = onRestoreFactoryEcuCoding
                        )
                    }
                }
            }

        Spacer(modifier = Modifier.height(14.dp))

        // --- Bottom Connection Actions ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onOpenDevicePicker,
                modifier = Modifier
                    .weight(1.2f)
                    .height(44.dp),
                shape = RoundedCornerShape(4.dp),
                border = BorderStroke(1.dp, CardBorder),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = TextPrimary,
                    containerColor = SurfaceDark
                )
            ) {
                Icon(Icons.Default.Bluetooth, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("CAMBIA OBD", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }

            if (connectionState is BleConnectionState.Connected || connectionState is BleConnectionState.Ready) {
                OutlinedButton(
                    onClick = onDisconnect,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    shape = RoundedCornerShape(4.dp),
                    border = BorderStroke(1.dp, DangerRed),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = DangerRed,
                        containerColor = DarkBackground
                    )
                ) {
                    Text("DISCONNETTI", color = DangerRed, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            } else {
                OutlinedButton(
                    onClick = onReconnect,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    shape = RoundedCornerShape(4.dp),
                    border = BorderStroke(1.dp, AccentCyan),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = AccentCyan,
                        containerColor = DarkBackground
                    )
                ) {
                    Text("RICONNETTI", color = AccentCyan, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // --- Embedded Diagnostic Log Section ---
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .clickable { showLogs = !showLogs },
            color = SurfaceDark,
            border = BorderStroke(1.dp, CardBorder),
            shape = RoundedCornerShape(4.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "TERMINALE DIAGNOSTICO CAN / OBD",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        letterSpacing = 0.5.sp
                    )
                }
                Icon(
                    imageVector = if (showLogs) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        AnimatedVisibility(visible = showLogs) {
            val listState = rememberLazyListState()
            val context = androidx.compose.ui.platform.LocalContext.current
            LaunchedEffect(liveState.logs.size) {
                if (liveState.logs.isNotEmpty()) {
                    listState.animateScrollToItem(liveState.logs.size - 1)
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .background(DarkBackground, RoundedCornerShape(4.dp))
                    .border(1.dp, CardBorder, RoundedCornerShape(4.dp))
                    .padding(10.dp)
            ) {
                // Header Bar con azioni Condividi / Pulisci
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "FRAME TX/RX & EVENTI LIVE (${liveState.logs.size})",
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        color = TextSecondary,
                        fontWeight = FontWeight.Bold
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(
                            onClick = {
                                val shareIntent = com.yaris.hvfan.data.ObdLogger.createShareIntent()
                                if (shareIntent != null) {
                                    context.startActivity(Intent.createChooser(shareIntent, "Esporta Log Diagnostico ECU Yaris"))
                                }
                            },
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(1.dp, AccentCyan),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = SurfaceDark,
                                contentColor = AccentCyan
                            ),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(26.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("CONDIVIDI LOG", fontSize = 9.sp, fontWeight = FontWeight.Black)
                        }

                        OutlinedButton(
                            onClick = {
                                val candumpIntent = com.yaris.hvfan.data.ObdLogger.createCanDumpShareIntent()
                                if (candumpIntent != null) {
                                    context.startActivity(Intent.createChooser(candumpIntent, "Esporta Traccia CAN (candump)"))
                                }
                            },
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(1.dp, CardBorder),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = SurfaceDark,
                                contentColor = TextPrimary
                            ),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                            modifier = Modifier.height(26.dp)
                        ) {
                            Icon(Icons.Default.Tune, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(11.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("CANDUMP", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = {
                                com.yaris.hvfan.data.ObdLogger.clearLogs()
                            },
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(1.dp, CardBorder),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = SurfaceDark,
                                contentColor = TextMuted
                            ),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(26.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = TextMuted, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("RESET", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .background(Color(0xFF07090C), RoundedCornerShape(4.dp))
                        .padding(8.dp)
                ) {
                    if (liveState.logs.isEmpty()) {
                        Text(
                            text = "Nessun evento registrato finora. Connettiti all'adattatore OBD per avviare il tracciamento.",
                            color = TextMuted,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    } else {
                        LazyColumn(state = listState) {
                            items(liveState.logs) { logLine ->
                                val color = when {
                                    logLine.contains("ERR") || logLine.contains("fallit") || logLine.contains("TIMEOUT") || logLine.contains("EXCEPTION") -> DangerRed
                                    logLine.contains("TX >>>") -> Color(0xFF64B5F6) // Light Blue
                                    logLine.contains("RX <<<") -> Color(0xFF81C784) // Light Green
                                    logLine.contains("✅") || logLine.contains("SUCCESS") -> SuccessGreen
                                    logLine.contains("⚠️") -> WarningOrange
                                    else -> AccentCyan
                                }
                                Text(
                                    text = logLine,
                                    color = color,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    lineHeight = 14.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
}



