# Changelog & Cronologia Rilasci

Tutti i cambiamenti e miglioramenti significativi di questo progetto sono documentati in questo file.
Il formato è basato su [Keep a Changelog](https://keepachangelog.com/it/1.0.0/) e aderisce al [Semantic Versioning](https://semver.org/lang/it/):
- **MAJOR (`X.0.0`)**: Modifiche architetturali radicali, nuove sezioni o ridisegno totale della dashboard.
- **MINOR (`0.X.0`)**: Aggiunta di nuove funzionalità, sensori, codifiche o telemetrie.
- **PATCH (`0.0.X`)**: Bugfix, ottimizzazioni di performance o aggiustamenti grafici minori.

---

## [2.2.0] - 2026-08-31
### 🛠️ Suite Completa Codifiche Centralina (All Toyota TNGA-B Codings)
#### Added
- **Espansione Completa Codifiche Centralina nella Scheda "CODIFICHE"**:
  - **🔑 Smart Key & Serrature**:
    - Volume sirena acustica esterna alla chiusura/apertura (Muto, Basso, Medio, Alto).
    - Auto-Relock (tempo di richiusura automatica a 30s, 60s, 120s se le porte non vengono aperte).
    - Sblocco selettivo porte (Solo lato guida vs Tutte le porte).
    - Finestrini totali con telecomando originale.
    - Blocco porte a 20 km/h (Speed Lock) o in marcia D, con sblocco automatico in `P`.
  - **🌧️ Tergicristalli & Pioggia**:
    - Tergilunotto automatico inserendo la retromarcia.
    - Passata finale anti-goccia lavavetri (*Drip Wipe*).
    - Intermittenza spazzole dipendente dalla velocità vettura.
  - **💡 Luci, Frecce & Plafoniera**:
    - Frecce comfort cambio corsia (3, 4 o 5 lampeggi).
    - Tempo dissolvenza luci abitacolo interne (7.5s, 15s OEM, 30s).
    - Illuminazione vano piedi / pedali attiva anche durante la marcia notturna.
    - Regolazione sensibilità fari crepuscolari (Scuro -1, Normale, Chiaro +1).
    - Luci di cortesia Follow Me Home (OFF, 30s, 60s).
  - **🛡️ ADAS & Sicurezza (Toyota Safety Sense 2.5)**:
    - Volume avviso sonoro cambio corsia LDA/LTA (Basso, Medio, Alto).
    - Sensibilità e distanza rilevamento angolo cieco BSM (Vicino, Normale, Anticipato).
  - **❄️ Climatizzazione & Efficienza**:
    - Disattivazione automatica A/C forzata su tasto AUTO.
    - Modalità Eco Run Clima per massimizzare l'autonomia elettrica EV.
  - **🛡️ Backup & Restore**: Lettura iniziale, scrittura sicura con feedback visivo e tasto *"Ripristina Impostazioni di Fabbrica (OEM Toyota)"*.

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

---

## [1.0.0] - 2026-08-31
### 🌀 Initial Production Release
#### Added
- **Forzatura Ventola Batteria Denso HV**: Controllo Mode 30/2F a 12V Livello 6 (100% MAX).
- **Lettura Termica Moduli Batteria**: Monitoraggio 4 sonde celle e canale aspirazione (`PID 2228C1`).
- **Analisi Warm-Up Termico HSD (S0 ➔ S4)**: Tracciamento liquido (ECT), aria (IAT) e giri (RPM).
- **Foreground Service Persistente**: Funzionamento continuo in background con Google Maps / Waze.
