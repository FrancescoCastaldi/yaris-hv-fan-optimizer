# Changelog & Cronologia Rilasci

Tutti i cambiamenti e miglioramenti significativi di questo progetto sono documentati in questo file.
Il formato è basato su [Keep a Changelog](https://keepachangelog.com/it/1.0.0/) e aderisce al [Semantic Versioning](https://semver.org/lang/it/):
- **MAJOR (`X.0.0`)**: Modifiche architetturali radicali, nuove sezioni o ridisegno totale della dashboard.
- **MINOR (`0.X.0`)**: Aggiunta di nuove funzionalità, sensori, codifiche o telemetrie.
- **PATCH (`0.0.X`)**: Bugfix, ottimizzazioni di performance o aggiustamenti grafici minori.

---

## [2.8.2] - 2026-09-04
### 🏁 Gazoo Racing Heritage Motorsport Final Deployment & Site Sync
#### Added & Improved
- **Sincronizzazione Completa Release v2.8.2**:
  - Allineamento build Android Gradle con `versionName = "2.8.2"` e `versionCode = 14`.
  - Aggiornamento script di build per generazione e firma automatica di `YarisHvFanControl-v2.8.2.apk`.
  - Sincronizzazione completa del portale web (`docs/index.html`, `docs/404.html`, `docs/vercel.json` e workflow CI/CD) con download diretto dell'APK v2.8.2.
  - Aggiornamento documentazione tecnica, README e manifest di compatibilità.

---

## [2.8.1] - 2026-09-04
### 🏁 Gazoo Racing Heritage Motorsport Emblem & Clean Palette Refinement
#### Added & Improved
- **Nuovo Logo Motorsport Gazoo Racing Heritage**:
  - Emblema esclusivo con scudo aerodinamico e trama in fibra di carbonio opaca a trama fine.
  - Monogramma 'GR HV' in alluminio spazzolato con accenti Toyota Racing Red (`#D71920`) e dark graphite.
  - Eliminazione totale di bagliori artificiali, fumo o glow stile AI.
- **Sincronizzazione Completa Icone di Sistema & Web**:
  - Nuove icone launcher Android rigenerate per tutte le densità (MDPI, HDPI, XHDPI, XXHDPI, XXXHDPI) sia in formato tondo che standard.
  - Favicon web `favicon.ico` e immagine hero sincronizzata in `docs/icon.jpg`.
  - Nuovo asset interno `ic_motorsport_logo.png` visualizzato nel badge di testata dell'app in Dashboard.
- **Raffinamento Palette Colori Motorsport Luxury**:
  - Rimozione completa di ombreggiature glow neon sul sito web `docs/index.html` e `preview.html`.
  - Nuove classi e bottoni con gradiente racing solido e bordi metallici a taglio laser.

---

## [2.8.0] - 2026-09-04
### 🏎️ MoTeC Motorsport Redesign, Semantica Connessione Rigorosa, Resilienza Backend & Closed-Loop Hall RPM
#### Added & Improved
- **Redesign UUXD MoTeC / Bosch Motorsport**:
  - Interfaccia ad altissimo contrasto Dark/OLED senza glow artificiosi.
  - Barra contagiri shift-light a 10 segmenti color-coded ispirata alla strumentazione da corsa.
  - Card tachimetro Dragy stile corsa e matrice a 4 celle Denso per il pacco batterie.
- **Diagnostica a Circuito Chiuso (Closed-Loop ECU Feedback) & Hall RPM**:
  - Invio Mode 30 IO Control con validazione di risposta `Positive ACK (0x70 81 06)`.
  - Stima tachimetrica fisica fino a ~4650 RPM con badge dinamico `ECU ACK • ~XXXX RPM` nella scheda VENTOLA.
  - Multi-PID Fallback a cascata per pacchi litio TNGA-B (`2228C1`, `2161`, `21C3`, `21C4`, `2228C0`).
- **Semantica di Connessione Rigorosa a 2 Livelli (Zero Falsi Positivi)**:
  - Stato `[● ECU READY]` solo in presenza di effettivi frame CAN decodificati dalle centraline Toyota negli ultimi 5 secondi.
  - Stato `[▲ DONGLE OK - ATTESA ECU]` quando il dongle Bluetooth è associato ma l'auto ha il quadro spento o la centralina è in standby.
  - Sostituzione di tutti i valori fittizi (`0 km/h`, `0.0°C`, delta termico e duty ventola) con placeholder rigorosi `--` o `--.-°C` a motore/quadro spento.
  - Notifica Android trasparente in background: informa l'utente di avviare l'auto (spia READY) senza riportare temperature o stati fittizi.
- **Resilienza di Connettività Backend a Prova di Bomba**:
  - `BroadcastReceiver` di sistema per `BluetoothAdapter.ACTION_STATE_CHANGED`: gestione pulita della disattivazione Bluetooth e riconnessione automatica immediata appena il Bluetooth torna `STATE_ON`.
  - Drenaggio preventivo dello stream di input SPP: eliminazione di byte orfani e residui seriali prima dell'invio di comandi UDS.
  - Timeout di comando adattivo e differenziato (`timeoutMs`) in `BleManager.sendCommand`.
  - Scanner con ordinamento prioritario dei dongle OBD (`Vgate`, `vLinker`, `OBDII`, `ELM327`) e dei dispositivi bonded in cima alla lista.
  - Auto-Recovery CAN trasparente: Warm Start `AT WS` dell'ELM327 e ripristino ISO-TP Flow Control dopo 5 secondi di assenza di frame CAN senza interruzione della connessione Bluetooth.

---

## [2.7.0] - 2026-09-01
### 🏎️ Closed-Loop ECU Fan Confirmation & Live UI Web Simulator
#### Added
- Validazione risposta ECU Active Test `30 81 06`.
- Simulatore UI Web `preview.html` per testare le funzionalità da browser.

---

## [2.5.0] - 2026-08-31
### 🔌 Supporto Totale Vgate iCar Pro BLE 4.0+ & Discovery Engine Potenziato
#### Added
- **Riconoscimento e Connessione Istantanea Vgate iCar Pro BLE 4.0+**:
  - Parser avanzato dei byte di advertisement per identificare il nome anche su chip BLE 4.0 che omettono il nome broadcast (`IOS-Vlink`, `Android-Vlink`, `Vgate`).
  - Mappatura completa UUID GATT per profili Microchip ISSC (`49535343-...`), HM-10 (`FFE0/FFE1`), e custom Vgate (`18F0`, `E0FF`).
  - Fallback garantito per il service discovery che evita il blocco sui chip BLE a MTU fisso (23 byte).
  - Caricamento istantaneo di tutti i dispositivi già associati su Android (`Bonded Devices`) con badge verde `[ASSOCIATO]`.
  - Prompt di attivazione automatica del Bluetooth all'avvio se disattivato.

---

## [2.3.1] - 2026-08-31
### 📺 Supporto Ufficiale Toyota Touch 3 (Display Audio - Senza Mappe)
#### Added
- **Modulo Specifico per Infotainment Toyota Touch 3 (Display Audio MM19)**:
  - **Animazione di Avvio Schermo**: Scelta tra **🏁 Toyota Gazoo Racing (GR)**, **⚡ Hybrid Synergy Drive** e **Standard Toyota**.
  - **ASL (Auto Sound Levelizer)**: Compensazione automatica del volume audio in base alla velocità reale e al rumore di rotolamento (OFF, Basso, Medio, Alto).
  - **Disattivazione Bip Schermo & Tasti Fisici**: Silenziamento completo del segnale sonoro alla pressione dello schermo e dei tasti fisici `AUDIO`, `MENU`, `HOME`.
  - **Ritardo Spegnimento Retrocamera in Manovra**: Mantiene la visuale posteriore per 5 secondi in marcia D.

---

## [2.3.0] - 2026-08-31
### 🖥️ Supporto Ufficiale Toyota Yaris MK4 MY2025 (Smart Connect & Digital Cockpit 7.0")
#### Added
- **Modulo Esclusivo per Yaris Restyling MY2025 (Allestimento Trend / Lounge)**:
  - **Quadro Strumenti Digitale 7.0" (Digital Cockpit)**: Selezione rapida del layout quadranti tra **🏁 Sport GR**, **Smart**, **Casual** e **Tough**.
  - **Disattivazione Bip Limiti di Velocità ISA / Cartelli RSA**: Eliminazione del cicalino continuo sui limiti stradali.

---

## [2.2.0] - 2026-08-31
### 🛠️ Suite Completa Codifiche Centralina (All Toyota TNGA-B Codings)
#### Added
- **Espansione Completa Codifiche Centralina nella Scheda "CODIFICHE"**:
  - Smart Key, Volume Sirena Esterna, Auto-Relock (30s/60s/120s), Sblocco Selettivo.
  - Finestrini da telecomando e Chiusura Porte automatica in D / 20 km/h.
  - Tergilunotto automatico in retro, Drip Wipe e intermittenza tachimetrica.
  - Frecce comfort (3, 4, 5 lampeggi), dissolvenza luci plafoniera e sensibilità fari.
  - Volume avvisi cambio corsia LDA/LTA e sensibilità angolo cieco BSM.
  - Disattivazione automatica A/C forzata su tasto AUTO e modalità Eco Run.

---

## [2.0.0] - 2026-08-31
### 🏎️ Gazoo Racing Major Update & Dual-Tab Interface
#### Added
- **Architettura a Doppia Scheda (Dual-Tab)**: `GR COCKPIT` e `VENTOLA & TERMICHE`.
- **Badge Ufficiale Gazoo Racing**: Logo vettoriale originale Toyota GR ad alto contrasto.
- **Tachimetro Digitale Gigante (52sp)**: Velocità reale da CAN bus (`PID 010D`).
- **Launch Control Light Automatico**: `[🟢 LAUNCH READY]` a 0 km/h e `[⏱️ SCATTO IN CORSO]`.
- **Cronometro Dragy 0-50 km/h & 0-100 km/h**: Con salvataggio Record Personale (PB).
- **Telemetria Motore & Anticipo Termico**: Anticipo reale (`PID 010E` °BTDC), carico motore (%) e pedale gas (%).
- **Firma Digitale RSA 2048-bit**: Certificato di sicurezza per Android V1/V2/V3.
- **Portale Web Mobile-Friendly**: Download diretto APK con barra sticky 1-tap.
