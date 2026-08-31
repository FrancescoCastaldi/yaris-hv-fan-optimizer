# Changelog & Cronologia Rilasci

Tutti i cambiamenti e miglioramenti significativi di questo progetto sono documentati in questo file.
Il formato è basato su [Keep a Changelog](https://keepachangelog.com/it/1.0.0/) e aderisce al [Semantic Versioning](https://semver.org/lang/it/):
- **MAJOR (`X.0.0`)**: Modifiche architetturali radicali, nuove sezioni o ridisegno totale della dashboard.
- **MINOR (`0.X.0`)**: Aggiunta di nuove funzionalità, sensori o telemetrie compatibili con le versioni precedenti.
- **PATCH (`0.0.X`)**: Bugfix, ottimizzazioni di performance o aggiustamenti grafici minori.

---

## [2.0.0] - 2026-08-31
### 🏎️ Gazoo Racing Major Update & Dual-Tab Interface
#### Added
- **Architettura a Doppia Scheda (Dual-Tab)**:
  - **`🏁 GR COCKPIT`**: Cruscotto digitale da competizione Gazoo Racing ad alto contrasto.
  - **`🌀 VENTOLA & TERMICHE`**: Console avanzata per il controllo termico della batteria e del motore termico.
- **Badge Ufficiale Gazoo Racing**: Logo vettoriale fedele allo stile Toyota GR.
- **Tachimetro Digitale Gigante**: Visualizzazione della velocità reale in km/h con precisione CAN (`PID 010D`).
- **Launch Control Light Automatico**:
  - `[🟢 LAUNCH READY]` a vettura ferma (`0 km/h`).
  - `[⏱️ SCATTO IN CORSO]` con calcolo automatico al primo colpo di acceleratore.
- **Cronometro Dragy 0-50 km/h & 0-100 km/h**:
  - Ticker centesimale in tempo reale.
  - Scorecard con confronto dell'ultimo tempo e salvataggio del **Record Personale (PB)**.
- **Telemetria Motore & Anticipo Termico**:
  - Anticipo di accensione reale in gradi (`PID 010E` °BTDC).
  - Carico motore termico (`PID 0104` %).
  - Posizione pedale acceleratore (`PID 0111` %).
- **Certificato di Firma Digitale RSA 2048-bit**:
  - Firma V1 (JAR), V2 (Full APK) e V3 integrata per prevenire avvisi di app dannosa.
- **Icona di Lancio HD Personalizzata**:
  - Logo neon cyan fan + lightning bolt su trama carbon fiber, convertito in tutte le densità mipmap (mdpi, hdpi, xhdpi, xxhdpi, xxxhdpi).
- **Nuovo Portale Web Mobile-Friendly & Vercel Deploy**:
  - Download diretto dell'APK con pulsante mobile sticky a 1 tocco e CI/CD con GitHub Actions.

---

## [1.0.0] - 2026-08-31
### 🌀 Initial Production Release
#### Added
- **Forzatura Ventola Batteria Denso HV**:
  - Controllo diagnostico Mode 30/2F (comando `300806` / `2F580306`) a 12V Livello 6 (100% MAX).
  - Prevenzione del taglio di potenza e derating termico della batteria al litio sopra i 36°C.
- **Lettura Termica Moduli Batteria**:
  - Monitoraggio delle 4 sonde di temperatura batteria (T1, T2, T3, T4) e canale di aspirazione (`PID 2228C1`).
- **Analisi Warm-Up Termico HSD (Fasi S0 ➔ S4)**:
  - Tracciamento della temperatura del liquido refrigerante (ECT `0105`), aria aspirata (IAT `010F`) e giri motore (RPM `010C`).
  - Consigli dinamici intelligenti per la gestione del climatizzatore.
- **Foreground Service Persistente**: Funzionamento continuo in background anche a schermo spento e con navigatori (Google Maps / Waze).
- **Auto-Riconnessione Bluetooth Low Energy (BLE)** per dongle vLinker MC+, Veepeak, Carista ed ELM327.
