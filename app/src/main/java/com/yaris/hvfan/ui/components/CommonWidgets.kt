package com.yaris.hvfan.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yaris.hvfan.R
import com.yaris.hvfan.ble.BleConnectionState
import com.yaris.hvfan.ui.theme.*

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
    hasEcuCommunication: Boolean = false,
    isVehicleReady: Boolean = false,
    isStandbyMode: Boolean = false,
    auxiliary12vVoltage: Float = 0f
) {
    val borderColor: Color
    val textColor: Color
    val text: String
    when (connectionState) {
        is BleConnectionState.Ready, is BleConnectionState.Connected -> {
            if (hasEcuCommunication) {
                borderColor = SuccessGreen
                textColor = SuccessGreen
                text = if (auxiliary12vVoltage > 0f) {
                    "● READY ONLINE (${String.format(java.util.Locale.US, "%.1f", auxiliary12vVoltage)}V)"
                } else {
                    "● ECU ONLINE"
                }
            } else if (isStandbyMode) {
                borderColor = WarningOrange
                textColor = WarningOrange
                text = if (auxiliary12vVoltage > 0f) {
                    "▲ STANDBY (${String.format(java.util.Locale.US, "%.1f", auxiliary12vVoltage)}V)"
                } else {
                    "▲ STANDBY - ATTESA READY"
                }
            } else if (isInitialized) {
                if (isVehicleReady || auxiliary12vVoltage >= 13.0f) {
                    borderColor = AccentCyan
                    textColor = AccentCyan
                    text = if (auxiliary12vVoltage > 0f) {
                        "◌ SINCRONIZZAZIONE (${String.format(java.util.Locale.US, "%.1f", auxiliary12vVoltage)}V)"
                    } else {
                        "◌ SINCRONIZZAZIONE"
                    }
                } else {
                    borderColor = WarningOrange
                    textColor = WarningOrange
                    text = if (auxiliary12vVoltage > 0f) {
                        "▲ DONGLE OK (${String.format(java.util.Locale.US, "%.1f", auxiliary12vVoltage)}V)"
                    } else {
                        "▲ DONGLE OK - ATTESA ECU"
                    }
                }
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
            text = "○ AUTO-RETRY #${connectionState.attempt}"
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
