package com.yaris.hvfan.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yaris.hvfan.R
import com.yaris.hvfan.obd.*
import com.yaris.hvfan.ui.theme.*

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
    onForcedFanToggle: (Boolean) -> Unit,
    onAutoCoolingToggle: (Boolean) -> Unit = {},
    onAutoCoolingTriggerChanged: (Float) -> Unit = {},
    onAutoCoolingHysteresisChanged: (Float) -> Unit = {},
    onAutoCoolingTargetSpeedChanged: (Int) -> Unit = {},
    onManualFanLevelChanged: (Int) -> Unit = {}
) {
    val isForced = liveState.isManualFanForced || liveState.fanForcedMax
    val isFanMax = isConnected && (liveState.batteryStatus.isFanForced || isForced)
    val bat = liveState.batteryStatus
    val deltaT = if (isConnected && liveState.hasEcuCommunication && bat.maxTemp > 0.0) {
        val temps = listOf(bat.temp1, bat.temp2, bat.temp3, bat.temp4).filter { it > 0.0 }
        if (temps.isNotEmpty()) (temps.maxOrNull() ?: 0.0) - (temps.minOrNull() ?: 0.0) else 0.0
    } else 0.0

    val haptic = LocalHapticFeedback.current
    val currentTarget = liveState.manualFanTargetLevel

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
                                text = "FORZATURA ATTIVA VENTOLA",
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
                                    text = if (isFanMax) "UDS L${liveState.manualFanTargetLevel} FORZATA" else "AUTO OEM",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = if (isFanMax) AccentCyan else TextSecondary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = if (isFanMax) "Forzatura attiva (L${liveState.manualFanTargetLevel} / 6 • ~${liveState.manualFanTargetLevel * 750} RPM)" else "Controllo automatico OEM attivo",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }

                    Switch(
                        checked = isForced,
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

                // --- 🎛️ STEPPER SELETTORE LIVELLO L1–L6 ---
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DarkBackground, RoundedCornerShape(6.dp))
                        .border(1.dp, CardBorder, RoundedCornerShape(6.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "LIVELLO FORZATURA:",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondary,
                            letterSpacing = 0.8.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "L$currentTarget",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            color = if (isForced) AccentCyan else TextPrimary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "(~${currentTarget * 750} RPM)",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = TextMuted
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Pulsante (-)
                        Surface(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .clickable(enabled = currentTarget > 1) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    val newLevel = (currentTarget - 1).coerceIn(1, 6)
                                    onManualFanLevelChanged(newLevel)
                                },
                            color = if (currentTarget > 1) SurfaceDark else DarkBackground,
                            border = BorderStroke(1.dp, if (currentTarget > 1) CardBorder else CardBorder.copy(alpha = 0.3f)),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "−",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Black,
                                    color = if (currentTarget > 1) TextPrimary else TextMuted
                                )
                            }
                        }

                        // Pulsante (+)
                        Surface(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .clickable(enabled = currentTarget < 6) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    val newLevel = (currentTarget + 1).coerceIn(1, 6)
                                    onManualFanLevelChanged(newLevel)
                                },
                            color = if (currentTarget < 6) SurfaceDark else DarkBackground,
                            border = BorderStroke(1.dp, if (currentTarget < 6) AccentCyan.copy(alpha = 0.5f) else CardBorder.copy(alpha = 0.3f)),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "+",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Black,
                                    color = if (currentTarget < 6) AccentCyan else TextMuted
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Quick Selector L1-L6 Discrete Buttons Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    for (lvl in 1..6) {
                        val isSelected = isForced && currentTarget == lvl
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .height(32.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onManualFanLevelChanged(lvl)
                                },
                            color = if (isSelected) AccentCyan else DarkBackground,
                            border = BorderStroke(1.dp, if (isSelected) AccentCyan else CardBorder),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "L$lvl",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = if (isSelected) DarkBackground else TextPrimary
                                )
                            }
                        }
                    }
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

                // 6 Segment bars (interactive quick selection)
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
                                .height(14.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onManualFanLevelChanged(level)
                                }
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

        // --- 4. SMART AUTO-COOLING PROTECTION (PREDITTIVA CON ISTERESI REGOLABILE) ---
        val autoCool = liveState.autoCoolingStatus
        val haptic = LocalHapticFeedback.current

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(6.dp),
            color = SurfaceDark,
            border = BorderStroke(1.dp, if (autoCool.isEnabled && autoCool.isActivelyCooling) SuccessGreen else if (autoCool.isEnabled) AccentCyan else CardBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Header & Toggle Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "SMART AUTO-COOLING PROTECTION",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                color = TextPrimary,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(3.dp),
                                color = DarkBackground,
                                border = BorderStroke(
                                    1.dp,
                                    when {
                                        !autoCool.isEnabled -> CardBorder
                                        autoCool.isActivelyCooling -> SuccessGreen
                                        else -> AccentCyan
                                    }
                                )
                            ) {
                                Text(
                                    text = when {
                                        !autoCool.isEnabled -> "OFF"
                                        autoCool.isActivelyCooling -> "RAFFREDDAMENTO (L${autoCool.targetSpeed})"
                                        else -> "STANDBY SOGLIA"
                                    },
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = when {
                                        !autoCool.isEnabled -> TextMuted
                                        autoCool.isActivelyCooling -> SuccessGreen
                                        else -> AccentCyan
                                    },
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "Protezione termica predittiva con isteresi e monitoraggio continuo in background",
                            fontSize = 11.sp,
                            color = TextSecondary,
                            lineHeight = 14.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Switch(
                        checked = autoCool.isEnabled,
                        onCheckedChange = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onAutoCoolingToggle(it)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = AccentCyan,
                            checkedTrackColor = DarkBackground,
                            checkedBorderColor = AccentCyan,
                            uncheckedThumbColor = TextMuted,
                            uncheckedTrackColor = DarkBackground,
                            uncheckedBorderColor = CardBorder
                        )
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 1. Slider Soglia Trigger (28°C - 42°C)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "TEMPERATURA DI INNESCO (ON)",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondary,
                            letterSpacing = 0.8.sp
                        )
                        Text(
                            text = "Invia comando Mode 30 al raggiungimento della soglia",
                            fontSize = 10.sp,
                            color = TextMuted
                        )
                    }
                    Text(
                        text = "${String.format(java.util.Locale.US, "%.1f", autoCool.triggerTemp)}°C",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        color = if (autoCool.isEnabled) AccentCyan else TextMuted,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Slider(
                    value = autoCool.triggerTemp,
                    onValueChange = {
                        val rounded = Math.round(it * 2f) / 2f
                        onAutoCoolingTriggerChanged(rounded)
                    },
                    valueRange = 28f..42f,
                    steps = 27,
                    enabled = autoCool.isEnabled,
                    colors = SliderDefaults.colors(
                        thumbColor = AccentCyan,
                        activeTrackColor = AccentCyan,
                        inactiveTrackColor = DarkBackground
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                // 2. Slider Isteresi (1.0°C - 5.0°C)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "ISTERESI DI SPEGNIMENTO (OFF)",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondary,
                            letterSpacing = 0.8.sp
                        )
                        Text(
                            text = "Rilascia alla centralina a ${String.format(java.util.Locale.US, "%.1f", autoCool.cutoffTemp)}°C (delta -${String.format(java.util.Locale.US, "%.1f", autoCool.hysteresis)}°C)",
                            fontSize = 10.sp,
                            color = if (autoCool.isEnabled) SuccessGreen else TextMuted
                        )
                    }
                    Text(
                        text = "Δ ${String.format(java.util.Locale.US, "%.1f", autoCool.hysteresis)}°C",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        color = if (autoCool.isEnabled) WarningOrange else TextMuted,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Slider(
                    value = autoCool.hysteresis,
                    onValueChange = {
                        val rounded = Math.round(it * 2f) / 2f
                        onAutoCoolingHysteresisChanged(rounded)
                    },
                    valueRange = 1f..5f,
                    steps = 7,
                    enabled = autoCool.isEnabled,
                    colors = SliderDefaults.colors(
                        thumbColor = WarningOrange,
                        activeTrackColor = WarningOrange,
                        inactiveTrackColor = DarkBackground
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                // 3. Velocità Ventola Target (Livelli 1-6)
                Text(
                    text = "VELOCITÀ BERSAGLIO VENTOLA",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary,
                    letterSpacing = 0.8.sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    for (speed in 1..6) {
                        val isSelected = autoCool.targetSpeed == speed
                        val speedColor = if (speed <= 2) SuccessGreen else if (speed <= 4) AccentCyan else WarningOrange
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .height(32.dp)
                                .clickable(enabled = autoCool.isEnabled) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onAutoCoolingTargetSpeedChanged(speed)
                                },
                            shape = RoundedCornerShape(4.dp),
                            color = if (isSelected && autoCool.isEnabled) speedColor else DarkBackground,
                            border = BorderStroke(1.dp, if (isSelected && autoCool.isEnabled) speedColor else CardBorder)
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "L$speed",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace,
                                    color = if (isSelected && autoCool.isEnabled) Color.Black else if (autoCool.isEnabled) TextPrimary else TextMuted
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 4. Preset Rapidi
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PresetButton(
                        label = "🏁 Track (30°C/L6)",
                        isSelected = autoCool.triggerTemp == 30f && autoCool.targetSpeed == 6 && autoCool.hysteresis == 2f,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            onAutoCoolingTriggerChanged(30.0f)
                            onAutoCoolingHysteresisChanged(2.0f)
                            onAutoCoolingTargetSpeedChanged(6)
                            if (!autoCool.isEnabled) onAutoCoolingToggle(true)
                        }
                    )
                    PresetButton(
                        label = "⚖️ Bilanciato (33.5°/L6)",
                        isSelected = autoCool.triggerTemp == 33.5f && autoCool.targetSpeed == 6 && autoCool.hysteresis == 2f,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            onAutoCoolingTriggerChanged(33.5f)
                            onAutoCoolingHysteresisChanged(2.0f)
                            onAutoCoolingTargetSpeedChanged(6)
                            if (!autoCool.isEnabled) onAutoCoolingToggle(true)
                        }
                    )
                    PresetButton(
                        label = "🍃 Comfort (36°/L4)",
                        isSelected = autoCool.triggerTemp == 36f && autoCool.targetSpeed == 4 && autoCool.hysteresis == 2.5f,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            onAutoCoolingTriggerChanged(36.0f)
                            onAutoCoolingHysteresisChanged(2.5f)
                            onAutoCoolingTargetSpeedChanged(4)
                            if (!autoCool.isEnabled) onAutoCoolingToggle(true)
                        }
                    )
                }
            }
        }
    }
}

