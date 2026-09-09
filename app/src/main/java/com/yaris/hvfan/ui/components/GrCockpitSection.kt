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
import androidx.compose.ui.res.painterResource
import com.yaris.hvfan.R
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yaris.hvfan.obd.*
import com.yaris.hvfan.ui.theme.*

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
                        liveState.isStandbyMode -> Pair(WarningOrange, "ATTESA READY")
                        !liveState.hasEcuCommunication && !liveState.isVehicleReady -> Pair(WarningOrange, "ATTESA READY")
                        !liveState.hasEcuCommunication && liveState.isVehicleReady -> Pair(AccentCyan, "SINCRONIZZAZIONE")
                        accel.isLaunchReady -> Pair(SuccessGreen, "LAUNCH READY")
                        accel.isTimingActive -> Pair(WarningOrange, "SCATTO ATTIVO")
                        isFullBoost -> Pair(AccentCyan, "FULL BOOST 59kW")
                        liveState.batteryStatus.isThermalThrottled -> Pair(DangerRed, "TAGLIO TERMICO")
                        else -> Pair(SuccessGreen, "ONLINE")
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
