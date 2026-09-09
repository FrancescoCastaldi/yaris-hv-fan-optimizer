# 🚀 Piano di Implementazione & Stato Avanzamento: Release v3.0.1

**Obiettivo:** Risoluzione definitiva dell'oscillazione READY (13V) $\leftrightarrow$ Standby/Sleep su Toyota Yaris TNGA XP210 e dongle OBD con ADC sfasato.

---

## 📊 Tabella di Avanzamento Task

| Task ID | Descrizione Attività | Stato | Dettagli / Note |
| :--- | :--- | :---: | :--- |
| **F1** | Rimozione standby prematuro prima di Stadio 1 | 🟢 COMPLETATO | Esecuzione garantita di 7E0 prima di qualsiasi decisione di standby |
| **F2** | Bypass del timeout di 4 secondi su 7DF al risveglio | 🟢 COMPLETATO | Connessione diretta a 7E0 (Engine ECU) |
| **F3** | Isteresi robusta anti-oscillazione su AT RV | 🟢 COMPLETATO | Rimozione di !isVehicleReady come trigger di sleep istantaneo |
| **F4** | Test unitari (103 test) verdi al 100% | 🟢 COMPLETATO | BUILD SUCCESSFUL in 9s |
| **F5** | Aggiornamento versione a v3.0.1 (versionCode 39) | 🟢 COMPLETATO | uild.gradle.kts, uild_apk.bat, deploy.yml, docs/ |
| **F6** | Compilazione APK Release v3.0.1 (uild_apk.bat) | 🟡 IN CORSO | Generazione YarisHvFanControl-v3.0.1.apk |
| **F7** | Git Commit, Tag v3.0.1 e Push | ⏳ DA INIZIARE | Deploy su GitHub Pages e branch main |

---

## 📝 Note Tecniche
- Risolto il difetto per cui un dongle Vgate con lettura a 12.8V-12.9V faceva rimbalzare l'app tra modalità READY e SLEEP ogni 2 secondi, bloccando l'invio dei comandi ventola e telemetria.
