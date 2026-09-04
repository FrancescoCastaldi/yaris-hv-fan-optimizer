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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.yaris.hvfan.R
import com.yaris.hvfan.ble.BleConnectionState
import com.yaris.hvfan.obd.*
import com.yaris.hvfan.ui.theme.*

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

    if (isLandscape) {
        // --- 🏁 OPPO A94 5G MOTORSPORT LANDSCAPE LAYOUT (20:9 DUAL-COLUMN COCKPIT) ---
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF06080D)) // Pure deep OLED black to hide punch hole
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
                            hasEcuCommunication = liveState.hasEcuCommunication
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

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
                                    onForcedFanToggle = onForcedFanToggle
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
                                onForcedFanToggle = onForcedFanToggle
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
        }
    } else {
        // --- 📱 PORTRAIT LAYOUT (Vertical Navigation) ---
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkBackground)
                .padding(14.dp)
                .verticalScroll(scrollState)
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
                        hasEcuCommunication = liveState.hasEcuCommunication
                    )
                }
            }

            // --- Auto-Alert Banner for ECU Communication ---
            if ((connectionState is BleConnectionState.Ready || connectionState is BleConnectionState.Connected) &&
                !liveState.hasEcuCommunication && liveState.ecuAlertMessage != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(4.dp),
                    color = DarkBackground,
                    border = BorderStroke(1.dp, WarningOrange)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = WarningOrange,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "CENTRALINA NON RISPONDE",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                color = WarningOrange,
                                letterSpacing = 0.8.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = liveState.ecuAlertMessage ?: "Verifica che il quadro dell'auto sia su READY (spia verde accesa) e che l'adattatore OBD sia ben inserito.",
                                fontSize = 10.sp,
                                color = TextSecondary,
                                lineHeight = 14.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = onReconnect,
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(1.dp, WarningOrange),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = WarningOrange),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text(text = "RIPROVA", fontSize = 10.sp, fontWeight = FontWeight.Black)
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
                            onForcedFanToggle = onForcedFanToggle
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
            LaunchedEffect(liveState.logs.size) {
                if (liveState.logs.isNotEmpty()) {
                    listState.animateScrollToItem(liveState.logs.size - 1)
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .padding(top = 6.dp)
                    .background(DarkBackground, RoundedCornerShape(4.dp))
                    .border(1.dp, CardBorder, RoundedCornerShape(4.dp))
                    .padding(10.dp)
            ) {
                LazyColumn(state = listState) {
                    items(liveState.logs) { logLine ->
                        Text(
                            text = logLine,
                            color = AccentCyan,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 14.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}
}

/**
 * =========================================================
 * 1. GAZOO RACING (GR) COCKPIT VIEW
 * =========================================================
 */
@Composable
fun GrCockpitSection(
    liveState: ObdLiveState,
    isConnected: Boolean
) {
    val perf = liveState.performanceStatus
    val accel = liveState.accelerationState
    val warmup = liveState.warmupStatus
    val isFullBoost = isConnected && !liveState.batteryStatus.isThermalThrottled && (liveState.warmupStatus.stage == WarmupStage.S4 || liveState.warmupStatus.stage == WarmupStage.S2)

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {

        // --- 1. MoTeC / BOSCH DIGITAL SPEEDOMETER & SHIFT-LIGHT CLUSTER ---
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(6.dp),
            color = SurfaceDark,
            border = BorderStroke(1.dp, CardBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_gr_logo),
                            contentDescription = "Gazoo Racing Logo",
                            modifier = Modifier
                                .width(36.dp)
                                .height(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "TELEMETRIA GAZOORACING",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            color = TextPrimary,
                            letterSpacing = 1.sp
                        )
                    }

                    // Hybrid Powertrain Status Indicator
                    val (statusColor, statusText) = when {
                        !isConnected -> Pair(TextMuted, "DISCONNESSO")
                        !liveState.hasEcuCommunication -> Pair(WarningOrange, "ATTESA READY")
                        accel.isLaunchReady -> Pair(SuccessGreen, "LAUNCH READY")
                        accel.isTimingActive -> Pair(WarningOrange, "SCATTO ATTIVO")
                        isFullBoost -> Pair(AccentCyan, "FULL BOOST 59kW")
                        liveState.batteryStatus.isThermalThrottled -> Pair(DangerRed, "TAGLIO TERMICO")
                        else -> Pair(SuccessGreen, "IBRIDO PRONTO")
                    }

                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = DarkBackground,
                        border = BorderStroke(1.dp, statusColor)
                    ) {
                        Text(
                            text = statusText,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            color = statusColor
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 10-Segment Discrete Shift-Light LED Bar (Bosch Motorsport style)
                val currentRpm = if (isConnected && warmup.hasLiveData) warmup.engineRpm else 0
                val rpmFrac = (currentRpm.coerceIn(0, 5600) / 5600f)
                val activeSegments = (rpmFrac * 10).toInt()

                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "REGIME MOTORE",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondary,
                            letterSpacing = 0.8.sp
                        )
                        Text(
                            text = if (isConnected && warmup.hasLiveData) {
                                if (currentRpm > 0) "$currentRpm RPM" else "EV / 0 RPM"
                            } else "-- RPM",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            color = if (isConnected && currentRpm > 0) TextPrimary else TextMuted
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        for (i in 1..10) {
                            val isLit = isConnected && i <= activeSegments
                            val segColor = when {
                                i <= 5 -> SuccessGreen
                                i <= 8 -> WarningOrange
                                else -> GrRedPrimary
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(10.dp)
                                    .background(
                                        if (isLit) segColor else DarkBackground,
                                        RoundedCornerShape(2.dp)
                                    )
                                    .border(1.dp, if (isLit) segColor else CardBorder, RoundedCornerShape(2.dp))
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Primary Speedometer & Sprint Timer Console
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(4.dp),
                    color = DarkBackground,
                    border = BorderStroke(1.dp, CardBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Monospace Speedometer
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "VELOCITÀ",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextSecondary,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    text = if (isConnected && liveState.hasEcuCommunication) "${accel.currentSpeedKmh}" else "--",
                                    fontSize = 56.sp,
                                    fontWeight = FontWeight.Black,
                                    color = if (isConnected && liveState.hasEcuCommunication && accel.currentSpeedKmh > 0) TextPrimary else TextMuted,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = " km/h",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextSecondary,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.padding(bottom = 10.dp)
                                )
                            }
                        }

                        // Divider hairline
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(64.dp)
                                .background(CardBorder)
                        )

                        // Live Sprint Timer
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = when {
                                    !isConnected -> "TIMER STANDBY"
                                    accel.isTimingActive -> "SCATTO IN CORSO"
                                    else -> "ULTIMO TEMPO"
                                },
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (accel.isTimingActive) WarningOrange else TextSecondary,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = when {
                                    accel.isTimingActive -> String.format("%.2fs", accel.elapsedMs / 1000f)
                                    accel.last0to100TimeSec != null -> String.format("%.2fs", accel.last0to100TimeSec)
                                    else -> "--.--s"
                                },
                                fontSize = 34.sp,
                                fontWeight = FontWeight.Black,
                                color = when {
                                    accel.isTimingActive -> WarningOrange
                                    accel.last0to100TimeSec != null -> SuccessGreen
                                    else -> TextMuted
                                },
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Sprint Dragy-style Times (0-50 & 0-100 km/h)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SprintScoreCard(
                        title = "0-50 km/h (Città)",
                        lastTime = accel.last0to50TimeSec,
                        bestTime = accel.best0to50TimeSec,
                        modifier = Modifier.weight(1f)
                    )
                    SprintScoreCard(
                        title = "0-100 km/h (Sprint)",
                        lastTime = accel.last0to100TimeSec,
                        bestTime = accel.best0to100TimeSec,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // --- 2. ENGINE DYNAMICS & COMBUSTION TELEMETRY (2x2 Matrix) ---
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(6.dp),
            color = SurfaceDark,
            border = BorderStroke(1.dp, CardBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "DINAMICA MOTORE TERMICO (M15A-FXE)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = TextPrimary,
                        letterSpacing = 1.sp
                    )

                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = DarkBackground,
                        border = BorderStroke(1.dp, CardBorder)
                    ) {
                        Text(
                            text = "LIVE UDS",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = AccentCyan,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TelemetryChip(
                        label = "Anticipo (°BTDC)",
                        value = if (isConnected && perf.hasLiveData) String.format("%.1f°", perf.timingAdvance) else "--.-°",
                        highlight = isConnected && perf.hasLiveData && perf.timingAdvance >= 15f,
                        modifier = Modifier.weight(1f)
                    )
                    TelemetryChip(
                        label = "Carico Motore",
                        value = if (isConnected && perf.hasLiveData) "${perf.engineLoadPercent.toInt()}%" else "--%",
                        highlight = isConnected && perf.hasLiveData && perf.engineLoadPercent > 70f,
                        modifier = Modifier.weight(1f)
                    )
                    TelemetryChip(
                        label = "Farfalla Gas",
                        value = if (isConnected && perf.hasLiveData) "${perf.throttlePercent.toInt()}%" else "--%",
                        highlight = isConnected && perf.hasLiveData && perf.throttlePercent > 50f,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(4.dp),
                    color = DarkBackground,
                    border = BorderStroke(1.dp, CardBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isConnected) Icons.Default.CheckCircle else Icons.Default.Info,
                            contentDescription = null,
                            tint = if (isConnected) SuccessGreen else TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isConnected) {
                                "Raffreddamento forzato attivo: minima resistenza interna batteria e 100% coppia MG2 pronta."
                            } else {
                                "Connetti l'adattatore OBD per attivare il monitoraggio dell'anticipo e della coppia."
                            },
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            color = TextSecondary
                        )
                    }
                }
            }
        }
    }
}

/**
 * =========================================================
 * 2. FAN & BATTERY THERMAL MANAGEMENT VIEW
 * =========================================================
 */
@Composable
fun FanManagementSection(
    liveState: ObdLiveState,
    isConnected: Boolean,
    onThresholdChanged: (Int) -> Unit,
    onForcedFanToggle: (Boolean) -> Unit
) {
    val isFanMax = isConnected && liveState.hasEcuCommunication && (liveState.batteryStatus.isFanForced || liveState.fanForcedMax)
    val bat = liveState.batteryStatus
    val deltaT = if (isConnected && liveState.hasEcuCommunication && bat.maxTemp > 0.0) {
        val temps = listOf(bat.temp1, bat.temp2, bat.temp3, bat.temp4).filter { it > 0.0 }
        if (temps.isNotEmpty()) (temps.maxOrNull() ?: 0.0) - (temps.minOrNull() ?: 0.0) else 0.0
    } else 0.0

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {

        // --- 1. FAN OVERRIDE & DISCRETE 6-LEVEL GAUGE (MoTeC Style) ---
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(6.dp),
            color = SurfaceDark,
            border = BorderStroke(1.dp, if (isFanMax) AccentCyan else CardBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "OVERDRIVE VENTOLA HV",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                color = TextPrimary,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(3.dp),
                                color = DarkBackground,
                                border = BorderStroke(1.dp, if (isFanMax) AccentCyan else CardBorder)
                            ) {
                                Text(
                                    text = if (isFanMax) "MODE 30 ACTIVE" else "AUTO OEM",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = if (isFanMax) AccentCyan else TextSecondary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = if (isFanMax) "Forzatura 100% (Duty 6/6 UDS) attiva" else "Intervento automatico alla soglia",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }

                    val haptic = LocalHapticFeedback.current
                    Switch(
                        checked = liveState.fanForcedMax,
                        onCheckedChange = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onForcedFanToggle(it)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = AccentCyan,
                            uncheckedThumbColor = TextMuted,
                            uncheckedTrackColor = DarkBackground
                        )
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Discrete 6-Segment Level Indicator
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "LIVELLO VENTOLA",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary,
                        letterSpacing = 0.8.sp
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isConnected && liveState.hasEcuCommunication && bat.isEcuAckConfirmed && bat.estimatedFanRpm > 0) {
                            Surface(
                                shape = RoundedCornerShape(2.dp),
                                color = DarkBackground,
                                border = BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.5f))
                            ) {
                                Text(
                                    text = "ECU ACK • ~${bat.estimatedFanRpm} RPM",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = SuccessGreen,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text(
                            text = if (isConnected && liveState.hasEcuCommunication) "DUTY ${bat.fanSpeedLevel} / 6" else "DUTY -- / 6",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            color = if (isConnected && liveState.hasEcuCommunication && bat.fanSpeedLevel > 0) AccentCyan else TextMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 6 Segment bars
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    for (level in 1..6) {
                        val isActive = isConnected && liveState.hasEcuCommunication && bat.fanSpeedLevel >= level
                        val activeColor = when {
                            level <= 2 -> SuccessGreen
                            level <= 4 -> AccentCyan
                            else -> WarningOrange
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(12.dp)
                                .background(
                                    if (isActive) activeColor else DarkBackground,
                                    RoundedCornerShape(2.dp)
                                )
                                .border(1.dp, if (isActive) activeColor else CardBorder, RoundedCornerShape(2.dp))
                        )
                    }
                }
            }
        }

        // --- 2. DENSO TRACTION BATTERY 4-MODULE MATRIX WITH THERMAL DELTA ---
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(6.dp),
            color = SurfaceDark,
            border = BorderStroke(1.dp, CardBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "BATTERIA TRAZIONE DENSO 177V",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            color = TextPrimary,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "Matrice 4 Sonde Modulo + Condotto Aspirazione",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }

                    val (batBadgeColor, batBadgeText) = when {
                        !isConnected -> Pair(TextMuted, "STANDBY")
                        !liveState.hasEcuCommunication -> Pair(WarningOrange, "ATTESA ECU")
                        bat.maxTemp >= 36.0 -> Pair(DangerRed, "TAGLIO TERMICO")
                        bat.maxTemp >= 31.0 -> Pair(WarningOrange, "ATTENZIONE")
                        else -> Pair(SuccessGreen, "TERMICA OTTIMALE")
                    }

                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = DarkBackground,
                        border = BorderStroke(1.dp, batBadgeColor)
                    ) {
                        Text(
                            text = batBadgeText,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            color = batBadgeColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Big Hero Temperature & Thermal Delta Grid
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        modifier = Modifier.weight(1.3f),
                        shape = RoundedCornerShape(4.dp),
                        color = DarkBackground,
                        border = BorderStroke(1.dp, CardBorder)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "TEMP MASSIMA",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextSecondary,
                                letterSpacing = 0.8.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (isConnected && liveState.hasEcuCommunication && bat.maxTemp > 0.0) String.format("%.1f°C", bat.maxTemp) else "--.-°C",
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Black,
                                color = when {
                                    !isConnected || !liveState.hasEcuCommunication -> TextMuted
                                    bat.maxTemp >= 35.0 -> DangerRed
                                    bat.maxTemp >= 30.0 -> WarningOrange
                                    else -> AccentCyan
                                },
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "LIMITE TAGLIO: 36.0°C",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextMuted,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(4.dp),
                        color = DarkBackground,
                        border = BorderStroke(1.dp, CardBorder)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "DELTA T MODULI",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextSecondary,
                                letterSpacing = 0.8.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (isConnected && liveState.hasEcuCommunication && bat.maxTemp > 0.0) String.format("Δ %.1f°C", deltaT) else "Δ --.-°C",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Black,
                                color = if (isConnected && liveState.hasEcuCommunication && deltaT > 3.0) WarningOrange else if (isConnected && liveState.hasEcuCommunication) SuccessGreen else TextMuted,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "BILANCIAMENTO",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextMuted,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 4 Cell Modules + Intake Air Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        SensorItem(label = "Cella 1", value = bat.temp1, isReady = isConnected && liveState.hasEcuCommunication && bat.temp1 > 0.0)
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        SensorItem(label = "Cella 2", value = bat.temp2, isReady = isConnected && liveState.hasEcuCommunication && bat.temp2 > 0.0)
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        SensorItem(label = "Cella 3", value = bat.temp3, isReady = isConnected && liveState.hasEcuCommunication && bat.temp3 > 0.0)
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        SensorItem(label = "Cella 4", value = bat.temp4, isReady = isConnected && liveState.hasEcuCommunication && bat.temp4 > 0.0)
                    }
                    Box(modifier = Modifier.weight(1.1f)) {
                        SensorItem(label = "Aspiraz.", value = bat.intakeTemp, isReady = isConnected && liveState.hasEcuCommunication && bat.intakeTemp > 0.0)
                    }
                }
            }
        }

        // --- 3. WARM-UP STAGES & EFFICIENCY TIMELINE CARD ---
        val warmup = liveState.warmupStatus
        val stageColor = when {
            !isConnected || !warmup.hasLiveData -> TextMuted
            warmup.stage == WarmupStage.S0 || warmup.stage == WarmupStage.S1A -> WarningOrange
            warmup.stage == WarmupStage.S1B || warmup.stage == WarmupStage.S2 -> Color(0xFFFFD54F)
            warmup.stage == WarmupStage.S3 -> AccentCyan
            warmup.stage == WarmupStage.S4 -> SuccessGreen
            else -> TextMuted
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(6.dp),
            color = SurfaceDark,
            border = BorderStroke(1.dp, CardBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "STADIO WARM-UP HSD",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            color = TextPrimary,
                            letterSpacing = 1.sp
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = DarkBackground,
                        border = BorderStroke(1.dp, stageColor)
                    ) {
                        Text(
                            text = if (isConnected && warmup.hasLiveData) warmup.stage.name else "STANDBY",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            color = stageColor
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = if (isConnected && warmup.hasLiveData) warmup.stage.title else "In attesa telemetria termica...",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    color = TextPrimary
                )
                Text(
                    text = if (isConnected && warmup.hasLiveData) {
                        "${warmup.stage.subtitle} • Target: ${warmup.stage.targetTempDescription}"
                    } else {
                        "Accendi il quadro vettura su READY per rilevare lo stadio HSD."
                    },
                    fontSize = 11.sp,
                    color = TextSecondary,
                    lineHeight = 15.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TelemetryChip(
                        label = "Liquido (ECT)",
                        value = if (isConnected && warmup.hasLiveData) "${warmup.coolantTemp.toInt()}°C" else "--°C",
                        highlight = isConnected && warmup.hasLiveData && warmup.coolantTemp >= 73f,
                        modifier = Modifier.weight(1f)
                    )
                    TelemetryChip(
                        label = "Aria Esterna",
                        value = if (isConnected && warmup.hasLiveData) "${warmup.ambientTemp.toInt()}°C" else "--°C",
                        highlight = false,
                        modifier = Modifier.weight(1f)
                    )
                    TelemetryChip(
                        label = "Giri Motore",
                        value = if (isConnected && warmup.hasLiveData) {
                            if (warmup.engineRpm > 0) "${warmup.engineRpm}" else "EV / 0 RPM"
                        } else "-- RPM",
                        highlight = isConnected && warmup.hasLiveData && warmup.engineRpm == 0 && warmup.stage == WarmupStage.S4,
                        modifier = Modifier.weight(1.1f)
                    )
                }

                if (isConnected && warmup.recommendations.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(4.dp),
                        color = DarkBackground,
                        border = BorderStroke(1.dp, CardBorder)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "PROTOCOLLO EFFICIENZA TOYOTA",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                color = AccentCyan,
                                letterSpacing = 0.8.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            warmup.recommendations.forEach { tip ->
                                Text(
                                    text = "• $tip",
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp,
                                    color = TextSecondary,
                                    modifier = Modifier.padding(vertical = 1.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // --- 4. TARGET THRESHOLD SLIDER & PRESETS CARD ---
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(6.dp),
            color = SurfaceDark,
            border = BorderStroke(1.dp, CardBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "SOGLIA ATTIVAZIONE AUTOMATICA",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = TextPrimary,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "${liveState.targetThreshold}°C",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = AccentCyan,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Slider(
                    value = liveState.targetThreshold.toFloat(),
                    onValueChange = { onThresholdChanged(it.toInt()) },
                    valueRange = 15f..40f,
                    steps = 24,
                    colors = SliderDefaults.colors(
                        thumbColor = AccentCyan,
                        activeTrackColor = AccentCyan,
                        inactiveTrackColor = DarkBackground
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PresetButton(
                        label = "20°C (Racing)",
                        isSelected = liveState.targetThreshold == 20,
                        modifier = Modifier.weight(1f),
                        onClick = { onThresholdChanged(20) }
                    )
                    PresetButton(
                        label = "25°C (Bilanciato)",
                        isSelected = liveState.targetThreshold == 25,
                        modifier = Modifier.weight(1f),
                        onClick = { onThresholdChanged(25) }
                    )
                    PresetButton(
                        label = "30°C (Silenzioso)",
                        isSelected = liveState.targetThreshold == 30,
                        modifier = Modifier.weight(1f),
                        onClick = { onThresholdChanged(30) }
                    )
                }
            }
        }
    }
}

/**
 * =========================================================
 * 3. COMPLETE ECU CUSTOMIZATION & CODING VIEW
 * =========================================================
 */
@Composable
fun EcuCodingSection(
    codingState: EcuCustomizationState,
    isConnected: Boolean,
    onRead: () -> Unit,
    onApply: (EcuCustomizationState) -> Unit,
    onRestoreFactory: () -> Unit
) {
    var stateDraft by remember(codingState) { mutableStateOf(codingState) }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {

        // --- Top Control & Safety Backup Card ---
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(6.dp),
            color = SurfaceDark,
            border = BorderStroke(1.dp, CardBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = AccentCyan,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "CENTRALINA BODY (UDS/TDS)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            color = TextPrimary,
                            letterSpacing = 1.sp
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = DarkBackground,
                        border = BorderStroke(1.dp, if (isConnected) AccentCyan else CardBorder)
                    ) {
                        Text(
                            text = if (isConnected) (if (codingState.isReadCompleted) "BACKUP ATTIVO" else "PRONTO") else "IN ATTESA OBD",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            color = if (isConnected) AccentCyan else TextMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = codingState.lastOperationStatus,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = if (codingState.lastOperationStatus.contains("✅")) SuccessGreen else TextSecondary,
                    lineHeight = 15.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onRead,
                        enabled = isConnected && !codingState.isWriting,
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp),
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(1.dp, if (isConnected) AccentCyan else CardBorder),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = AccentCyan,
                            containerColor = DarkBackground
                        )
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("LEGGI ECU", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { onApply(stateDraft) },
                        enabled = isConnected && !codingState.isWriting,
                        modifier = Modifier
                            .weight(1.3f)
                            .height(42.dp),
                        shape = RoundedCornerShape(4.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = GrRedPrimary,
                            disabledContainerColor = CardBorder
                        )
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("SCRIVI SU ECU", fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                TextButton(
                    onClick = onRestoreFactory,
                    enabled = isConnected && !codingState.isWriting,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Ripristina configurazione originale Toyota OEM", fontSize = 11.sp, color = TextMuted)
                }
            }
        }

        // --- Category 0: 📺 TOYOTA TOUCH 3 (DISPLAY AUDIO - SENZA MAPPE) ---
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(6.dp),
            color = SurfaceDark,
            border = BorderStroke(1.dp, CardBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Tv,
                            contentDescription = null,
                            tint = AccentCyan,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "DISPLAY AUDIO TOUCH 3",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            color = TextPrimary,
                            letterSpacing = 1.sp
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = DarkBackground,
                        border = BorderStroke(1.dp, CardBorder)
                    ) {
                        Text(
                            text = "HEAD UNIT",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Animazione di Avvio Schermo
                Text(
                    text = "Animazione di Avvio (Opening Screen)",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PresetButton(
                        label = "GR Gazoo",
                        isSelected = stateDraft.touch3OpeningAnimation == Touch3OpeningScreen.GAZOO_RACING,
                        modifier = Modifier.weight(1.1f),
                        onClick = { stateDraft = stateDraft.copy(touch3OpeningAnimation = Touch3OpeningScreen.GAZOO_RACING) }
                    )
                    PresetButton(
                        label = "Hybrid Synergy",
                        isSelected = stateDraft.touch3OpeningAnimation == Touch3OpeningScreen.HYBRID_SYNERGY,
                        modifier = Modifier.weight(1.1f),
                        onClick = { stateDraft = stateDraft.copy(touch3OpeningAnimation = Touch3OpeningScreen.HYBRID_SYNERGY) }
                    )
                    PresetButton(
                        label = "Standard",
                        isSelected = stateDraft.touch3OpeningAnimation == Touch3OpeningScreen.STANDARD_TOYOTA,
                        modifier = Modifier.weight(0.9f),
                        onClick = { stateDraft = stateDraft.copy(touch3OpeningAnimation = Touch3OpeningScreen.STANDARD_TOYOTA) }
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // ASL Auto Sound Levelizer
                Text(
                    text = "ASL: Compensazione Volume con Velocità",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PresetButton(
                        label = "OFF",
                        isSelected = stateDraft.aslVolumeMode == AslVolumeMode.OFF,
                        modifier = Modifier.weight(0.8f),
                        onClick = { stateDraft = stateDraft.copy(aslVolumeMode = AslVolumeMode.OFF) }
                    )
                    PresetButton(
                        label = "Basso",
                        isSelected = stateDraft.aslVolumeMode == AslVolumeMode.LOW,
                        modifier = Modifier.weight(1f),
                        onClick = { stateDraft = stateDraft.copy(aslVolumeMode = AslVolumeMode.LOW) }
                    )
                    PresetButton(
                        label = "Medio",
                        isSelected = stateDraft.aslVolumeMode == AslVolumeMode.MID,
                        modifier = Modifier.weight(1.1f),
                        onClick = { stateDraft = stateDraft.copy(aslVolumeMode = AslVolumeMode.MID) }
                    )
                    PresetButton(
                        label = "Alto",
                        isSelected = stateDraft.aslVolumeMode == AslVolumeMode.HIGH,
                        modifier = Modifier.weight(1f),
                        onClick = { stateDraft = stateDraft.copy(aslVolumeMode = AslVolumeMode.HIGH) }
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Retrocamera Delay
                Text(
                    text = "Ritardo Spegnimento Retrocamera in Marcia D",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PresetButton(
                        label = "5 secondi (Comodo)",
                        isSelected = stateDraft.rearCameraDelay == CameraOffDelay.SEC_5,
                        modifier = Modifier.weight(1.3f),
                        onClick = { stateDraft = stateDraft.copy(rearCameraDelay = CameraOffDelay.SEC_5) }
                    )
                    PresetButton(
                        label = "Immediato",
                        isSelected = stateDraft.rearCameraDelay == CameraOffDelay.IMMEDIATE,
                        modifier = Modifier.weight(1f),
                        onClick = { stateDraft = stateDraft.copy(rearCameraDelay = CameraOffDelay.IMMEDIATE) }
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                CodingSwitchRow(
                    label = "Bip Feedback Tocco Schermo & Tasti",
                    checked = stateDraft.touchScreenBeep,
                    onCheckedChange = { stateDraft = stateDraft.copy(touchScreenBeep = it) }
                )
            }
        }

        // --- Category 1: 🔔 Comfort & Cicalini di Bordo ---
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(6.dp),
            color = SurfaceDark,
            border = BorderStroke(1.dp, CardBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "COMFORT & CICALINI",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    color = TextPrimary,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(14.dp))

                Text(text = "Cicalino Retromarcia (Reverse Beep)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PresetButton(
                        label = "Singolo Bip (Comfort)",
                        isSelected = stateDraft.reverseBeep == ReverseBeepMode.SINGLE,
                        modifier = Modifier.weight(1f),
                        onClick = { stateDraft = stateDraft.copy(reverseBeep = ReverseBeepMode.SINGLE) }
                    )
                    PresetButton(
                        label = "Continuo (OEM)",
                        isSelected = stateDraft.reverseBeep == ReverseBeepMode.CONTINUOUS,
                        modifier = Modifier.weight(1f),
                        onClick = { stateDraft = stateDraft.copy(reverseBeep = ReverseBeepMode.CONTINUOUS) }
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = CardBorder)
                Spacer(modifier = Modifier.height(10.dp))

                CodingSwitchRow(
                    label = "Cicalino Cintura Conducente",
                    checked = stateDraft.driverSeatbeltBeep,
                    onCheckedChange = { stateDraft = stateDraft.copy(driverSeatbeltBeep = it) }
                )
                CodingSwitchRow(
                    label = "Cicalino Cintura Passeggero",
                    checked = stateDraft.passengerSeatbeltBeep,
                    onCheckedChange = { stateDraft = stateDraft.copy(passengerSeatbeltBeep = it) }
                )
                CodingSwitchRow(
                    label = "Cicalino Cinture Posteriori",
                    checked = stateDraft.rearSeatbeltBeep,
                    onCheckedChange = { stateDraft = stateDraft.copy(rearSeatbeltBeep = it) }
                )
            }
        }

        // --- Category 2: 🔑 Smart Key, Telecomando & Serrature ---
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(6.dp),
            color = SurfaceDark,
            border = BorderStroke(1.dp, CardBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "SMART KEY & SERRATURE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    color = TextPrimary,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(14.dp))

                Text(text = "Volume Segnale Sirena Esterna", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PresetButton(label = "Muto", isSelected = stateDraft.keylessBuzzerVolume == KeylessBuzzerVolume.MUTE, modifier = Modifier.weight(1f), onClick = { stateDraft = stateDraft.copy(keylessBuzzerVolume = KeylessBuzzerVolume.MUTE) })
                    PresetButton(label = "Basso", isSelected = stateDraft.keylessBuzzerVolume == KeylessBuzzerVolume.LOW, modifier = Modifier.weight(1f), onClick = { stateDraft = stateDraft.copy(keylessBuzzerVolume = KeylessBuzzerVolume.LOW) })
                    PresetButton(label = "Medio", isSelected = stateDraft.keylessBuzzerVolume == KeylessBuzzerVolume.MEDIUM, modifier = Modifier.weight(1f), onClick = { stateDraft = stateDraft.copy(keylessBuzzerVolume = KeylessBuzzerVolume.MEDIUM) })
                    PresetButton(label = "Alto", isSelected = stateDraft.keylessBuzzerVolume == KeylessBuzzerVolume.HIGH, modifier = Modifier.weight(1f), onClick = { stateDraft = stateDraft.copy(keylessBuzzerVolume = KeylessBuzzerVolume.HIGH) })
                }

                Spacer(modifier = Modifier.height(14.dp))
                Text(text = "Tempo Richiusura Automatica (Auto-Relock)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PresetButton(label = "30s", isSelected = stateDraft.autoRelockTime == AutoRelockTime.SEC_30, modifier = Modifier.weight(1f), onClick = { stateDraft = stateDraft.copy(autoRelockTime = AutoRelockTime.SEC_30) })
                    PresetButton(label = "60s", isSelected = stateDraft.autoRelockTime == AutoRelockTime.SEC_60, modifier = Modifier.weight(1f), onClick = { stateDraft = stateDraft.copy(autoRelockTime = AutoRelockTime.SEC_60) })
                    PresetButton(label = "120s", isSelected = stateDraft.autoRelockTime == AutoRelockTime.SEC_120, modifier = Modifier.weight(1f), onClick = { stateDraft = stateDraft.copy(autoRelockTime = AutoRelockTime.SEC_120) })
                }

                Spacer(modifier = Modifier.height(14.dp))
                Text(text = "Sblocco Selettivo Portiere", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PresetButton(label = "Tutte (1 tocco)", isSelected = stateDraft.doorUnlockMode == DoorUnlockMode.ALL_DOORS, modifier = Modifier.weight(1f), onClick = { stateDraft = stateDraft.copy(doorUnlockMode = DoorUnlockMode.ALL_DOORS) })
                    PresetButton(label = "Solo Guida", isSelected = stateDraft.doorUnlockMode == DoorUnlockMode.DRIVER_FIRST, modifier = Modifier.weight(1f), onClick = { stateDraft = stateDraft.copy(doorUnlockMode = DoorUnlockMode.DRIVER_FIRST) })
                }

                Spacer(modifier = Modifier.height(14.dp))
                CodingSwitchRow(
                    label = "Apertura/Chiusura Finestrini da Telecomando",
                    checked = stateDraft.windowsWithKeyFob,
                    onCheckedChange = { stateDraft = stateDraft.copy(windowsWithKeyFob = it) }
                )

                Spacer(modifier = Modifier.height(10.dp))
                Text(text = "Chiusura Automatica Serrature in Movimento", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PresetButton(label = "A 20 km/h", isSelected = stateDraft.autoDoorLock == AutoDoorLockMode.BY_SPEED, modifier = Modifier.weight(1f), onClick = { stateDraft = stateDraft.copy(autoDoorLock = AutoDoorLockMode.BY_SPEED) })
                    PresetButton(label = "In Marcia D", isSelected = stateDraft.autoDoorLock == AutoDoorLockMode.BY_SHIFT_D, modifier = Modifier.weight(1f), onClick = { stateDraft = stateDraft.copy(autoDoorLock = AutoDoorLockMode.BY_SHIFT_D) })
                    PresetButton(label = "OFF", isSelected = stateDraft.autoDoorLock == AutoDoorLockMode.OFF, modifier = Modifier.weight(0.7f), onClick = { stateDraft = stateDraft.copy(autoDoorLock = AutoDoorLockMode.OFF) })
                }

                Spacer(modifier = Modifier.height(10.dp))
                CodingSwitchRow(
                    label = "Sblocco Automatico Porte inserendo 'P'",
                    checked = stateDraft.autoDoorUnlock,
                    onCheckedChange = { stateDraft = stateDraft.copy(autoDoorUnlock = it) }
                )
            }
        }

        // --- Category 3: 🌧️ Tergicristalli & Sensore Pioggia ---
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(6.dp),
            color = SurfaceDark,
            border = BorderStroke(1.dp, CardBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "TERGICRISTALLI & SENSORE PIOGGIA",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    color = TextPrimary,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(14.dp))

                CodingSwitchRow(
                    label = "Tergilunotto Automatico in Retromarcia",
                    checked = stateDraft.rearWiperReverseLink,
                    onCheckedChange = { stateDraft = stateDraft.copy(rearWiperReverseLink = it) }
                )
                CodingSwitchRow(
                    label = "Passata Finale Anti-Goccia Lavavetri (Drip Wipe)",
                    checked = stateDraft.dripWipeExtraPass,
                    onCheckedChange = { stateDraft = stateDraft.copy(dripWipeExtraPass = it) }
                )
                CodingSwitchRow(
                    label = "Intermittenza Spazzole Legata alla Velocità",
                    checked = stateDraft.wiperSpeedLink,
                    onCheckedChange = { stateDraft = stateDraft.copy(wiperSpeedLink = it) }
                )
            }
        }

        // --- Category 4: 💡 Luci, Frecce & Plafoniera ---
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(6.dp),
            color = SurfaceDark,
            border = BorderStroke(1.dp, CardBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "FRECCE, FARI & PLAFONIERA",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    color = TextPrimary,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(14.dp))

                Text(text = "Lampeggi Freccia Comfort (Cambio Corsia)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PresetButton(label = "3 Lampeggi", isSelected = stateDraft.turnSignalFlashes == TurnSignalFlashes.FLASHES_3, modifier = Modifier.weight(1f), onClick = { stateDraft = stateDraft.copy(turnSignalFlashes = TurnSignalFlashes.FLASHES_3) })
                    PresetButton(label = "4 Lampeggi", isSelected = stateDraft.turnSignalFlashes == TurnSignalFlashes.FLASHES_4, modifier = Modifier.weight(1f), onClick = { stateDraft = stateDraft.copy(turnSignalFlashes = TurnSignalFlashes.FLASHES_4) })
                    PresetButton(label = "5 Lampeggi", isSelected = stateDraft.turnSignalFlashes == TurnSignalFlashes.FLASHES_5, modifier = Modifier.weight(1f), onClick = { stateDraft = stateDraft.copy(turnSignalFlashes = TurnSignalFlashes.FLASHES_5) })
                }

                Spacer(modifier = Modifier.height(14.dp))
                Text(text = "Dissolvenza Luci Interne Plafoniera", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PresetButton(label = "7.5s", isSelected = stateDraft.interiorDimTime == InteriorLightDimTime.SEC_7_5, modifier = Modifier.weight(1f), onClick = { stateDraft = stateDraft.copy(interiorDimTime = InteriorLightDimTime.SEC_7_5) })
                    PresetButton(label = "15s (OEM)", isSelected = stateDraft.interiorDimTime == InteriorLightDimTime.SEC_15, modifier = Modifier.weight(1f), onClick = { stateDraft = stateDraft.copy(interiorDimTime = InteriorLightDimTime.SEC_15) })
                    PresetButton(label = "30s", isSelected = stateDraft.interiorDimTime == InteriorLightDimTime.SEC_30, modifier = Modifier.weight(1f), onClick = { stateDraft = stateDraft.copy(interiorDimTime = InteriorLightDimTime.SEC_30) })
                }

                Spacer(modifier = Modifier.height(10.dp))
                CodingSwitchRow(
                    label = "Illuminazione Vano Piedi Attiva in Marcia",
                    checked = stateDraft.footwellLightingInDrive,
                    onCheckedChange = { stateDraft = stateDraft.copy(footwellLightingInDrive = it) }
                )

                Spacer(modifier = Modifier.height(14.dp))
                Text(text = "Sensibilità Fari Crepuscolari Automatici", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PresetButton(label = "Scuro (-1)", isSelected = stateDraft.lightSensitivity == LightSensitivity.DARK_1, modifier = Modifier.weight(1f), onClick = { stateDraft = stateDraft.copy(lightSensitivity = LightSensitivity.DARK_1) })
                    PresetButton(label = "Normale", isSelected = stateDraft.lightSensitivity == LightSensitivity.NORMAL, modifier = Modifier.weight(1f), onClick = { stateDraft = stateDraft.copy(lightSensitivity = LightSensitivity.NORMAL) })
                    PresetButton(label = "Chiaro (+1)", isSelected = stateDraft.lightSensitivity == LightSensitivity.LIGHT_1, modifier = Modifier.weight(1f), onClick = { stateDraft = stateDraft.copy(lightSensitivity = LightSensitivity.LIGHT_1) })
                }

                Spacer(modifier = Modifier.height(14.dp))
                Text(text = "Luci Follow Me Home", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PresetButton(label = "OFF", isSelected = stateDraft.followMeHome == FollowMeHomeDuration.OFF, modifier = Modifier.weight(0.8f), onClick = { stateDraft = stateDraft.copy(followMeHome = FollowMeHomeDuration.OFF) })
                    PresetButton(label = "30s", isSelected = stateDraft.followMeHome == FollowMeHomeDuration.SEC_30, modifier = Modifier.weight(1f), onClick = { stateDraft = stateDraft.copy(followMeHome = FollowMeHomeDuration.SEC_30) })
                    PresetButton(label = "60s", isSelected = stateDraft.followMeHome == FollowMeHomeDuration.SEC_60, modifier = Modifier.weight(1f), onClick = { stateDraft = stateDraft.copy(followMeHome = FollowMeHomeDuration.SEC_60) })
                }
            }
        }

        // --- Category 5: 🛡️ ADAS & Assistenza Guida (TSS 2.5) ---
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(6.dp),
            color = SurfaceDark,
            border = BorderStroke(1.dp, CardBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "ADAS & SICUREZZA (TSS)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    color = TextPrimary,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(14.dp))

                Text(text = "Volume Avviso Cambio Corsia (LDA / LTA)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PresetButton(label = "Basso", isSelected = stateDraft.ldaWarningVolume == LdaWarningVolume.LOW, modifier = Modifier.weight(1f), onClick = { stateDraft = stateDraft.copy(ldaWarningVolume = LdaWarningVolume.LOW) })
                    PresetButton(label = "Medio", isSelected = stateDraft.ldaWarningVolume == LdaWarningVolume.MEDIUM, modifier = Modifier.weight(1f), onClick = { stateDraft = stateDraft.copy(ldaWarningVolume = LdaWarningVolume.MEDIUM) })
                    PresetButton(label = "Alto", isSelected = stateDraft.ldaWarningVolume == LdaWarningVolume.HIGH, modifier = Modifier.weight(1f), onClick = { stateDraft = stateDraft.copy(ldaWarningVolume = LdaWarningVolume.HIGH) })
                }

                Spacer(modifier = Modifier.height(14.dp))
                Text(text = "Sensibilità Angolo Cieco (BSM)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PresetButton(label = "Vicino", isSelected = stateDraft.bsmSensitivity == BsmSensitivity.NEAR, modifier = Modifier.weight(1f), onClick = { stateDraft = stateDraft.copy(bsmSensitivity = BsmSensitivity.NEAR) })
                    PresetButton(label = "Normale", isSelected = stateDraft.bsmSensitivity == BsmSensitivity.NORMAL, modifier = Modifier.weight(1f), onClick = { stateDraft = stateDraft.copy(bsmSensitivity = BsmSensitivity.NORMAL) })
                    PresetButton(label = "Anticipato", isSelected = stateDraft.bsmSensitivity == BsmSensitivity.FAR, modifier = Modifier.weight(1f), onClick = { stateDraft = stateDraft.copy(bsmSensitivity = BsmSensitivity.FAR) })
                }
            }
        }

        // --- Category 6: ❄️ Climatizzatore & Modalità Eco ---
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(6.dp),
            color = SurfaceDark,
            border = BorderStroke(1.dp, CardBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "CLIMATIZZATORE & ECO EFFICIENZA",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    color = TextPrimary,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(14.dp))

                CodingSwitchRow(
                    label = "Attivazione Compressore A/C su 'AUTO'",
                    checked = stateDraft.autoAcWithAutoButton,
                    onCheckedChange = { stateDraft = stateDraft.copy(autoAcWithAutoButton = it) }
                )
                CodingSwitchRow(
                    label = "Modalità Eco Run Clima (Risparmio Batteria HV)",
                    checked = stateDraft.ecoAirConEfficiencyMode,
                    onCheckedChange = { stateDraft = stateDraft.copy(ecoAirConEfficiencyMode = it) }
                )
            }
        }
    }
}

@Composable
fun CodingSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 52.dp)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextPrimary, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onCheckedChange(it)
            },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = AccentCyan,
                uncheckedThumbColor = TextMuted,
                uncheckedTrackColor = DarkBackground
            )
        )
    }
}

/**
 * =========================================================
 * 4. HELPER COMPOSABLES & LOGOS (MoTeC / Bosch Motorsport)
 * =========================================================
 */

@Composable
fun GazooRacingLogoBadge() {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = Color(0xFF090A0E),
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_motorsport_logo),
                contentDescription = "Yaris HV Gazoo Racing Motorsport Badge",
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
            )
        }
    }
}

@Composable
fun SprintScoreCard(
    title: String,
    lastTime: Float?,
    bestTime: Float?,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = DarkBackground,
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title.uppercase(),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = TextSecondary,
                letterSpacing = 0.8.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (lastTime != null) String.format("%.2fs", lastTime) else "--.--s",
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                color = if (lastTime != null) TextPrimary else TextMuted,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "RECORD ",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextMuted,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = if (bestTime != null) String.format("%.2fs", bestTime) else "--.--s",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    color = if (bestTime != null) SuccessGreen else TextMuted,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
fun PresetButton(
    label: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    Surface(
        modifier = modifier
            .defaultMinSize(minHeight = 52.dp)
            .clip(RoundedCornerShape(6.dp))
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
            .then(
                if (isSelected) Modifier.border(BorderStroke(1.5.dp, AccentCyan), RoundedCornerShape(6.dp))
                else Modifier.border(BorderStroke(1.dp, CardBorder), RoundedCornerShape(6.dp))
            ),
        color = if (isSelected) AccentCyan.copy(alpha = 0.18f) else SurfaceDark,
        shape = RoundedCornerShape(6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
                color = if (isSelected) AccentCyan else TextSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
fun SensorItem(label: String, value: Double, isReady: Boolean) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = DarkBackground,
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label.uppercase(),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = TextSecondary,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (isReady) String.format("%.1f°", value) else "--.-°",
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
                color = if (isReady) TextPrimary else TextMuted,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
fun TelemetryChip(
    label: String,
    value: String,
    highlight: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(4.dp),
        color = DarkBackground,
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label.uppercase(),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = TextSecondary,
                maxLines = 1,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = value,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
                color = if (highlight) SuccessGreen else TextPrimary,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
fun ConnectionBadge(
    connectionState: BleConnectionState,
    isInitialized: Boolean = false,
    hasEcuCommunication: Boolean = false
) {
    val borderColor: Color
    val textColor: Color
    val text: String
    when (connectionState) {
        is BleConnectionState.Ready, is BleConnectionState.Connected -> {
            if (hasEcuCommunication) {
                borderColor = SuccessGreen
                textColor = SuccessGreen
                text = "● ECU ONLINE"
            } else if (isInitialized) {
                borderColor = WarningOrange
                textColor = WarningOrange
                text = "▲ DONGLE OK - ATTESA ECU"
            } else {
                borderColor = AccentCyan
                textColor = AccentCyan
                text = "◌ LINK OBD..."
            }
        }
        is BleConnectionState.Connecting -> {
            borderColor = WarningOrange
            textColor = WarningOrange
            text = "◌ CONNESSIONE"
        }
        is BleConnectionState.Reconnecting -> {
            borderColor = WarningOrange
            textColor = WarningOrange
            text = "○ AUTO-RETRY"
        }
        is BleConnectionState.Scanning -> {
            borderColor = WarningOrange
            textColor = WarningOrange
            text = "◌ SCANSIONE"
        }
        is BleConnectionState.Error -> {
            borderColor = DangerRed
            textColor = DangerRed
            text = "■ ERRORE LINK"
        }
        else -> {
            borderColor = CardBorder
            textColor = TextMuted
            text = "○ DISCONNESSO"
        }
    }

    Surface(
        shape = RoundedCornerShape(4.dp),
        color = DarkBackground,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 0.8.sp,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}
