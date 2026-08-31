package com.yaris.hvfan.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yaris.hvfan.ble.BleConnectionState
import com.yaris.hvfan.obd.ObdLiveState
import com.yaris.hvfan.obd.WarmupStage
import com.yaris.hvfan.ui.theme.*

enum class DashboardTab {
    GR_COCKPIT,
    FAN_CONTROL
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
    onForcedFanToggle: (Boolean) -> Unit
) {
    var selectedTab by remember { mutableStateOf(DashboardTab.GR_COCKPIT) }
    var showLogs by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    val infiniteTransition = rememberInfiniteTransition(label = "PulseTransition")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseScale"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(16.dp)
            .verticalScroll(scrollState)
    ) {
        // --- Top App Header Bar ---
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = SurfaceDark,
            tonalElevation = 4.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GazooRacingLogoBadge()
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "YARIS HYBRID MK4",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Black,
                            color = TextPrimary,
                            letterSpacing = 0.8.sp
                        )
                        Text(
                            text = savedDeviceName ?: "Nessun dongle associato",
                            fontSize = 12.sp,
                            color = if (savedDeviceName != null) AccentCyan else TextSecondary
                        )
                    }
                }

                ConnectionBadge(connectionState = connectionState)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // --- Custom Segmented Tab Bar (GR Cockpit vs Fan Control) ---
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(16.dp),
            color = SurfaceDark
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Tab 1: GR Cockpit
                val isGrSelected = selectedTab == DashboardTab.GR_COCKPIT
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { selectedTab = DashboardTab.GR_COCKPIT },
                    color = if (isGrSelected) Color(0xFFFF1801) else Color.Transparent,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = null,
                            tint = if (isGrSelected) Color.White else TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "GR COCKPIT",
                            fontSize = 13.sp,
                            fontWeight = if (isGrSelected) FontWeight.Black else FontWeight.Bold,
                            color = if (isGrSelected) Color.White else TextSecondary,
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
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { selectedTab = DashboardTab.FAN_CONTROL },
                    color = if (isFanSelected) AccentCyan else Color.Transparent,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Air,
                            contentDescription = null,
                            tint = if (isFanSelected) DarkBackground else TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "VENTOLA & TERMICHE",
                            fontSize = 12.sp,
                            fontWeight = if (isFanSelected) FontWeight.Black else FontWeight.Bold,
                            color = if (isFanSelected) DarkBackground else TextSecondary,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- Content Based on Selected Tab ---
        AnimatedContent(
            targetState = selectedTab,
            transitionSpec = {
                fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(180))
            },
            label = "TabContentTransition"
        ) { tab ->
            when (tab) {
                DashboardTab.GR_COCKPIT -> {
                    GrCockpitSection(
                        liveState = liveState,
                        pulseScale = pulseScale
                    )
                }
                DashboardTab.FAN_CONTROL -> {
                    FanManagementSection(
                        liveState = liveState,
                        pulseScale = pulseScale,
                        onThresholdChanged = onThresholdChanged,
                        onForcedFanToggle = onForcedFanToggle
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- Bottom Connection Actions ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onOpenDevicePicker,
                modifier = Modifier
                    .weight(1.2f)
                    .height(50.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CardBackground)
            ) {
                Icon(Icons.Default.Bluetooth, contentDescription = null, tint = AccentCyan)
                Spacer(modifier = Modifier.width(8.dp))
                Text("CAMBIA OBD", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }

            if (connectionState is BleConnectionState.Connected || connectionState is BleConnectionState.Ready) {
                Button(
                    onClick = onDisconnect,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DangerRed.copy(alpha = 0.2f))
                ) {
                    Text("DISCONNETTI", color = DangerRed, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            } else {
                Button(
                    onClick = onReconnect,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentCyan.copy(alpha = 0.2f))
                ) {
                    Text("RICONNETTI", color = AccentCyan, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // --- Embedded Diagnostic Log Section ---
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .clickable { showLogs = !showLogs },
            color = SurfaceDark,
            shape = RoundedCornerShape(18.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = null,
                        tint = TextSecondary
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Terminale Diagnostica CAN / OBD",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                }
                Icon(
                    imageVector = if (showLogs) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = TextSecondary
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
                    .height(200.dp)
                    .padding(top = 8.dp)
                    .background(Color.Black, RoundedCornerShape(16.dp))
                    .padding(12.dp)
            ) {
                LazyColumn(state = listState) {
                    items(liveState.logs) { logLine ->
                        Text(
                            text = logLine,
                            color = AccentCyan,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
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
    pulseScale: Float
) {
    val perf = liveState.performanceStatus
    val accel = liveState.accelerationState
    val isFullBoost = !liveState.batteryStatus.isThermalThrottled && (liveState.warmupStatus.stage == WarmupStage.S4 || liveState.warmupStatus.stage == WarmupStage.S2)

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {

        // --- GR Hero Speedometer & Sprint Timer Card ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Top Sub-Header with dynamic status badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFF1801))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "GAZOO RACING TELEMETRY",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFFFF5252),
                            letterSpacing = 1.sp
                        )
                    }

                    val badgeColor = when {
                        accel.isLaunchReady -> SuccessGreen
                        accel.isTimingActive -> WarningOrange
                        isFullBoost -> AccentCyan
                        else -> WarningOrange
                    }
                    val badgeText = when {
                        accel.isLaunchReady -> "🟢 LAUNCH READY"
                        accel.isTimingActive -> "⏱️ SCATTO ATTIVO"
                        isFullBoost -> "⚡ 59 kW FULL BOOST"
                        else -> "⚠️ DERATING ATTIVO"
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = badgeColor.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = badgeText,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            color = badgeColor
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Digital Speedometer & Live Timer Centerpiece
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFF070A0F),
                    border = BorderStroke(1.dp, Color(0xFF222B3D))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Digital Speedometer
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "VELOCITÀ",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextSecondary,
                                letterSpacing = 1.sp
                            )
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    text = "${accel.currentSpeedKmh}",
                                    fontSize = 52.sp,
                                    fontWeight = FontWeight.Black,
                                    color = if (accel.currentSpeedKmh > 0) Color.White else TextSecondary,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = " km/h",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFF5252),
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(60.dp)
                                .background(DividerColor)
                        )

                        // Live Sprint Timer
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = if (accel.isTimingActive) "SCATTO IN CORSO" else "ULTIMO TEMPO",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (accel.isTimingActive) WarningOrange else TextSecondary,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = if (accel.isTimingActive) {
                                    String.format("%.2fs", accel.elapsedMs / 1000f)
                                } else if (accel.last0to100TimeSec != null) {
                                    String.format("%.2fs", accel.last0to100TimeSec)
                                } else "--.--s",
                                fontSize = 36.sp,
                                fontWeight = FontWeight.Black,
                                color = if (accel.isTimingActive) WarningOrange else SuccessGreen,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Sprint Dragy-style Times (0-50 & 0-100 km/h)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
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

        // --- Engine Dynamics & Power Split Card ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = AccentCyan,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "RENDIMENTO TERMICO & ANTICIPO",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        color = TextSecondary,
                        letterSpacing = 0.8.sp
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TelemetryChip(
                        label = "Anticipo (°BTDC)",
                        value = if (perf.hasLiveData) "${String.format("%.1f", perf.timingAdvance)}°" else "--",
                        highlight = perf.hasLiveData && perf.timingAdvance >= 15f,
                        modifier = Modifier.weight(1f)
                    )
                    TelemetryChip(
                        label = "Carico Termico",
                        value = if (perf.hasLiveData) "${perf.engineLoadPercent.toInt()}%" else "--",
                        highlight = perf.hasLiveData && perf.engineLoadPercent > 70f,
                        modifier = Modifier.weight(1f)
                    )
                    TelemetryChip(
                        label = "Pedale Gas",
                        value = if (perf.hasLiveData) "${perf.throttlePercent.toInt()}%" else "--",
                        highlight = perf.hasLiveData && perf.throttlePercent > 50f,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = CardBackground
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = SuccessGreen,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Raffreddamento forzato attivo: zero calo di tensione per la batteria e 100% di coppia elettrica MG2 pronta.",
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            color = TextPrimary
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
    pulseScale: Float,
    onThresholdChanged: (Int) -> Unit,
    onForcedFanToggle: (Boolean) -> Unit
) {
    val isFanMax = liveState.batteryStatus.isFanForced || liveState.fanForcedMax
    val activeBgGradient = Brush.horizontalGradient(
        colors = listOf(Color(0xFF0066FF), Color(0xFF00E5FF))
    )
    val inactiveBgGradient = Brush.horizontalGradient(
        colors = listOf(CardBackground, SurfaceDark)
    )

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {

        // --- HUGE FAN OVERRIDE BUTTON ---
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(115.dp)
                .clip(RoundedCornerShape(24.dp))
                .border(
                    width = if (isFanMax) 2.dp else 1.dp,
                    color = if (isFanMax) AccentCyan else DividerColor,
                    shape = RoundedCornerShape(24.dp)
                )
                .clickable { onForcedFanToggle(!isFanMax) },
            color = Color.Transparent
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(if (isFanMax) activeBgGradient else inactiveBgGradient)
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(if (isFanMax) Color.White.copy(alpha = 0.2f) else CardBackground),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Air,
                                contentDescription = null,
                                tint = if (isFanMax) Color.White else TextSecondary,
                                modifier = Modifier.size(38.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column {
                            Text(
                                text = if (isFanMax) "VENTOLA HV AL 100%" else "VENTOLA AUTOMATICA",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                color = if (isFanMax) Color.White else TextPrimary,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = if (isFanMax) "Forzatura Livello 6 Attiva (Mode 30)" else "Attiva oltre la soglia impostata",
                                fontSize = 12.sp,
                                color = if (isFanMax) Color.White.copy(alpha = 0.85f) else TextSecondary
                            )
                        }
                    }

                    Switch(
                        checked = isFanMax,
                        onCheckedChange = { onForcedFanToggle(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF003399),
                            uncheckedThumbColor = TextSecondary,
                            uncheckedTrackColor = SurfaceDark
                        )
                    )
                }
            }
        }

        // --- BATTERY THERMALS & SENSORS CARD ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "TEMPERATURE BATTERIA TRAZIONE",
                        style = Typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary,
                        letterSpacing = 1.sp
                    )

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (liveState.batteryStatus.maxTemp > 35.0) WarningOrange.copy(alpha = 0.2f) else SuccessGreen.copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = if (liveState.batteryStatus.maxTemp > 35.0) "ATTENZIONE CALORE" else "STATO OTTIMALE",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (liveState.batteryStatus.maxTemp > 35.0) WarningOrange else SuccessGreen
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (liveState.isInitialized) "${String.format("%.1f", liveState.batteryStatus.maxTemp)}°C" else "--",
                            fontSize = 38.sp,
                            fontWeight = FontWeight.Black,
                            color = when {
                                !liveState.isInitialized -> TextSecondary
                                liveState.batteryStatus.maxTemp >= 35.0 -> WarningOrange
                                else -> AccentCyan
                            },
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "Temperatura Max",
                            style = Typography.labelSmall,
                            color = TextSecondary
                        )
                    }

                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(50.dp)
                            .background(DividerColor)
                    )

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (liveState.isInitialized) "LIVELLO ${liveState.batteryStatus.fanSpeedLevel}" else "OFF",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (liveState.batteryStatus.fanSpeedLevel > 0) SuccessGreen else TextSecondary,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "Velocità Ventola",
                            style = Typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
                HorizontalDivider(color = DividerColor)
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Singoli Moduli Batteria & Canale Aspirazione:",
                    style = Typography.labelSmall,
                    color = TextSecondary,
                    modifier = Modifier.padding(bottom = 10.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    SensorItem(label = "Mod. 1", value = liveState.batteryStatus.temp1, isReady = liveState.isInitialized)
                    SensorItem(label = "Mod. 2", value = liveState.batteryStatus.temp2, isReady = liveState.isInitialized)
                    SensorItem(label = "Mod. 3", value = liveState.batteryStatus.temp3, isReady = liveState.isInitialized)
                    SensorItem(label = "Mod. 4", value = liveState.batteryStatus.temp4, isReady = liveState.isInitialized)
                    SensorItem(label = "Aspiraz.", value = liveState.batteryStatus.intakeTemp, isReady = liveState.isInitialized)
                }
            }
        }

        // --- WARM-UP STAGES & EFFICIENCY TIPS CARD ---
        val warmup = liveState.warmupStatus
        val stageColor = when (warmup.stage) {
            WarmupStage.S0, WarmupStage.S1A -> WarningOrange
            WarmupStage.S1B, WarmupStage.S2 -> Color(0xFFFFEB3B)
            WarmupStage.S3 -> AccentCyan
            WarmupStage.S4 -> SuccessGreen
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(stageColor)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "FASE WARM-UP TERMICO",
                            style = Typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondary,
                            letterSpacing = 1.sp
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = stageColor.copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = warmup.stage.name,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            color = stageColor
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = warmup.stage.title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    color = TextPrimary
                )
                Text(
                    text = "${warmup.stage.subtitle} (${warmup.stage.targetTempDescription})",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Engine & Atmosphere Telemetry Badges
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TelemetryChip(
                        label = "Liquido (ECT)",
                        value = if (warmup.hasLiveData) "${warmup.coolantTemp.toInt()}°C" else "--",
                        highlight = warmup.hasLiveData && warmup.coolantTemp >= 73f,
                        modifier = Modifier.weight(1f)
                    )
                    TelemetryChip(
                        label = "Aria Est. (IAT)",
                        value = if (warmup.hasLiveData) "${warmup.ambientTemp.toInt()}°C" else "--",
                        highlight = false,
                        modifier = Modifier.weight(1f)
                    )
                    TelemetryChip(
                        label = "Giri Motore",
                        value = if (warmup.hasLiveData) {
                            if (warmup.engineRpm > 0) "${warmup.engineRpm}" else "EV / Spento"
                        } else "--",
                        highlight = warmup.hasLiveData && warmup.engineRpm == 0 && warmup.stage == WarmupStage.S4,
                        modifier = Modifier.weight(1.1f)
                    )
                }

                if (warmup.recommendations.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = CardBackground
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.FlashOn,
                                    contentDescription = null,
                                    tint = AccentCyan,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "CONSIGLI DI RISCALDAMENTO RAPIDO",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    color = AccentCyan,
                                    letterSpacing = 0.8.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            warmup.recommendations.forEach { tip ->
                                Text(
                                    text = "• $tip",
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp,
                                    color = TextPrimary,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // --- TARGET THRESHOLD SLIDER & PRESETS ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "SOGLIA AUTOMATICA ATTIVAZIONE",
                        style = Typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "${liveState.targetThreshold}°C",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        color = AccentCyan,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Slider(
                    value = liveState.targetThreshold.toFloat(),
                    onValueChange = { onThresholdChanged(it.toInt()) },
                    valueRange = 15f..40f,
                    steps = 24,
                    colors = SliderDefaults.colors(
                        thumbColor = AccentCyan,
                        activeTrackColor = AccentCyan,
                        inactiveTrackColor = CardBackground
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

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
                        label = "25°C (Ideale)",
                        isSelected = liveState.targetThreshold == 25,
                        modifier = Modifier.weight(1f),
                        onClick = { onThresholdChanged(25) }
                    )
                    PresetButton(
                        label = "30°C (Silenzio)",
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
 * 3. HELPER COMPOSABLES & LOGOS
 * =========================================================
 */

@Composable
fun GazooRacingLogoBadge() {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color.Black,
        border = BorderStroke(1.dp, Color(0xFF3A4456))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // White G box
            Text(
                text = "G",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace
            )
            // Red R box
            Surface(
                shape = RoundedCornerShape(3.dp),
                color = Color(0xFFFF1801),
                modifier = Modifier.padding(start = 2.dp)
            ) {
                Text(
                    text = "R",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(horizontal = 3.dp, vertical = 0.dp)
                )
            }
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
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF0D121B),
        border = BorderStroke(1.dp, Color(0xFF222B3D))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                color = Color(0xFFFF5252),
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (lastTime != null) String.format("%.2fs", lastTime) else "--.--s",
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                color = if (lastTime != null) TextPrimary else TextSecondary,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "🏆 RECORD: ",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary
                )
                Text(
                    text = if (bestTime != null) String.format("%.2fs", bestTime) else "--",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    color = SuccessGreen,
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
    Surface(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() },
        color = if (isSelected) AccentCyan else CardBackground,
        shape = RoundedCornerShape(12.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) DarkBackground else TextPrimary,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun SensorItem(label: String, value: Double, isReady: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = Typography.labelSmall,
            color = TextSecondary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = if (isReady) "${value.toInt()}°" else "--",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
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
        shape = RoundedCornerShape(14.dp),
        color = CardBackground
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                fontSize = 11.sp,
                color = TextSecondary,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = if (highlight) SuccessGreen else TextPrimary
            )
        }
    }
}

@Composable
fun ConnectionBadge(connectionState: BleConnectionState) {
    val bgColor: Color
    val text: String
    when (connectionState) {
        is BleConnectionState.Ready -> {
            bgColor = SuccessGreen
            text = "CONNESSO"
        }
        is BleConnectionState.Connected -> {
            bgColor = AccentCyan
            text = "LINK OBD"
        }
        is BleConnectionState.Connecting -> {
            bgColor = WarningOrange
            text = "CONNESSIONE..."
        }
        is BleConnectionState.Scanning -> {
            bgColor = WarningOrange
            text = "SCANSIONE..."
        }
        is BleConnectionState.Error -> {
            bgColor = DangerRed
            text = "ERRORE"
        }
        else -> {
            bgColor = DividerColor
            text = "DISCONNESSO"
        }
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bgColor.copy(alpha = 0.2f))
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Text(
            text = text,
            color = bgColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )
    }
}
