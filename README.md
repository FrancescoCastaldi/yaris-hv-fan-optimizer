# Toyota Yaris MK4 Hybrid - HV Battery Cooling, GR Cockpit & ECU Coding Suite 🏎️⚡

[![Website](https://img.shields.io/badge/Website-Live%20Portal-00E5FF.svg?style=for-the-badge&logo=googlechrome)](https://francescocastaldi.github.io/yaris-hv-fan-optimizer/)
[![Download APK](https://img.shields.io/badge/Download-APK%20Release%20(v2.9.1)-D71920.svg?style=for-the-badge&logo=android)](https://francescocastaldi.github.io/yaris-hv-fan-optimizer/YarisHvFanControl-v2.9.1.apk)
[![Android](https://img.shields.io/badge/Platform-Android%208.0%2B%20(API%2026%2B)-3DDC84.svg?style=flat&logo=android)](https://www.android.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.24-7F52FF.svg?style=flat&logo=kotlin)](https://kotlinlang.org/)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose%20M3-4285F4.svg?style=flat&logo=jetpackcompose)](https://developer.android.com/jetpack/compose)
[![Tests](https://img.shields.io/badge/Tests-100%25%20Passing-brightgreen.svg?style=flat&logo=githubactions)](https://github.com/FrancescoCastaldi/yaris-hv-fan-optimizer)
[![RSA Signed](https://img.shields.io/badge/Signature-RSA%202048--bit%20(V1/V2/V3/V4)-00E676.svg?style=flat&logo=letsencrypt)](yaris_release.keystore)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

Applicazione Android nativa ad altissime prestazioni per **Toyota Yaris MK4 Hybrid (Piattaforma XP210 / TNGA-B, MY2020 - MY2025+)**. Interagisce via Bluetooth Low Energy (BLE) o Bluetooth Classic SPP con l'infrastruttura CAN bus dell'auto per offrire:
1. **Sfondo Esclusivo Motorsport Micro-Twill Carbon Fiber (Novità v2.9.1)**: Texture in fibra di carbonio 2x2 ultra-leggera ad accelerazione hardware nativa sia nell'app che nel portale web, con vignettatura OLED radiale morbida ad alto contrasto;
2. **Smart Auto-Cooling Protection Suite (v2.9.0)**: Controllo termico predittivo con soglia regolabile (28°C–42°C), isteresi di spegnimento (1°C–5°C), selettore di velocità bersaglio (L1–L6), esecuzione continua in background 24/7 con segnale audio e vibrazione haptic all'innesco;
3. **Nuovo Logo & Palette Gazoo Racing Heritage Motorsport** (Fibra di carbonio, alluminio spazzolato, zero glow AI);
4. **Telemetria MoTeC / Gazoo Racing & Cronometro Dragy 0-100 km/h**;
5. **Gestione Termica Attiva & Forzatura Ventola Batteria HV Denso con Closed-Loop ECU ACK e Hall RPM**;
6. **Suite Completa di Codifiche Centralina ECU UDS** (Toyota Touch 3, Bip retromarcia comfort, Chiusura porte, Alzacristalli da chiave, Frecce comfort e ADAS);
7. **Resilienza di Connettività Backend a Prova di Bomba & Auto-Recovery CAN Bus Trasparente**.

---

## 🌐 Sito Web Ufficiale & Download Diretto
- **Portale Web Ufficiale**: 👉 **[https://francescocastaldi.github.io/yaris-hv-fan-optimizer/](https://francescocastaldi.github.io/yaris-hv-fan-optimizer/)**
- **Simulatore Interattivo Web**: 👉 **[https://francescocastaldi.github.io/yaris-hv-fan-optimizer/preview.html](https://francescocastaldi.github.io/yaris-hv-fan-optimizer/preview.html)**
- **Download Diretto Ultimo APK (v2.9.1)**: 👉 **[Scarica YarisHvFanControl-v2.9.1.apk](https://francescocastaldi.github.io/yaris-hv-fan-optimizer/YarisHvFanControl-v2.9.1.apk)** (disponibile anche come [YarisHvFanControl.apk](https://francescocastaldi.github.io/yaris-hv-fan-optimizer/YarisHvFanControl.apk))
- **Deploy su Vercel (1-Click)**: 👉 **[Deploy Vercel](https://vercel.com/new/clone?repository-url=https://github.com/FrancescoCastaldi/yaris-hv-fan-optimizer&root-directory=docs)**

---

## 🌟 Architettura a 3 Schede (Release v2.9.1 MoTeC Motorsport Edition)

### 1. 🏁 Scheda `COCKPIT` (Telemetria & Prestazioni)
- **Logo Ufficiale Toyota Gazoo Racing "GR"**: Badge vettoriale originale ad alto contrasto con contorni bianchi nitidi su sfondo Dark/OLED.
- **Tachimetro Digitale Gigante (52sp)**: Lettura in tempo reale della velocità reale da CAN bus (`PID 010D`).
- **Launch Control Light Automatico**: Indicatore `[🟢 LAUNCH READY]` a 0 km/h e `[⏱️ SCATTO IN CORSO]` al primo tocco dell'acceleratore.
- **Cronometro Dragy 0-50 km/h e 0-100 km/h**: Misurazione automatica dello scatto con memorizzazione persistente del **Personal Best (PB)**.
- **Telemetria Motore Termico M15A-FXE**: Anticipo di accensione reale (`PID 010E` °BTDC), carico motore (`PID 0104` %) e posizione farfalla (`PID 0111` %).

### 2. 🌀 Scheda `VENTOLA` (Smart Auto-Cooling, Termica & Sicurezza Ibrida)
- **Smart Auto-Cooling Protection System**:
  - Slider continuo per la temperatura di innesco (28.0°C – 42.0°C, step 0.5°C).
  - Slider isteresi di spegnimento regolabile (1.0°C – 5.0°C) per prevenire oscillazioni on/off.
  - Selettore velocità ventola target da Livello 1 a Livello 6 con preset rapidi (*Gazoo Track*, *Bilanciato*, *Comfort*).
  - Monitoraggio attivo 24/7 in background tramite `ForegroundService` con segnale acustico e haptic all'innesco.
- **Forzatura Attiva Ventola Denso (Livello 6 MAX)**: Invia frame UDS IO Control Mode 30 (`300806`) per raffreddare istantaneamente il pacco batteria.
- **Prevenzione Tagli Termici (Zero Derating)**: Mantiene le celle tra 22°C e 26°C, scongiurando il taglio di coppia da 59 kW e della frenata rigenerativa sopra i 36°C.
- **Monitoraggio 4 Sonde Celle**: Lettura in tempo reale di tutte le temperature del pacco e della temperatura di aspirazione (`PID 2228C1`).
- **Analisi Fasi Warm-Up (S0 ➔ S4)**: Tracciamento delle fasi di riscaldamento del catalizzatore e del liquido refrigerante per la massima efficienza in modalità EV.

### 3. 🛠️ Scheda `CODIFICHE` (Personalizzazioni Centralina UDS)
- **📺 Toyota Touch 3 (Display Audio)**:
  - *Animazione di Avvio Schermo*: Impostabile su **🏁 Toyota Gazoo Racing (GR)**, **⚡ Hybrid Synergy Drive** o **Toyota Standard**.
  - *Auto Sound Levelizer (ASL)*: Compensazione automatica del volume in base alla velocità.
  - *Ritardo Spegnimento Retrocamera in D*: 5s o 10s per manovre comode.
  - *Bip Touchscreen & Guadagno Microfono Viva Voce*.
- **🔔 Comfort & Cicalini di Bordo**:
  - *Cicalino Retromarcia*: **Singolo Bip (One Beep Comfort)** o Bip Continuo OEM.
  - *Cicalini Cinture di Sicurezza*: Disattivazione/Attivazione selettiva guidatore, passeggero e sedili posteriori.
- **🔑 Smart Key & Serrature**:
  - *Chiusura Automatica Porte*: A 20 km/h (Speed Lock) o all'inserimento della marcia D.
  - *Sblocco Automatico*: All'inserimento della marcia P.
  - *Apertura/Chiusura Finestrini da Telecomando* (Pressione prolungata).
  - *Volume Sirena Esterna Answerback*: Feedback acustico di chiusura e apertura porte.
- **💡 Luci, Frecce & Plafoniera**:
  - *Frecce Comfort al Tocco (Lane Change)*: 3, 4, 5 o 6 lampeggi automatici.
  - *Sensibilità Fari Crepuscolari & Follow Me Home*: 30s, 60s, 90s.
  - *Temporizzazione Luce Abitacolo*: 7.5s, 15s, 30s e illuminazione vano piedi in marcia.
- **🛡️ ADAS & Clima**:
  - *Bip Limiti di Velocità RSA*: Muto (solo visivo) o sonoro.
  - *Sensibilità Angolo Cieco (BSM) & Volume Avviso Corsia (LDA)*.
  - *Funzionamento A/C con Tasto AUTO* & Modalità Eco AirCon.
- **🔄 Sicurezza & Ripristino Fabbrica**: Pulsante dedicato per ripristinare tutte le impostazioni OEM di fabbrica a 1-click.

---

## 🧪 Test Automatizzati & Qualità del Codice (100% Passing)
La pipeline di build integra test unitari e di integrazione continui:
- `ToyotaCommandsTest.kt`: Test parsing frame UDS batteria e costanti diagnostiche;
- `Elm327ParserTest.kt`: Test pulizia protocollo e filtraggio risposte;
- `EcuCodingAndPipelineTest.kt`: Test formule telemetria, formule °BTDC, percentuali carico e default ECU;
- `ObdControllerIntegrationTest.kt`: Test logica cronometro Dragy, macchina a stati warm-up e payload di scrittura UDS Mode 3B/2E.

Esegui i test localmente con:
```bash
gradle testReleaseUnitTest
```

---

## 🔌 Adattatori OBD-II BLE Compatibili
- **Vgate iCar Pro BLE 4.0+ / iCar 2 BLE** *(Piena compatibilità plug-and-play e low-power standby)*
- **vLinker MC+ / FD+ (BLE)** *(Consigliato per massima velocità multi-frame CAN)*
- **Veepeak OBDCheck BLE / BLE+**
- **Carista OBD BLE**
- **Adattatori ELM327 BLE 4.0+ generici**

---

## 🏗️ Architettura & Flusso Dati

```mermaid
graph TD
    UI[Jetpack Compose UI<br/>GR Cockpit / Fan / ECU Codings] <-->|StateFlow| Activity[MainActivity]
    Activity <-->|Bound Service| Service[FanControlForegroundService]
    Service --> Controller[ObdController Multi-ECU Switching]
    Controller <-->|AT / CAN Frames| Ble[BleManager GATT Client]
    Ble <-->|BLE 4.0 / 247 MTU| Dongle[OBD-II BLE Dongle]
    Dongle <-->|ISO 15765-4 CAN 500k| ECU[Toyota Yaris MK4<br/>Battery 7E2 / Engine 7E0 / Body 750 / Meter 7C0 / ADAS 7A0]
```

---

## 🛠️ Compilazione e Rilascio Locale
Per compilare e firmare l'APK con certificato RSA:
```cmd
D:\Sviluppo\yaris-hv-fan-android\build_apk.bat
```
L'APK generato viene automaticamente copiato e verificato in `YarisHvFanControl.apk` e sincronizzato nella cartella web `docs/`.

---

## 📄 Licenza
Progetto distribuito sotto licenza MIT. Vedere il file [LICENSE](LICENSE) per ulteriori dettagli.
