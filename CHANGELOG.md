# Changelog & Cronologia Rilasci

Tutti i cambiamenti e miglioramenti significativi di questo progetto sono documentati in questo file.
Il formato è basato su [Keep a Changelog](https://keepachangelog.com/it/1.0.0/) e aderisce al [Semantic Versioning](https://semver.org/lang/it/):
- **MAJOR (`X.0.0`)**: Modifiche architetturali radicali, nuove sezioni o ridisegno totale della dashboard.
- **MINOR (`0.X.0`)**: Aggiunta di nuove funzionalità, sensori, codifiche o telemetrie.
- **PATCH (`0.0.X`)**: Bugfix, ottimizzazioni di performance o aggiustamenti grafici minori.

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
