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
                    PresetButton(
                        label = "10 secondi",
                        isSelected = stateDraft.rearCameraDelay == CameraOffDelay.SEC_10,
                        modifier = Modifier.weight(1f),
                        onClick = { stateDraft = stateDraft.copy(rearCameraDelay = CameraOffDelay.SEC_10) }
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
                    PresetButton(label = "6 Lampeggi", isSelected = stateDraft.turnSignalFlashes == TurnSignalFlashes.FLASHES_6, modifier = Modifier.weight(1f), onClick = { stateDraft = stateDraft.copy(turnSignalFlashes = TurnSignalFlashes.FLASHES_6) })
                    PresetButton(label = "Disattivato", isSelected = stateDraft.turnSignalFlashes == TurnSignalFlashes.OFF, modifier = Modifier.weight(1f), onClick = { stateDraft = stateDraft.copy(turnSignalFlashes = TurnSignalFlashes.OFF) })
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
                    PresetButton(label = "90s", isSelected = stateDraft.followMeHome == FollowMeHomeDuration.SEC_90, modifier = Modifier.weight(1f), onClick = { stateDraft = stateDraft.copy(followMeHome = FollowMeHomeDuration.SEC_90) })
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

                Spacer(modifier = Modifier.height(14.dp))
                CodingSwitchRow(
                    label = "RCTA: Allerta Traffico Posteriore",
                    checked = stateDraft.rctaEnabled,
                    onCheckedChange = { stateDraft = stateDraft.copy(rctaEnabled = it) }
                )
                CodingSwitchRow(
                    label = "LTA: Mantenimento Corsia",
                    checked = stateDraft.ltaEnabled,
                    onCheckedChange = { stateDraft = stateDraft.copy(ltaEnabled = it) }
                )
                CodingSwitchRow(
                    label = "PCS: Ricorda Ultimo Stato",
                    checked = stateDraft.pcsRememberLast,
                    onCheckedChange = { stateDraft = stateDraft.copy(pcsRememberLast = it) }
                )
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
                CodingSwitchRow(
                    label = "Ventilatore su Sbrinatore",
                    checked = stateDraft.blowerOnDefroster,
                    onCheckedChange = { stateDraft = stateDraft.copy(blowerOnDefroster = it) }
                )

                Spacer(modifier = Modifier.height(14.dp))
                Text(text = "Calibrazione Temperatura Clima", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PresetButton(label = "-2°C", isSelected = stateDraft.temperatureCalibration == TemperatureCalibration.MINUS_2, modifier = Modifier.weight(1f), onClick = { stateDraft = stateDraft.copy(temperatureCalibration = TemperatureCalibration.MINUS_2) })
                    PresetButton(label = "-1°C", isSelected = stateDraft.temperatureCalibration == TemperatureCalibration.MINUS_1, modifier = Modifier.weight(1f), onClick = { stateDraft = stateDraft.copy(temperatureCalibration = TemperatureCalibration.MINUS_1) })
                    PresetButton(label = "0°C", isSelected = stateDraft.temperatureCalibration == TemperatureCalibration.ZERO, modifier = Modifier.weight(1f), onClick = { stateDraft = stateDraft.copy(temperatureCalibration = TemperatureCalibration.ZERO) })
                    PresetButton(label = "+1°C", isSelected = stateDraft.temperatureCalibration == TemperatureCalibration.PLUS_1, modifier = Modifier.weight(1f), onClick = { stateDraft = stateDraft.copy(temperatureCalibration = TemperatureCalibration.PLUS_1) })
                    PresetButton(label = "+2°C", isSelected = stateDraft.temperatureCalibration == TemperatureCalibration.PLUS_2, modifier = Modifier.weight(1f), onClick = { stateDraft = stateDraft.copy(temperatureCalibration = TemperatureCalibration.PLUS_2) })
                }
            }
        }
    }
}

