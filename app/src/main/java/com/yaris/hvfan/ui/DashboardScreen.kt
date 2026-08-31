package com.yaris.hvfan.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yaris.hvfan.ble.BleConnectionState
import com.yaris.hvfan.obd.ObdLiveState
import com.yaris.hvfan.obd.WarmupStage
import com.yaris.hvfan.ui.theme.*

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
    var showLogs by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    // Pulse animation for active fan
    val infiniteTransition = rememberInfiniteTransition(label = "FanPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Scale"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(16.dp)
            .verticalScroll(scrollState)
    ) {
        // --- 1. Top Header Bar ---
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = SurfaceDark,
            tonalElevation = 4.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "YARIS HYBRID MK4",
                        style = Typography.headlineMedium,
                        fontWeight = FontWeight.Black,
                        color = TextPrimary,
                        letterSpacing = 1.2.sp
                    )
                    Text(
                        text = savedDeviceName ?: "Nessun dongle associato",
                        style = Typography.bodyMedium,
                        color = AccentCyan
                    )
                }

                ConnectionBadge(connectionState = connectionState)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- 2. HUGE FAN MAX OVERRIDE BUTTON (DRIVER FRIENDLY) ---
        val isFanMax = liveState.batteryStatus.isFanForced || liveState.fanForcedMax
        val activeBgGradient = Brush.horizontalGradient(
            colors = listOf(Color(0xFF0066FF), Color(0xFF00E5FF))
        )
        val inactiveBgGradient = Brush.horizontalGradient(
            colors = listOf(CardBackground, SurfaceDark)
        )

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
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(if (isFanMax) Color.White.copy(alpha = 0.25f) else CardBackground),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Air,
                                contentDescription = null,
                                tint = if (isFanMax) Color.White else TextSecondary,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = if (isFanMax) "VENTOLA AL MASSIMO" else "MODALITÀ STANDARD",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                color = if (isFanMax) Color.White else TextPrimary
                            )
                            Text(
                                text = if (isFanMax) "Livello 6 (100% Attivo)" else "Tocca per forzare al 100%",
                                fontSize = 13.sp,
                                color = if (isFanMax) Color.White.copy(alpha = 0.9f) else TextSecondary
                            )
                        }
                    }

                    Switch(
                        checked = isFanMax,
                        onCheckedChange = { onForcedFanToggle(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF0038A8),
                            uncheckedThumbColor = TextSecondary,
                            uncheckedTrackColor = SurfaceDark
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- 3. BATTERY TEMPERATURE CARD ---
        val maxTemp = liveState.batteryStatus.maxTemp
        val tempColor = when {
            maxTemp >= 40 -> DangerRed
            maxTemp >= 32 -> WarningOrange
            maxTemp >= 25 -> AccentCyan
            else -> SuccessGreen
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = CardBackground)
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
                        text = "TEMPERATURA BATTERIA DI TRAZIONE",
                        style = Typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary,
                        letterSpacing = 1.sp
                    )
                    Icon(
                        imageVector = Icons.Default.Thermostat,
                        contentDescription = null,
                        tint = tempColor,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        text = if (liveState.isInitialized) String.format("%.1f", maxTemp) else "--.-",
                        fontSize = 54.sp,
                        fontWeight = FontWeight.Black,
                        color = tempColor
                    )
                    Text(
                        text = " °C (Punto più caldo)",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextSecondary,
                        modifier = Modifier.padding(bottom = 12.dp, start = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = DividerColor)
                Spacer(modifier = Modifier.height(14.dp))

                // Sensors Grid
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

        Spacer(modifier = Modifier.height(16.dp))

        // --- 3. WARM-UP STAGES & SMART EFFICIENCY TIPS ---
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
                        shape = RoundedCornerShape(12.dp),
                        color = stageColor.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = warmup.stage.name,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            color = stageColor
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = warmup.stage.title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = warmup.stage.subtitle,
                    fontSize = 13.sp,
                    color = TextSecondary
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Progress Bar toward Stage 4 (Full Hybrid Efficiency)
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Progresso Riscaldamento",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                        Text(
                            text = "${(warmup.progressPercent * 100).toInt()}%",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = stageColor
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = warmup.progressPercent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = stageColor,
                        trackColor = DividerColor
                    )
                }

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

        Spacer(modifier = Modifier.height(16.dp))

        // --- 4. GAZOO RACING COCKPIT & SPRINT TIMER (0-50 / 0-100 km/h) ---
        val perf = liveState.performanceStatus
        val accel = liveState.accelerationState
        val isFullBoost = !liveState.batteryStatus.isThermalThrottled && (liveState.warmupStatus.stage == WarmupStage.S4 || liveState.warmupStatus.stage == WarmupStage.S2)

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
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = null,
                            tint = Color(0xFFFF3D00), // GR Racing Red
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "GR COCKPIT & SPRINT TIMER",
                            style = Typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            color = TextSecondary,
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
                        accel.isTimingActive -> "⏱️ SCATTO IN CORSO"
                        isFullBoost -> "⚡ 100% BOOST READY"
                        else -> "⚠️ TAGLIO TERMICO"
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
                    shape = RoundedCornerShape(18.dp),
                    color = Color.Black
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
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
                                    fontSize = 44.sp,
                                    fontWeight = FontWeight.Black,
                                    color = if (accel.currentSpeedKmh > 0) Color.White else TextSecondary,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                )
                                Text(
                                    text = " km/h",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AccentCyan,
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(50.dp)
                                .background(DividerColor)
                        )

                        // Live Sprint Timer
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = if (accel.isTimingActive) "TIMER SCATTO" else "CRONOMETRO",
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
                                fontSize = 34.sp,
                                fontWeight = FontWeight.Black,
                                color = if (accel.isTimingActive) WarningOrange else SuccessGreen,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
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
                        title = "SCATTO 0-50 km/h",
                        lastTime = accel.last0to50TimeSec,
                        bestTime = accel.best0to50TimeSec,
                        modifier = Modifier.weight(1f)
                    )
                    SprintScoreCard(
                        title = "SCATTO 0-100 km/h",
                        lastTime = accel.last0to100TimeSec,
                        bestTime = accel.best0to100TimeSec,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Engine Performance & Ignition Advance Telemetry
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
                        label = "Carico Motore",
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
                            text = "Launch Control automatico: fermati a 0 km/h per armare il timer, premi tutto il gas e misura il tempo!",
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            color = TextPrimary
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
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
                        text = "${liveState.targetThreshold} °C",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        color = AccentCyan
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Large Touch Preset Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PresetButton(
                        label = "20°C (Consigliato)",
                        isSelected = liveState.targetThreshold == 20,
                        modifier = Modifier.weight(1.3f),
                        onClick = { onThresholdChanged(20) }
                    )
                    PresetButton(
                        label = "25°C",
                        isSelected = liveState.targetThreshold == 25,
                        modifier = Modifier.weight(0.9f),
                        onClick = { onThresholdChanged(25) }
                    )
                    PresetButton(
                        label = "30°C",
                        isSelected = liveState.targetThreshold == 30,
                        modifier = Modifier.weight(0.9f),
                        onClick = { onThresholdChanged(30) }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Slider with large step controls
                Slider(
                    value = liveState.targetThreshold.toFloat(),
                    onValueChange = { onThresholdChanged(it.toInt()) },
                    valueRange = 15f..35f,
                    steps = 19,
                    colors = SliderDefaults.colors(
                        thumbColor = AccentCyan,
                        activeTrackColor = AccentCyan,
                        inactiveTrackColor = DividerColor
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // --- 5. EXTRA LARGE DRIVER ACTION BUTTONS ---
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (connectionState is BleConnectionState.Ready || connectionState is BleConnectionState.Connected) {
                Button(
                    onClick = onDisconnect,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DangerRed.copy(alpha = 0.85f)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.PowerSettingsNew, contentDescription = null, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("DISCONNETTI ADATTATORE", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            } else {
                Button(
                    onClick = onReconnect,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    enabled = savedDeviceMac != null,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("RICONNETTI SUBITO", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }

            OutlinedButton(
                onClick = onOpenDevicePicker,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                border = androidx.compose.foundation.BorderStroke(1.dp, DividerColor),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.BluetoothSearching, contentDescription = null, tint = AccentCyan)
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (savedDeviceMac == null) "ASSOCIA ADATTATORE OBD (BLE)" else "CAMBIA ADATTATORE OBD", fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- 6. DIAGNOSTICS LOG TERMINAL ---
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .clickable { showLogs = !showLogs },
            color = SurfaceDark
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
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Log Diagnostica CAN / OBD",
                        style = Typography.bodyMedium,
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
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
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
        color = CardBackground
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                color = AccentCyan,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (lastTime != null) String.format("%.2fs", lastTime) else "--.--s",
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                color = if (lastTime != null) TextPrimary else TextSecondary,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
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
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
            }
        }
    }
}

