# Changelog & Cronologia Rilasci

Tutti i cambiamenti e miglioramenti significativi di questo progetto sono documentati in questo file.
Il formato è basato su [Keep a Changelog](https://keepachangelog.com/it/1.0.0/) e aderisce al [Semantic Versioning](https://semver.org/lang/it/):
- **MAJOR (`X.0.0`)**: Modifiche architetturali radicali, nuove sezioni o ridisegno totale della dashboard.
- **MINOR (`0.X.0`)**: Aggiunta di nuove funzionalità, sensori, codifiche o telemetrie.
- **PATCH (`0.0.X`)**: Bugfix, ottimizzazioni di performance o aggiustamenti grafici minori.

---

## [2.1.0] - 2026-08-31
### 🛠️ Modulo Codifiche Centralina (ECU Coding & Customizations)
#### Added
- **Nuova Scheda "🛠️ CODIFICHE ECU"**:
  - **Cicalino Retromarcia (Reverse Beep)**: Passaggio da bip continuo a singolo bip di comfort per il sistema ibrido.
  - **Cicalini Cinture di Sicurezza**: Controllo sonoro per sedile conducente, passeggero e sedili posteriori.
  - **Finestrini Comfort con Telecomando**: Apertura e chiusura totale dei finestrini tenendo premuto il tasto della chiave originale.
  - **Blocco Automatico Porte**: Chiusura porte a 20 km/h (Speed Lock) o all'innesto della marcia `D`, con sblocco automatico in `P`.
  - **Frecce Comfort**: Personalizzazione del numero di lampeggi per il cambio corsia (3, 4 o 5 lampeggi).
  - **Climatizzazione Intelligente**: Opzione per impedire l'attivazione automatica del compressore A/C premendo AUTO.
- **Protocollo di Sicurezza & Backup**:
  - Lettura iniziale della configurazione con backup in memoria.
  - Scrittura sicura UDS/TDS con conferma visiva e gestione errori.
  - Pulsante 1-Click *"Ripristina Impostazioni di Fabbrica"*.
- **Interfaccia a 3 Schede**: Navigazione fluida tra `COCKPIT`, `VENTOLA` e `CODIFICHE`.

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
