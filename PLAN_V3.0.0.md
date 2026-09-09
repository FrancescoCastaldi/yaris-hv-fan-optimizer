# 🚀 Piano di Implementazione & Stato Avanzamento: Release v3.0.0

**Obiettivo:** Rilascio Major Release v3.0.0 con **Forzatura Manuale Attiva della Ventola Pacco Batteria HV (L1–L6)**, Stepper interattivo nel Cockpit, Keep-Alive UDS continuo e Persistenza su SharedPreferences per Toyota Yaris TNGA XP210.

---

## 📊 Tabella di Avanzamento Task

| Task ID | Descrizione Attività | Stato | Dettagli / Note |
| :--- | :--- | :---: | :--- |
| **T1** | Aggiunta chiavi persistenza in AppPreferences.kt | 🟢 COMPLETATO | isManualFanForced e manualFanTargetLevel (1..6) |
| **T2** | Espansione PIDs UDS in ToyotaYarisCommands.kt | 🟢 COMPLETATO | Aggiunto getFanSpeedCommandAlt (Mode 2F fallback) |
| **T3** | Aggiornamento ObdLiveState e metodi in ObdController.kt | 🟢 COMPLETATO | setManualForcedFan(enabled, level) e setManualFanTargetLevel |
| **T4** | Disaccoppiamento comando ventola dai vincoli discovery | 🟢 COMPLETATO | Invio immediato su header 7E2 senza blocchi su temp/discovery |
| **T5** | Keep-alive UDS periodico (~1.5s) e comando release 300800 | 🟢 COMPLETATO | Ripristino OEM quando spento, Mode 2F fallback su 7F30 |
| **T6** | Restyling UI Cockpit in DashboardScreen.kt | 🟢 COMPLETATO | Switch ON/OFF + Stepper [-] / [+] con indicatore L1-L6 e RPM |
| **T7** | Aggiornamento FanControlForegroundService e MainActivity | 🟢 COMPLETATO | Sincronizzazione con preferenze all'avvio |
| **T8** | Bump versione v3.0.0 (versionCode 38) | 🟢 COMPLETATO | pp/build.gradle.kts, uild_apk.bat, deploy.yml |
| **T9** | Sincronizzazione sito web e simulator (docs/) | 🟢 COMPLETATO | index.html, 404.html, preview.html, simulator.js |
| **T10** | Esecuzione Unit Test (	estReleaseUnitTest) | 🟢 COMPLETATO | Suite al 100% verde (103 test passati) |
| **T11** | Compilazione APK Release (uild_apk.bat) | 🟡 IN CORSO | Generazione YarisHvFanControl-v3.0.0.apk |
| **T12** | Git Commit, Tag v3.0.0 e Push | ⏳ DA INIZIARE | Rilascio ufficiale su GitHub |

---

## 📝 Registro Modifiche Recenti
- **2026-09-09**: Implementata forzatura manuale L1-L6 con Stepper e bypass discovery. Test unitari passati al 100%. Versione aggiornata a v3.0.0.
