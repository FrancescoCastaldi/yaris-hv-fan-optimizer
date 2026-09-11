# Changelog & Release History

All notable changes and technical improvements for this project are documented in this file.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/) and adheres to [Semantic Versioning](https://semver.org/):
- **MAJOR (`X.0.0`)**: Fundamental architectural overhauls, major subsystems, or comprehensive UI redesigns.
- **MINOR (`0.X.0`)**: New features, additional sensors, ECU coding options, or telemetry pipelines.

## [3.1.0] - 2026-09-11
### ⚡ Migrazione Completa Codifiche ECU a UDS ISO 14229-1 (Toyota TNGA-B)
- **Eliminazione Totale Dipendenze K-Line `3B`**:
  - Rimossi integralmente tutti i letterali e i comandi legacy K-Line `3Bxx` (`3B00`, `3B01`, `3B20`, ecc.) dal layer di comando e dalla pipeline di personalizzazione centralina.
- **Implementazione Protocollo Read-Before-Write a 8 Fasi**:
  1. `ensureCanHeader(targetHeader)` con timeout diagnostico dedicato `CMD_TIMEOUT_ECU_CODING` (`AT ST 96`).
  2. Apertura e transizione a sessione estesa UDS `1003` (`CMD_UDS_SESSION_EXTENDED`) con verifica stretta della risposta positiva `5003` o gestione intercettata di NRC 0x22 (*ConditionsNotCorrect*).
  3. Lettura dello stato originale con Service 0x22 (`buildUdsRead(did)`) per backup e calcolo differenziale mirato.
  4. Calcolo del nuovo payload con isolamento del parametro o bitmasking.
  5. Scrittura su EEPROM tramite Service 0x2E (`buildUdsWrite(did, payload)`) con verifica stretta della risposta positiva `6E <DID>`.
  6. Read-After-Write di convalida immediata con Service 0x22 per certificare l'avvenuta memorizzazione in centralina.
  7. Commit EEPROM chiudendo la sessione diagnostica su default `1001` (`CMD_UDS_SESSION_DEFAULT`) con guard-time di 100ms.
  8. Ripristino atomico nel blocco `finally withContext(NonCancellable)` dell'header CAN motore (`ensureEngineHeader()`).
- **Definizione DIDs UDS per Tutte le Centraline Toyota TNGA-B**:
  - **Combination Meter (`7C0`/`7C8`)**: DID `A001` (Reverse Beep singolo/continuo), `A002`/`A003`/`A004` (Cicalini cinture guidatore, passeggero e posteriori).
  - **Main Body / Gateway (`750`/`758`)**: DID `B001` (Chiusura porte automatica in velocità), `B002` (Sblocco porte in P), `B003` (Alzacristalli da telecomando), `B004` (Volume sirena wireless), `B005` (Timer riarmo chiusura), `B006` (Modalità sblocco porte), `B010` (Comfort Turn Signal flashes), `B011` (Sensibilità fari crepuscolari), `B012` (Follow Me Home), `B013` (Dissolvenza luci abitacolo), `B014` (Illuminazione vano piedi in D), `B020`/`B021`/`B022` (Tergicristalli e sensore pioggia).
  - **Air Conditioning (`7C4`/`7CC`)**: DID `C001` (Compressore A/C su AUTO), `C002` (Eco aircon efficiency), `C003` (Ventilatore su sbrinatore), `C004` (Offset calibrazione temperatura).
  - **ADAS & TSS 2.5 (`7A0`/`7A8`)**: DID `D001` (Volume LDA), `D002` (Sensibilità BSM), `D003` (RCTA), `D004` (LTA), `D005` (PCS memoria ultimo stato).
- **Miglioramento Diagnostica UDS & Decodifica NRC**:
  - Validazione estesa in `Elm327Protocol.isUdsPositiveResponse` per SID 0x50, 0x62, 0x6E, 0x7E.
  - Decodifica chiara per l'utente in `getUdsNrcDescription`: NRC 0x11 (*ServiceNotSupported*), NRC 0x22 (*ConditionsNotCorrect: veicolo non pronto, quadro in READY, portiere chiuse, cambio in P*), NRC 0x31 (*RequestOutOfRange*).
- **Incremento Versione & Sincronizzazione**:
  - Bump versione a `v3.1.0` (`versionCode = 48`).
  - Sincronizzati build script, workflow CI/CD, README, portale web e file di documentazione.

## [3.0.9] - 2026-09-11
### 🔌 Risoluzione Definitiva Connessione OBD-II Toyota TNGA-B & Vgate iCar Pro
- **FIX 1: Bonifica Totale di `AT AR` dall'Handshake**:
  - Rimosso completamente `AT AR` dalla sequenza di negoziazione in `performHandshake()`, azzerando la desincronizzazione e il ripristino involontario dei filtri di ricezione sui cloni ELM327 v2.3 / STN.
- **FIX 2: Stabilizzazione Rigida Protocollo `AT SP 6`**:
  - Immediata sincronizzazione su protocollo ISO 15765-4 CAN 11-bit 500kbaud (`AT SP 6`) senza mai usare `AT SPA 6`.
  - Introdotti 100ms di guard-time per assestamento oscillatore UART/CAN e verifica con `AT DPN` con fallback agganciato su protocollo 6.
- **FIX 3: Gateway Wake-Up e Routing Handshake a 3 Stadi**:
  - **Stadio 0 (Sveglia Gateway Broadcast)**: query broadcast `7DF` -> `0100` preliminare per svegliare e instradare la sessione diagnostica su V-CAN e C-CAN del Central Gateway TNGA-B.
  - **Stadio 1 (Aggancio Motore con Isolamento Hardware)**: interrogazione diretta `7E0` + `CRA 7E8` con scatto immediato su fallback broadcast funzionale `7DF` + `CRA 7E8` su risposte vuote (`NO DATA`).
  - **Stadio 2 (Aggancio HV BMS Denso)**: commutazione su `7E2` + `CRA 7EA` con timeout esteso `AT ST FF` (~1044ms) per la ricezione completa dei frame ISO-TP multiframe.
- **FIX 4: Gestione Avanzata Negative Response Codes (NRC) UDS & Accelerazione Discovery**:
  - Parser UDS NRC per `7F <Service> <NRC>`: rifiuto permanente immediato per `7F xx 11` (*ServiceNotSupported*) e `7F xx 12` (*SubFunctionNotSupported*), isolamento in attesa per `7F xx 22` (*ConditionsNotCorrect*), e attesa frame per `7F xx 78` (*ResponsePending*).
  - Accelerazione cursore fallback batteria: cooldown dei candidati abbassato da 30s a 5s (`DEFAULT_COOLDOWN_MS = 5_000L`) con scansione dinamica non bloccante.
- **FIX 5: Timeout Dinamici Calibrati sullo Stato Veicolo (READY vs ACC)**:
  - Misurazione continua della tensione ausiliaria 12V con `AT RV`.
  - In stato READY (convertitore DC-DC attivo, tensione >= 13.0V): applicati timeout rapidi e aggressivi (`AT ST 32` / `AT ST 64`).
  - In stato quadro acceso non READY (< 13.0V): polling rilassato con standby timeout a basso consumo (`AT ST 64`) per preservare la batteria dei servizi.
- **FIX 6: Resilienza Bluetooth SPP / BLE con `AT WS` & Serial Drain**:
  - Auto-recovery CAN blindato su Warm Start `AT WS` e ritardo di stabilizzazione di 100ms, preservando intatto il socket RFCOMM/BLE.
  - Flush automatico del buffer seriale con drain `\r` a vuoto e pausa di 50ms sui comandi andati in timeout.
- **FIX 7: Unificazione e Pulizia Costanti Inizializzazione**:
  - Unificata la sequenza in un'unica lista canonica `INIT_COMMANDS` con `AT H0` (formato compatto standard ELM327) e rimozione totale di `AT AR`, con alias backward-compatible `VGATE_CALIBRATED_INIT_COMMANDS`.
- **Monotonic Version Increment & Web Portal Synchronization**:
  - Incremento versione a `v3.0.9` (`versionCode = 47`).
  - Sincronizzazione automatica di tutti i file di distribuzione APK e pagine web del portale.

## [3.0.8] - 2026-09-10
### 🛡️ CAN OBD-II Filter Sanitization, 7DF Functional Telemetry & Safe Fan Actuation
- **R1: Elimination of Filter Corruption (`AT AR` removal after `AT SH`)**:
  - Completely eliminated `AT AR` emission after `AT SH <header>` across all transmission contexts.
  - Setting transmission header via `AT SH` and hardware receive filter via `AT CRA` now strictly preserves the CAN hardware filter without resetting it on ELM327 clone adapters.
  - Kept system CAN Auto Formatting (`AT CAF 1`) active without sending corrupting Flow Control overrides.
- **R2: Functional Addressing (`7DF`) Engine Telemetry & Fallback**:
  - Implemented transparent fallback to functional broadcast `7DF` paired with `AT CRA 7E8` when physical `7E0` queries return `NO DATA` (such as on Toyota TNGA-B DLC3 Central Gateway isolation).
  - Telemetry parameters (Engine RPM `010C`, Vehicle Speed `010D`, Coolant Temperature `0105`, Throttle `0111`) are acquired cleanly without dropping engine telemetry frames.
- **R3: Safe Battery Fan Actuation & Auto-Recovery Reconnection Flapping Prevention**:
  - Guarded fan actuation (`2F58030x` / `30080x`) to require established battery communication (`isBatteryCommunicationEstablished`: battery discovered, communication verified, temperature > 0.0°C), eliminating premature actuation commands at startup.
  - Replaced destructive auto-recovery sequence (`\r\r` -> `AT Z` hard reset) with Warm Start (`AT WS`), preventing RFCOMM Bluetooth socket closure / crash.
- **Monotonic Version Increment & Web Portal Synchronization**:
  - Bumped `versionCode` to 46 and `versionName` to `3.0.8`.
  - Updated all web portal links, simulator, and release automation scripts.

## [3.0.7] - 2026-09-10
### 🔋 Universal Mode 21 Battery Telemetry Prioritization & Secondary ECU Decoupling
- **Mode 21 Universal Battery PID Prioritization**:
  - Reordered `BATTERY_FALLBACK_PIDS` to place universal Mode 21 PIDs first (`2101`, `21C3`, `21C4`, `2161`), relegating Mode 22 UDS PIDs (`2228C1`, `2228C0`, `220101`) to fallback-only positions.
  - Resolves `NO DATA` / rejection on XP210 TNGA-B Denso HV Battery ECUs that reject Mode 22 3-byte identifiers without an active extended diagnostic session (`10 03`).
- **ELM327 Battery Multi-Frame ISO-TP Timeout Expansion**:
  - Increased `Elm327Protocol.CMD_TIMEOUT_BATTERY_ECU` from `AT ST C8` (~819ms) to `AT ST FF` (~1044ms), giving maximum margin for multi-frame consecutive frame reception across BLE/SPP interfaces.
- **Secondary ECU Decoupling**:
  - Guaranteed secondary ECUs (Meter `7C0`, ADAS `7A0`, Body `750`, Aircon `7C4`) remain strictly on-demand in ECU customization and cannot pollute or disrupt steady-state battery and engine telemetry.
- **Monotonic Version Increment & Web Portal Synchronization**:
  - Bumped `versionCode` to 45 and `versionName` to `3.0.7`.
  - Updated web portal links, simulator, and release automation scripts.

## [3.0.6] - 2026-09-10
### 🚗 Vgate iCar Pro Handshake Calibration & Resilient Staged TNGA-B CAN Lock
- **Calibrated Vgate iCar Pro Initial AT Handshake (R1)**:
  - Implemented calibrated sequence: `\r\r` (wake-up), `AT Z` (clean reset with 300ms bootloader settling delay), `AT E0`, `AT L0`, `AT S0`, `AT H0`, `AT SP 6` (ISO 15765-4 CAN 11-bit 500kbaud) with graceful fallback to `AT SP 0`, `AT AT 1` (Adaptive Timing 1), and `AT CAF 1` (CAN Auto Formatting).
  - Preserved buffer safety by omitting aggressive hardware filters (`AT CRA`) and custom Flow Control (`AT FC`) during initial CAN negotiation, avoiding Vgate internal buffer lockup.
  - Sized broadcast CAN initial query (`7DF` -> `0100` / `010C`) wait timeout to 8000ms with robust prompt drain up to 500ms on timeout to avoid UART desynchronization.
- **Resilient Staged Handshake Architecture (R2)**:
  - **Stage 1 (Engine ECU 7E0 Lock)**: Direct targeting of Engine ECU (`AT SH 7E0` with hardware filter isolation) using standard PIDs (`010C` RPM, `010D` Speed, `0100` Supported PIDs) with positive response validation (`41 0C` / `41 0D` / `41 00`) and up to 3 retry attempts on `NO DATA` / `CAN ERROR`.
  - **Stage 2 (Denso HV Battery 7E2 Fallback Chain)**: Seamless PID fallback chain (`2228C1` -> `2228C0` -> `2101` -> `21C3` -> `2161`) with controlled auto-retries before latching the active battery PID into `BatteryDiscoveryEngine`.
- **Diagnostic UI & Handshake Progress Feedback (R3)**:
  - Replaced generic "non ricevo dati" alert with granular stage indicators ("Sveglia adattatore", "Sincronizzazione CAN 500k", "Aggancio motore 7E0", "Lettura batteria 7E2") in both live state banner and log trace (`ObdLogger`).
- **Monotonic Version Increment & Web Portal Synchronization**:
  - Bumped `versionCode` to 44 and `versionName` to `3.0.6`.
  - Updated web portal links, simulator, and build automation scripts.

## [3.0.5] - 2026-09-09
### 📚 Architectural Codemap, Tech Minimal Documentation & Git Contributor Cleanup
- **Comprehensive Architectural Codemap (`codemap.md`)**:
  - Published end-to-end system topology diagram covering presentation, foreground service, dual-rate scheduler, serial transport, and vehicle multi-ECU network (`7E0`, `7E2`, `750`, `7C0`, `7C4`, `7A0`).
  - Cataloged core component responsibilities, scheduling rates, and zero-allocation memory constraints.
- **Minimalist Technical Documentation (`README.md`, `AGENTS.md`, `PUSH_POLICY.md`)**:
  - Rewrote and streamlined project documentation into an ultra low-cortisol, high-density technical format in English.
  - Formatted adapter compatibility tables, test architecture, and local deterministic toolchain requirements.
- **Git History Hygiene & Contributor Cleanup**:
  - Purged co-author metadata attributing commits to third-party automated tools, preserving full author attribution and commit integrity.
- **Monotonic Version Increment & Web Portal Synchronization**:
  - Bumped `versionCode` to 43 and `versionName` to `3.0.5`.
  - Synchronized static assets, release artifacts, and web portal simulator.

## [3.0.4] - 2026-09-09
### ⚡ Atomic CAN Hardware Filter Configuration (AT CRA), Single-Flight Sequentiality & UDS Fan Service 0x2F
- **Atomic CAN Hardware Filters (`AT SH` + `AT CRA`)**:
  - Implemented deterministic hardware mapping via `ToyotaYarisCommands.getFilterForHeader`: every ECU transmission header (`7E0`, `7E2`, `7C0`, `750`, `7C4`, `7A0`) is atomically paired with its corresponding hardware receive filter (`7E8`, `7EA`, `7C8`, `758`, `7CC`, `7A8`).
  - Completely prevents physical frame drops by internal CAN controller filter masks on ELM327/OBDLink adapters; keeps open receive mask (`AT CRA`) for functional broadcast (`7DF`).
- **Single-Flight Strictly Sequential Dual-Rate Scheduler**:
  - Eliminated asynchronous cycle overlap (`scope.launch { executeDualRateCycle() }`) preventing destructive interleaved transmissions on the serial channel.
  - Introduced `obdTransactionMutex` to strictly serialize telemetry cycles and enforce mutual exclusion across ECU read and write coding sessions (`readEcuCustomizations`, `applyEcuCustomization`).
- **Primary HV Fan Control via UDS Service 0x2F (`2F 58 03 0x`)**:
  - Standardized primary fan override on Toyota TNGA-B XP210 to UDS `2F 58 03 0x` (InputOutputControlByIdentifier), retaining legacy Mode 30 (`30 08 0x`) as automatic fallback.
  - Implemented fail-safe OEM thermal restoration via UDS ReturnControlToECU (`2F 58 00`) and `30 08 00`.
- **Buffer Overrun Prevention & Serial Drain on ELM327**:
  - Added fail-fast connection verification prior to command dispatch.
  - Implemented timeout recovery transmitting `\r` drain bytes to flush the adapter UART buffer, with 15ms guard intervals between consecutive serial frames.

## [3.0.3] - 2026-09-09
### 🔍 Integrated ECU Diagnostic Logging & Direct OBD Trace File Sharing
- **Persistent Disk Diagnostic Logger (`ObdLogger`)**:
  - Dedicated logging subsystem writing raw vehicle bus communication to local storage (`cacheDir/ecu_logs/obd_trace_YYYYMMDD_HHmmss.txt`).
  - High-precision timestamps (`HH:mm:ss.SSS`), TX frame logging, RX raw responses with elapsed round-trip latency (`[elapsed ms]`), timeouts, and transport exceptions.
  - Automatic session rotation retaining the 10 most recent sessions to cap disk footprint.
- **Immediate Export & Share Sheet (Android `FileProvider`)**:
  - Configured secure paths under `androidx.core.content.FileProvider` (`com.yaris.hvfan.fileprovider`).
  - Integrated **"SHARE LOG"** action in both portrait dashboard and landscape modal dialog, dispatching `Intent.ACTION_SEND` across system targets (Google Drive, Telegram, Email, Bluetooth).
- **Enhanced Diagnostic Terminal (Portrait & Landscape)**:
  - High-contrast color-coded output: cyan outbound commands (`TX >>>`), green valid responses (`RX <<<`), bold red errors/timeouts (`ERR/TIMEOUT`), bright green confirmations.
  - Dedicated **"RESET"** action to flush active terminal buffer and current logs.
  - Full landscape orientation support via modal dialog accessible from the left rail.

## [3.0.2] - 2026-09-09
### 🏎️ Legacy Mobile Optimization & Constant 60 FPS Rendering for Android Devices
- **Zero-Lag Mobile Viewport Scrolling**:
  - Removed `background-attachment: fixed` and flattened gradients on mobile viewports (`<= 768px`), eliminating continuous GPU raster recalculations during scrolling.
  - Removed static `body::before` pseudo-element on mobile viewports to minimize GPU compositing overhead.
- **Compositing Overhead Reduction (Backdrop Filters)**:
  - Replaced expensive `backdrop-filter: blur(...)` passes on mobile with clean opaque surfaces (`#0d1117`, `#131820`), ensuring steady 60/120 FPS on older Android devices (Android 8–11).
- **DOM Virtualization via `content-visibility: auto`**:
  - Applied `content-visibility: auto` with `contain-intrinsic-size` across off-screen landing sections (`#features`, `#setup`, `#sniffer`, `#releases`), reducing initial First Contentful Paint (FCP) latency.
- **Asynchronous Asset Pipeline**:
  - Added `decoding="async"` and `loading="eager"` attributes for key visual assets; synchronized download version targets across `docs/` and `README.md`.

## [3.0.1] - 2026-09-09
### 🛡️ Definitive Resolution of READY/Standby Voltage Flapping & TNGA Gateway Bypass
- **Eliminated READY 13V $\leftrightarrow$ Sleep Cycling**:
  - Removed premature standby transition triggers before polling the engine ECU (`7E0`).
  - Added calibration tolerance for adapters with uncalibrated ADCs: vehicle is treated as active upon receiving CAN responses, preventing spurious disconnection loops.
  - Bypassed 4-second timeout on functional broadcast `7DF` during wake-up, establishing immediate binding to engine ECU `7E0`.
  - Enforced strict hysteresis requiring 10 consecutive read errors and 4 successive standby checks before declaring vehicle powered off.

## [3.0.0] - 2026-09-09
### 🚀 MAJOR RELEASE: Active HV Fan Manual Control (L1–L6), Cockpit Stepper & UDS Direct Bypass
- **Active Fan Manual Override (L1–L6)**:
  - Ergonomic level selector with `[-]` / `[+]` cockpit stepper and direct override toggle in dashboard.
  - Decoupled fan actuation from battery discovery state or temperature thresholds: allows immediate functional acoustic testing even on cold packs.
  - Dynamic UDS Toyota Denso frame generation: `300801`..`300806` (Mode 30 IO Control) with automatic `2F580301`..`2F580306` (Mode 2F) fallback upon NRC `7F30`.
  - Periodic cyclic keep-alive preventing ECU timeout resets, paired with fail-safe OEM release command `300800` (ReturnControlToECU) on termination.
  - Automatic persistence via `SharedPreferences` with restoration upon `READY` state entry.
- **TNGA CAN Negotiation Critical Resolution**:
  - Fixed initialization stalls caused by unsupported Mode 03 queries on TNGA gateways (graceful `NO DATA` tolerance).
  - Robust two-stage handshake (`7DF` Broadcast -> `7E0` Engine ECU) with elimination of unconstrained `AT SP 0` fallback.
  - Purged invalid PID `220101` from Denso HV battery candidate chain.
  - Lightweight, non-destructive auto-recovery without resetting the BLE stack.

## [2.9.22] - 2026-09-09
### 🛠️ Fix Negoziazione CAN e Auto-Recovery su Toyota Yaris TNGA
- Rimozione del blocco Mode 03 e ottimizzazione handshake centralina motore.

## [2.9.21] - 2026-09-09
### ⚡ Allineamento Handshake ELM327 / OBD a Standard Hybrid Assistant & Header Agnostic
- **Handshake Robusto Ibrido Toyota (`Elm327Protocol` & `ObdController`)**:
  - Sostituito l'hard reset distruttivo `AT Z` con doppio Warm Start `AT WS` (delay 150ms buffer flush) per prevenire il freeze e la perdita di baud rate sui cloni ELM327 e BLE.
  - Sequenza configurazione allineata ad Hybrid Assistant: `AT E0`, `ATI`, `STI`, `AT@1`, `AT SP 6` (ISO 15765-4 CAN 11-bit 500k), `AT AT 1`, `AT H1` (Headers ON), `AT L0`, `AT S0`, `AT CAF 1`.
  - Verifica voltaggio 12V reale (`AT RV`) e invio probe universale Mode 03 (`03`) su broadcast `7DF` per svegliare la linea CAN Toyota prima di interrogazioni UDS proprietarie.
- **Parser Agnostico Headers CAN (`ATH1` / `ATH0`)**:
  - `cleanResponse` e tutti i parser PID UDS (batteria HV `2228C1`/`2101`, coding) resi agnostici rispetto alla presenza di prefissi ECU (`7EA`, `7E8`) e frame consecutivi ISO-TP.
  - Protezione anti-collisione su byte `0x7F` nei dati e decodifica sicura con fallback `toIntOrNull(16)`.
- **Hardening Modulo Sniffer (`:sniffer`)**:
  - Emulatore offline aggiornato per gestire tutti i comandi di interrogazione e il probe `03` (`43 00 00 00 00 00 00`).
  - Avviso e modale di conferma nell'interfaccia se si tenta di avviare il bridge TCP senza adattatore Bluetooth connesso.

## [2.9.20] - 2026-09-07
### 🏎️ Cockpit Motorsport Ultra-Premium — Glassmorphism, Glow Neon & GPU Animations 60fps
- **Restyling Visivo Ultra-Premium (R1)**:
  - Sfondo `body` arricchito con 5 layer radiali volumetrici: luce conica da top (rosso corsa), bleeding ice sul bordo destro, contro-accento sinistro, cono luce centrale stile strumentazione da corsa, vignetta profonda agli angoli.
  - Vignetta cockpit su `body::before` doppio-layer: vignetta radiale perimetrale + sfumatura superiore per profondità strumentazione.
  - Card Feature List con glassmorphism ad alto contrasto: `backdrop-filter: blur(18px) saturate(1.4)`, bordo titanio, `inset` highlight e glow rosso neon su hover con `cubic-bezier(0.16, 1, 0.3, 1)`. Tutte le animazioni `will-change: transform, box-shadow` (GPU-accelerated).
- **Simulatore Cockpit & Batteria HV (R2)**:
  - Pulsanti L1–L6 con LED indicator graduato ice-blu→ambra→rosso-corsa al crescere del livello: ogni livello ha sfondo tintato, colore LED e animazione pulsazione propri.
  - Attributo `data-active-level` settato su `.simulator` al cambio livello, che guida via CSS selector i moduli `.airflow` e `.battery-pack i` reattivi al livello selezionato (nessun JS style manipulation, solo attributo → CSS).
  - Flussi `.airflow` con colore e velocità reattivi: L1=ice lento, L2=cyan, L3=argento, L4=ambra, L5=arancio corsa, L6=rosso MAX ultraveloce.
  - Celle `.battery-pack i` con `border-top-color` e `box-shadow` reattivi al livello (ice→amber→red glow).
- **Sezione Sniffer/Bridge & Badge (R3)**:
  - Badge `APK V1.0.0 / STANDALONE MITM` con classe `badge-standalone` e animazione `badge-ice-pulse` 2.4s infinite: il bordo ice pulsa alternando opacity e box-shadow glow.
  - Connettori verticali illuminati tra i 3 step della guida sniffer già presenti e potenziati con glow ice.
- **Performance GPU 60/120 FPS (R4)**:
  - `prefers-reduced-motion` media query già presente e rispettata.
  - Nessun reflow/repaint: tutte le animazioni su `transform`, `opacity`, `box-shadow` e `border-color`.
- **Bump versione**: `versionCode` 35, `versionName` 2.9.20. Aggiornati `build_apk.bat`, `deploy.yml`, tutti i riferimenti in `docs/`.

## [2.9.19] - 2026-09-07
### 🛡️ Hardening Background Bridge, Foreground Service & Robustezza Protocollo OBD
- **Servizio in Primo Piano Dedicato `:sniffer` (`BridgeForegroundService`)**:
  - Implementato `BridgeForegroundService` con tipo `connectedDevice` (Android 14+) e `PARTIAL_WAKE_LOCK` per garantire l'esecuzione senza interruzioni del server TCP e del polling Bluetooth quando Dr. Prius o Car Scanner girano in primo piano.
  - Aggiunta notifica di stato persistente e richiesta runtime del permesso `POST_NOTIFICATIONS` su Android 13+.
- **Hardening del Server TCP e del Parser ELM327**:
  - Abilitato `tcpNoDelay = true` per eliminare l'algoritmo di Nagle e abbattere la latenza di scambio frame.
  - Parsing conforme alle specifiche ELM327: caratteri Line Feed (`\n`) ignorati, delimitazione rigida su Carriage Return (`\r`), gestione tasto backspace/delete e risposta tempestiva al prompt per frame vuoti.
  - Emulazione offline estesa con supporto per parametri spaziati, comandi header (`AT SH`, `AT CRA`, `AT FCS`), PID standard OBD-II (`0100`, `0105`, `010C`, `010D`) e frame UDS batteria ibrida (`2101`, `2181`, `2228C1`).
- **Resilienza BLE e Gestione Traccia Log Continua**:
  - Negoziazione MTU BLE fino a 247 byte alla connessione ed elezione automatica del tipo di scrittura GATT (`WRITE_TYPE_NO_RESPONSE` vs `WRITE_TYPE_DEFAULT`).
  - Correzione della ripresa sessione nel logger: ripresa trasparente sul medesimo file di log senza sovrascritture o leak di descrittori file, con auto-generazione del file al tocco di "Ferma & Condividi Log".

## [2.9.18] - 2026-09-07
### 🚀 Rilascio Modulo Standalone OBD Bridge & Sniffer (:sniffer)
- **Nuovo Modulo Autonomo `:sniffer`**:
  - Creato modulo Gradle autonomo `:sniffer` con APK separato `YarisObdBridge-v1.0.0.apk`.
  - Firma RSA ufficiale con keystore di rilascio (`yaris_release.keystore`).
  - Interfaccia Jetpack Compose dark/carbon con animazione REC lampeggiante, contatore frame TX/RX, gestione permessi Android 12+ e live console monitor con font monospace.
- **Server TCP Man-In-The-Middle su 127.0.0.1:35000**:
  - Implementazione TCP bridge server con `ServerSocket` in ascolto su porta `35000` (loopback e LAN).
  - Proxy trasparente tra client TCP (Dr. Prius, Car Scanner, terminale OBD) e l'adattatore Bluetooth collegato (Classic SPP + BLE GATT).
  - Tracciamento rigoroso di tutti i pacchetti con formato millisecondi `[timestamp] TX >>> [cmd]` e `[timestamp] RX <<< [resp]`.
- **Esportazione & Condivisione Log Integrata**:
  - FileProvider Android configurato per condividere con un tocco la traccia completa `.txt` via WhatsApp, Google Drive, Telegram o Email tramite `Intent.ACTION_SEND`.
- **Portale Web & GitHub Pages**:
  - Aggiunta sezione dedicata `OBD BRIDGE & SNIFFER` su `docs/index.html` con card di download standalone, badge e guida interattiva in 3 passi per Dr. Prius e Car Scanner.
  - Sincronizzati entrambi i binari APK in root e nella cartella `docs/`.
  - Workflow GitHub Actions `deploy.yml` e script di build locale `build_apk.bat` estesi per compilare e distribuire entrambi gli APK ad ogni commit.

## [2.9.17] - 2026-09-07
### ⚡ Pulizia Buffer UDS Batteria, Hardening Concorrenza Coding & Resilienza Eccezioni
- **Rimozione 3E00 Incondizionato da Query Batteria UDS**:
  - Rimosso definitivamente il comando `CMD_TESTER_PRESENT` (`3E00`) prima del candidate probe in `executeBatteryThermalCycle()`, azzerando l'inquinamento dei buffer seriali su adapter cloni ELM327 durante la negoziazione multi-frame.
- **Hardening Concorrenza ECU Coding**:
  - Resa volatile (`@Volatile`) la flag `isEcuOperationInProgress` e garantito il ripristino sicuro di `HEADER_ENGINE_ECU` con blocco `finally` annidato protetto contro eccezioni di trasporto I/O.
  - Verifica di `isEcuOperationInProgress` estesa a ogni sotto-ciclo del dual-rate scheduler (batteria, fast loop e coolant) per prevenire qualsiasi interleaving su cambio header CAN.
- **Resilienza Eccezioni di Trasporto**:
  - Propagazione corretta di `IOException` ed eccezioni di canale fuori dalla fetta batteria per attivare l'isolamento per-ciclo VAL-OBD-008, garantendo il rispetto della cadenza termica e del polling 4000ms.

## [2.9.16] - 2026-09-07
### ⚡ Risoluzione Aggancio CAN Quadro Acceso, Timeout ECU UDS & Protezione Attuazione Ventola
- **Aggancio CAN con Quadro Acceso (12V < 13.0V)**:
  - Gestita la condizione in cui il quadro strumenti è acceso a vettura ferma o quadro inserito con convertitore DC-DC non attivo (tensione 11.6V–12.4V).
  - L'app esegue un probe su CAN broadcast 7DF: se il bus risponde, la vettura viene riconosciuta come attiva uscendo istantaneamente dallo standby senza attendere 13.0V.
- **Isolamento Concorrenza Scheduler Dual-Rate durante Codifiche ECU**:
  - Lo scheduler dual-rate mette in pausa la telemetria continua durante le operazioni di lettura e scrittura delle centraline (`isEcuOperationInProgress`), prevenendo collisioni di pacchetti e `NODATA` su Body, Meter, Aircon e ADAS.
- **Calibrazione Timeout UDS per Centraline Elettroniche di Bordo**:
  - Introdotto `CMD_TIMEOUT_ECU_CODING` (`AT ST 96`, ~614ms) per gli header Body (`750`), Meter (`7C0`), Clima (`7C4`) e ADAS (`7A0`), garantendo alle centraline il tempo necessario per rispondere a frame Mode 21 e 22.
- **Protezione Attuazione Ventola su Telemetria Termica Incompleta**:
  - I comandi ventola e i relativi log non vengono più inviati se la centralina batteria non è ancora stata scoperta (`BatteryEcuDiscoveryState.Discovered`) o se la temperatura massima rilevata è 0.0°C, eliminando i comandi `Ventola HV L6 | Batt: 0.0°C` registrati in diagnostica.
- **Ripristino Header CAN Corretto Post-Codifiche**:
  - Nei blocchi `finally` di `readEcuCustomizations` e `applyEcuCustomization`, l'header CAN ripristinato è `7E0` (Engine ECU standard), riallineando lo stato di comunicazione con il loop veloce di telemetria.
- **Rimozione 3E00 Incondizionato da Query Batteria UDS**:
  - Rimosso il keep-alive preventivo `3E00` (`CMD_TESTER_PRESENT`) prima del candidate probe in `executeBatteryThermalCycle()`, evitando che la risposta inquini i buffer seriali o le risposte multi-frame UDS su adapter cloni ELM327.

## [2.9.15] - 2026-09-07
### 🛡️ Eliminazione Falsi Positivi Scrittura Centralina, Isteresi Standby & Stabilizzazione Bus CAN
- **Eliminazione Falso Positivo Codifiche ECU (`applyEcuCustomization` & `readEcuCustomizations`)**:
  - Implementata validazione rigorosa UDS (`isUdsPositiveResponse`) su tutte le verifiche post-scrittura e lettura di Body ECU, Meter ECU, Aircon e ADAS.
  - Se le centraline rispondono `NODATA`, `ERROR`, `TIMEOUT` o Negative Response Code (`0x7F`), l'app segnala chiaramente l'errore `❌ Scrittura non riuscita: centralina non ha risposto (NODATA). Verifica quadro in READY`, eliminando il falso messaggio di successo che traeva in inganno l'utente a quadro spento.
- **Isteresi Tensione 12V per Standby / READY**:
  - Risolto il loop di "flapping" continuo tra modalità standby e loop attivo quando la tensione oscilla intorno alla soglia di 13.0V.
  - La transizione a READY avviene a `>= 13.0V`, mentre il ritorno a Standby a basso consumo scatta solo a `<= 12.6V` (`isVehicleStandby`) confermato per cicli consecutivi.
- **Protezione da Auto-Recovery Prematuro al Risveglio**:
  - Aggiunto reset del timer `lastAutoRecoveryTimestamp` e `lastStandbyExitTimestamp` all'uscita da standby, impedendo che un auto-recovery scatti dopo soli 3 secondi e interrompa la sincronizzazione UDS.
  - Inserito ritardo di stabilizzazione di 250ms per consentire al ricetrasmettitore CAN dell'adattatore di assestarsi prima delle prime query Mode 01.
- **Suite di Test Unitari**:
  - Aggiunti test di regressione per `isUdsPositiveResponse` e `isVehicleStandby` a garanzia della massima affidabilità nel tempo.

## [2.9.14] - 2026-09-07
### 🔧 Resilienza Query Multi-Frame Batteria Denso HV & Diagnosi Compatibilità Adapter
- **Timeout ELM esteso per risposta multi-frame PID `2228C1`**: Il timeout interno `AT ST` dedicato alla query UDS multi-frame della centralina batteria Denso (header `7E2`) è stato allungato da `AT ST 64` (~410ms) a `AT ST C8` (~819ms) per dare più margine ai cloni ELM327/Vlinker con implementazione lenta o carente del flow-control ISO-TP, senza penalizzare la reattività delle query single-frame su motore, body, meter e ADAS.
- **Probing a fasi `BatteryDiscoveryEngine` più tollerante**: Rivista la gestione dei tentativi e del cooldown per-candidato lungo la catena di fallback PID (`2228C1` → `2228C0` → `220101` → `2101` → `21C3` → `2161`), riducendo i falsi negativi dovuti a singoli timeout isolati su adapter con risposta multi-frame instabile.
- **Nuovo alert utente per probabile incompatibilità hardware dell'adapter**: Se l'intera fallback chain fallisce ripetutamente su più cicli di discovery consecutivi pur con bus CAN motore attivo e dati regolari, l'app mostra ora un avviso dedicato che invita l'utente a verificare la compatibilità del proprio dongle OBD-II con le risposte multi-frame ISO-TP, invece di ripetere indefinitamente tentativi silenziosi.
- **Documentazione**: Aggiunta sezione dedicata in `README.md` per aiutare l'utente a distinguere un limite hardware dell'adapter da un malfunzionamento software dell'app.

## [2.9.13] - 2026-09-06
### 🔌 Aggancio CAN Vlinker verificato e fallback protocollo automatico
- **Header broadcast `7DF` esplicito**: ogni handshake iniziale, risveglio da standby e auto-recovery imposta realmente l'header funzionale, senza affidarsi allo stato precedente del clone ELM327.
- **Fallback `AT SP 0` basato sui dati reali**: se `AT SP 6` risponde `OK` ma il PID `0100` restituisce `NO DATA`, l'app passa automaticamente all'auto-detect e ripete il probe con una finestra estesa.
- **Conferma ECU prima del successo**: un comando AT accettato non viene più scambiato per comunicazione CAN; il recovery completo richiede dati validi dalla centralina batteria, mentre l'aggancio del solo bus motore è indicato come parziale.
- **Risveglio da standby completo**: alla rilevazione READY viene verificato prima il bus broadcast e poi vengono interrogate le centraline motore e batteria.
- **Test di regressione**: aggiunta copertura per header `7DF` e rifiuto di risposte `OK`, `NO DATA` o PID non corrispondenti come prova di connessione.

## [2.9.12] - 2026-09-06
### 🛡️ Risoluzione Watchdog Auto-Recovery, Fallback PID Batteria & Stabilizzazione CAN
- **Watchdog di Silenzio CAN Resiliente**: Elevate le soglie di silenzio CAN da 5s a 15s (12s per standby) e l'intervallo di guardia dell'auto-recovery da 10s a 25s, eliminando il ciclo infinito di `AT WS` e reset che interrompeva continuamente la lettura e azzerava i buffer.
- **Tester Present Keep-Alive su ECU Denso (`3E00`)**: Aggiunto invio preventivo di `3E00` prima delle query batteria su ECU `7E2`, mantenendo costantemente attiva la sessione diagnostica UDS Toyota senza timeout di sessione.
- **Estensione Catena di Fallback PID Batteria**: Aggiunti PID alternativi Mode 22 (`220101`) e Mode 21 (`2101`) in `BATTERY_FALLBACK_PIDS` con gestione dei relativi identificatori di risposta (`620101` e `6101`), garantendo il recupero delle temperature anche su versioni firmware Denso non standard.
- **Correzione Logging Temperatura Batteria**: Eliminato il log fuorviante `Batt: 0.0°C` in attesa del primo aggancio termico valido; l'app ora segnala esplicitamente `In attesa telemetria termica...` evitando false letture o attivazioni improprie.
- **Calibrazione Switching Header CAN**: Esteso il ritardo post-`AT SH` a 120ms per garantire tempo di assestamento ai cloni ELM327 e ai dongle Vgate/Vlinker prima dell'invio dei frame successivi.
- **Sincronizzazione Completa Release & Docs**: Aggiornati build script, workflow CI GitHub Actions, documentazione e portale web servito da GitHub Pages.

## [2.9.11] - 2026-09-06
### 📦 Download Sito Sempre Disponibile
- **APK tracciato anche in `docs/`**: GitHub Pages (sorgente "Deploy from a branch", `main` + `/docs`) ora serve subito l'APK committato, senza attendere la pipeline CI.
- **Script di build sincronizzato**: `build_apk.bat` genera l'APK sia in root che in `docs/`.
- **Policy aggiornata**: `AGENTS.md` riflette il nuovo flusso (un solo APK versionato in root e in docs, nessun file rolling).
- Nessuna modifica alla logica dell'app rispetto a v2.9.10.

## [2.9.10] - 2026-09-06
### 🛠️ Espansione Codifiche: ADAS, Clima & Opzioni Mancanti
- **Nuove codifiche ADAS (ECU 7A0)**: RCTA Allerta Traffico Posteriore (`3B62`), LTA Mantenimento Corsia (`3B63`) e PCS con memoria dell'ultimo stato (`3B64`).
- **Nuove codifiche Clima (ECU 7C4)**: Ventilatore attivo con sbrinatore (`3B52`) e calibrazione quadrante temperatura da -2°C a +2°C (`3B53`).
- **Codifiche esistenti ora operative**: Cicalino cinture posteriori (`3B03`), sblocco selettivo porte (`3B25`), intermittenza legata alla velocità (`3B42`) e illuminazione vano piedi in marcia (`3B34`) ora inviano il comando UDS corretto.
- **Nuove opzioni UI**: Frecce comfort a 6 lampeggi e disattivazione, Follow Me Home 90 secondi e ritardo retrocamera 10 secondi.
- **Test aggiornati**: Asserzioni sui nuovi default in `EcuCodingAndPipelineTest.kt`.
- Nota: i DataIdentifier seguono lo schema interno dell'app; verificarne l'effetto sulla propria vettura prima dell'uso.

## [2.9.9] - 2026-09-06
### 🏁 Nuovo Pit Wall Digitale & Portale Motorsport Responsive
- **Nuova identità visiva del portale web**: Tipografia Barlow Condensed / Barlow / IBM Plex Mono, palette carbonio/alluminio/rosso/ghiaccio e layout a pit wall per `docs/index.html`, `docs/preview.html` e `docs/404.html`.
- **Demo interattiva condivisa**: Simulatore unico (`docs/simulator.js` + `docs/site.css`) con scheda ventola (L1–L6), cronometro Dragy 0-100 con avvio, pausa e reset, shift-light e matrice 4 celle Denso.
- **Scena termica hero animata**: Vista concettuale del pacco HV con flusso d'aria e toggle demo L3/L6 sulla home.
- **Repository a singolo APK**: Un solo file `YarisHvFanControl-v2.9.9.apk` nella root; la cartella `docs/` non traccia più file `.apk` (generati dalla pipeline CI durante il deploy).
- **Sincronizzazione completa riferimenti**: `build_apk.bat`, `.github/workflows/deploy.yml`, `README.md` e `AGENTS.md` allineati alla release v2.9.9.

## [2.9.8] - 2026-09-06
### 🛡️ OBD CAN Init Fix: Rimozione Filtro AT CRA & Protocol Timing Calibration
- **Risoluzione Definitiva Bug Connessione CAN Dongle Clone / Vlinker**: Rimosso l'invio del comando `AT CRA` (filtro di ricezione CAN) che su dongle cloni ELM327 e Vlinker causava il drop silenzioso di tutti i pacchetti in ingresso (`NO DATA` su ogni PID standard e proprietario). Aggiunto `AT AR` in sequenza di init per azzerare filtri residui sul chip.
- **Riorganizzazione Sequenza Inizializzazione ELM327**: Selezione esplicita del protocollo CAN 11-bit 500k (`AT SP 6`) anticipata rispetto alla configurazione del timing (`AT ST 96` a ~614ms) con verifica di conformità via `AT DPN`.
- **Nuovo Stadio 0 di Aggancio Bus CAN in Broadcast (`7DF`)**: Handshake preliminare con query PID `0100` su header funzionale broadcast per agganciare il bus ed eliminare lo stato `SEARCHING...` prima dell'applicazione degli header fisici (`7E0` / `7E2`).
- **Calibrazione Timeout e Delay di Stabilizzazione**: Sostituito `AT ST 20` (131ms, sotto il default ELM327) con `AT ST 32` (~205ms) per la telemetria continua ed esteso il timeout a 4000ms per le risposte multi-frame UDS batteria (`2228C1`), con delay di 100ms prima del primo comando UDS.
- **Logging Diagnostico Trasparente**: Log completo della risposta grezza ricevuta per ciascun PID in caso di fallimento o fallback, con avvisi chiari sullo stato READY e tensione 12V.
- **Aggiornamento Descrittore BLE Android 13+ (API 33+)**: Implementata gestione moderna `writeDescriptor` su Android 13+ con fallback deprecato per versioni precedenti e correzione codifica stringhe.

## [2.9.7] - 2026-09-06
### 🏎️ 3K Motorsport High-Definition Carbon Fiber Weave & Cockpit Integration
- **Texture Procedurale 3K Motorsport Twill 2x2 in Jetpack Compose**: Sostituito il microscopico tile 8x8 con un pattern procedurale ad alta risoluzione calibrato su scala reale motorsport per display AMOLED ad alta densità (36dp / 400+ PPI come Oppo A94 5G).
- **Fotometria Anisotropa Realistica**: Chiara differenziazione fisica tra fibre orizzontali ad alto riflesso titanio/grafite (`#2A303E` / `#363F50`), fibre verticali a riflessione diffusa in mezzitoni grafite (`#161B24`), e solchi d'ombra carbonio puro (`#080A0E`) a delimitazione dei fasci.
- **Integrazione Cockpit MoTeC / Gazoo Racing Continua (Portrait & Landscape)**: Sfondo monoscocca a fibra di carbonio esteso e continuo a tutto schermo sia in Portrait che Landscape (incluso safe margin punch-hole da 32dp), con bordi card telemetriche ad alto contrasto al titanio (`#2A364B`) e vignettatura radiale GPU morbida.
- **Sincronizzazione Web & Simulatore Interattivo**: Nuova trama vettoriale SVG/CSS ad alta fedeltà integrata sul portale GitHub Pages (`docs/index.html`, `docs/404.html`) e attorno alla cornice del simulatore interattivo (`docs/preview.html`).

## [2.9.6] - 2026-09-06
### ⚡ Dual-Engine OBD Connection Architecture (Dr. Prius + Hybrid Assistant)
- **Architettura Dual-Engine Dr. Prius & Hybrid Assistant**: Stack universale di base ispirato a Dr. Prius con `AT CAF 1` (CAN Auto-Formatting nativo standard per l'assemblaggio trasparente dei frame ISO-TP multi-frame), combinato con rilevamento hardware non distruttivo STN/OBDLink (`ST DI`, `ATI`) per attivare il Flow Control avanzato solo sui chip che lo supportano nativamente.
- **Risoluzione Definitiva per Vlinker & Cloni ELM327**: Mantenimento rigoroso del Flow Control automatico di sistema su adapter Vlinker (`Android-Vlink`) e cloni ELM327 senza invio di comandi `AT FC` che corrompono il buffer seriale.
- **Handshake CAN a Due Stadi con Aggancio Rapido**: Sincronizzazione preliminare su ECU motore (`7E0`/`7E8`) con PID standard OBD-II `0100` per consentire al dongle di completare la fase `SEARCHING...` del protocollo CAN 11-bit 500k, seguita da aggancio istantaneo su Denso HV Battery ECU (`7E2`/`7EA`).
- **Catena di Fallback Dinamica per Pacco Batteria**: Sequenza automatica e trasparente `2228C1` &rarr; `2228C0` &rarr; `21C3` &rarr; `2161` in caso di risposte negative o varianti firmware della centralina batteria.
- **Streaming RFCOMM Thread-Safe & Rilevamento READY Resiliente**: Buffer seriale atomico con sincronizzazione continua e rilevamento dello stato READY senza blocchi o falsi allarmi di "CENTRALINA NON RISPONDE".

## [2.9.5] - 2026-09-06
- Fix critico: Negoziazione protocollo CAN con PID OBD-II standard (0100) prima delle query UDS Toyota
- Fix critico: Rilevamento supporto Flow Control (AT FC) prima della configurazione — compatibilità Vlinker/clone
- Fix: Timeout estesi per prima query CAN (12s per SEARCHING) e query batteria HV (6s)
- Fix: Tentativo PID alternativi (2228C0, 2161, 21C3) se 2228C1 non risponde
- Fix: Auto-recovery non invia più comandi AT FC su adattatori non compatibili
- Fix: Fallback automatico su AT SP 0 (auto-detect) se AT SP 6 non trova il protocollo
## [2.9.4] - 2026-09-05
### 🛡️ Thread-Safe Stream Architecture, Flow Control Hardening & Firmware Version Banner Immunity
#### Added & Improved
- **Immunità del Parser Tensione a Banner Firmware ELM327/STN**:
  - Risolto potenziale falso riconoscimento della versione dongle (es. `ELM327 v1.5` o `STN1110 v2.2`) come tensione 12V: `parseBatteryVoltage()` dà priorità assoluta al suffisso `V` e applica filtraggio sul range reale di tensione automotive (8.0V - 18.0V).
- **Architettura di Streaming Bluetooth SPP Thread-Safe**:
  - Rimosso l'accesso concorrente su `socketInputStream` in `sendCommand`: tutta la ricezione dei byte avviene in modo atomico nel thread reader `startSocketReader`, prevenendo race condition, corruzione dei frame o disconnessioni spurie del socket RFCOMM.
- **Supporto Moderno Android 13+ (API 33+) BLE GATT**:
  - Implementato l'helper `writeGattCharacteristic` con dispatch a `BluetoothStatusCodes.SUCCESS`, eliminando deprecazioni del compilatore e garantendo compatibilità nativa su Android 13/14+.
- **Raffinamento Hardware Flow Control ISO-TP & Timeout Adattivo**:
  - Memorizzazione permanente dei registri `AT FC SH 7E2` e `AT FC SD 300000` all'avvio o recovery senza overhead di riscrittura ridondante ad ogni ciclo.
  - Assegnazione dinamica dei timeout CAN: `AT ST 64` (~400ms) durante l'accesso UDS multi-frame alla centralina batteria (`7E2`) e `AT ST 20` (~80ms) per la telemetria rapida motore (`7E0`).
  - Rilevamento automatico di compatibilità `AT FC SM 1` con fallback trasparente in caso di cloni ELM327 non standard.
- **Grace Period di Avvio & Ripristino Completo in Auto-Recovery**:
  - Evitato il trigger immediato di auto-recovery al primissimo ciclo dopo l'inizializzazione quando `lastValidCanTimestamp == 0L`, introducendo un grace period di 8s.
  - Inclusione esplicita di `AT AT 1` e `AT CAF 1` durante la sequenza di warm auto-recovery.
  - Probe CAN periodico (~10s) in modalità standby per sincronizzazione immediata se l'auto è attiva con batteria sotto i 13.0V.
- **UI Dashboard Standby & Landscape Banner**:
  - Distinzione visiva tra auto in standby a basso consumo (`AccentCyan` e icona hourglass) ed effettivo allarme centralina non rispondente (`WarningOrange`).
  - Banner di standby integrato coerentemente sia nel layout verticale (Portrait) che orizzontale (Landscape).
- **Pipeline di Release & Version Increment**:
  - Bump versione a `v2.9.4` (`versionCode = 19`), aggiornamento file di build e sincronizzazione completa sito web `docs/`.

## [2.9.3] - 2026-09-05
### 🔌 Intelligent Hybrid Assistant Handshake & Hardware ISO-TP Flow Control
#### Added & Improved
- **Handshake Avanzato Intelligente Hybrid Assistant & Wake-Up Protocol**:
  - **Sequenza Preventiva di Risveglio**: Invio di `\r\r` con flushing del buffer per risvegliare dongle ELM327/Vgate iCar Pro da stati di sleep profondo o risparmio energetico prima dei comandi AT.
  - **Warm Start Non-Bloccante**: Utilizzo di `AT WS` con attesa calibrata (600ms) evitando blocchi baudrate e reboot lenti di `AT Z`.
  - **Rilevamento Dinamico Stato READY & Tensione 12V (`AT RV`)**: Lettura della tensione ausiliaria 12V reale tramite `AT RV`. Se la tensione è >= 13.0V (convertitore DC-DC attivo), il veicolo è rilevato in stato READY; se < 12.8V o CAN inattivo, il controller entra in modalità Standby a basso consumo (loop distanziato a 2.5s con sola query di tensione), prevenendo saturazione del bus CAN ed errori a raffica `NO DATA` a veicolo spento.
- **Hardware ISO-TP Flow Control su Centralina Batteria Denso**:
  - Configurazione automatica dei filtri e controllo di flusso hardware per frame UDS multi-frame (PID `2228C1` e Mode 30):
    - `AT CRA 7EA` (filtro hardware CAN ricezione Battery ECU).
    - `AT FC SH 7E2` (header di controllo di flusso verso Battery ECU).
    - `AT FC SD 300000` (Clear To Send: Block Size = 0, Separation Time = 0ms per throughput istantaneo).
    - `AT FC SM 1` (modalità Flow Control custom definita dall'utente abilitata durante l'interrogazione della centralina HV e ripristino dinamico per altre centraline).
- **Silent Reconnection Watchdog a Circuito Chiuso**:
  - Eliminati pop-up, allarmi o toast invadenti in caso di disconnessione o perdita transitoria di pacchetti: il sistema passa silenziosamente a `Reconnecting` ed esegue backoff esponenziale autonomo fino al ripristino del segnale.
- **Connessione Automatica Istantanea in Background**:
  - All'avvio dell'app o del Foreground Service, se è presente un dispositivo Vgate/OBD accoppiato o precedentemente salvato, la connessione si avvia immediatamente senza forzare la finestra modale di scansione.
- **UI Dashboard & Service Notification**:
  - Badge di connessione e notifiche del servizio arricchite con visualizzazione tensione batteria 12V reale e indicazione chiara dello stato (`● READY ONLINE (XX.XV)`, `▲ STANDBY (XX.XV) - ATTESA READY`, `○ AUTO-RETRY #N`).
- **Suite di Test Unitari**:
  - Aggiunti test in `ObdControllerIntegrationTest.kt` per il parsing della tensione, isteresi dello stato READY, transizioni di standby e costanti ISO-TP Flow Control.
- **Pipeline & Artefatti di Rilascio**:
  - Bump versione a `v2.9.3` (`versionCode = 18`).
  - Sincronizzazione script di build `build_apk.bat`, workflow CI/CD `.github/workflows/deploy.yml`, e portale web `docs/` (`index.html`, `404.html`, `preview.html`) con `YarisHvFanControl-v2.9.3.apk`.

---

## [2.9.2] - 2026-09-05
### 🧹 Repository Hygiene & GitHub Pages Streamlining
#### Added & Improved
- **Semplificazione Hosting & Disattivazione Vercel**:
  - Rimozione configurazione ridondante `docs/vercel.json`, consolidando l'infrastruttura di deploy e hosting pubblico direttamente su **GitHub Pages** (`https://francescocastaldi.github.io/yaris-hv-fan-optimizer/`).
  - Ottimizzazione link e documentazione in `README.md` e direttive agenti in `AGENTS.md`.
- **Pipeline & Artefatti di Rilascio**:
  - Bump versione incrementale `v2.9.2` (`versionCode = 17`).
  - Sincronizzazione automatica degli script di compilazione `build_apk.bat` e CI/CD `.github/workflows/deploy.yml` per l'erogazione di `YarisHvFanControl-v2.9.2.apk`.

---

## [2.9.1] - 2026-09-05
### 🏁 Motorsport Micro-Twill Carbon Fiber Background & High-Speed Tiling
#### Added & Improved
- **Pattern Esclusivo Fibra di Carbonio Micro-Twill 2x2**:
  - Implementazione pattern opaco Motorsport Micro-Twill Weave 2x2 a 45° ad elevato contrasto ed eleganza estetica.
  - Generazione hardware-accelerata nativa su Android (`CarbonBackground.kt`) tramite `BitmapShader` (8x8 px) e tiling a ciclo zero-CPU, con morbida vignettatura radiale scura (`#0B0E11` -> `#050709`) per ottimizzazione consumi su display OLED/AMOLED.
  - Applicazione uniforme alla Dashboard nativa Jetpack Compose (`DashboardScreen.kt`) per entrambi gli orientamenti portrait e landscape.
- **Sincronizzazione Web & Brand Styling**:
  - Applicazione del texture gradient SVG Micro-Twill con overlay radiale scuro su tutto il portale web (`docs/index.html`, `docs/404.html`, `docs/preview.html`).
  - Aggiornamento dei badge di rilascio, card di download e link APK alla versione `v2.9.1` (`versionCode = 16`).
- **Pipeline di Build e Deploy**:
  - Aggiornato `build_apk.bat` con generazione automatica di `YarisHvFanControl-v2.9.1.apk`.
  - Configurate le route Vercel (`docs/vercel.json`) e il workflow CI/CD GitHub Actions (`.github/workflows/deploy.yml`).

---

## [2.9.0] - 2026-09-05
### 🌀 Smart Auto-Cooling Protection Suite & Dynamic Thermal Hysteresis
#### Added & Improved
- **Smart Auto-Cooling Protection System (Algoritmo Predittivo a Circuito Chiuso)**:
  - Monitoraggio continuo a ciclo chiuso della temperatura massima celle del pacco trazione litio Denso (`maxTemp`).
  - **Innesco automatico**: invia comandi Mode 30 IO Control non appena la temperatura raggiunge la soglia impostata (`triggerTemp`), senza richiedere l'intervento manuale del guidatore.
  - **Isteresi di spegnimento configurabile**: previene pendolamenti o continui cicli on/off, rilasciando il controllo alla centralina di bordo solo quando la temperatura scende a `triggerTemp - hysteresis` (es. innesco a 34°C, spegnimento a 32°C).
  - **Selettore velocità target ventola**: modulabile liberamente da Livello 1 a Livello 6, con supporto a 3 preset rapidi (*Gazoo Track* 30°C/L6, *Bilanciato* 33.5°C/L6, *Comfort* 36°C/L4).
- **Protezione Attiva 24/7 in Background**:
  - Il servizio Android in primo piano (`FanControlForegroundService`) con CPU partial wake-lock mantiene attivo il controllo anche con smartphone bloccato, schermo spento o con app di navigazione aperte (Google Maps, Waze).
  - Notifica persistente nella tendina di Android arricchita con stato live (`🌀 Auto-Cooling ATTIVO (L6)` e target di cutoff termico).
- **Feedback Acustico & Tattile all'Innesco**:
  - Segnale audio discreto (`ToneGenerator`) e vibrazione haptic personalizzata (`Vibrator` waveform) all'avvio e al rilascio del raffreddamento.
  - Aggiunto permesso di sistema `android.permission.VIBRATE` nel manifest.
- **Persistenza Parametri & Suite UI**:
  - Salvataggio persistente in `AppPreferences` delle soglie e velocità prescelte.
  - Riprogettazione card di regolazione termica nella scheda `VENTOLA` con controlli motorsport ad alta ergonomia.
- **Sincronizzazione Release & Portale Web**:
  - Aggiornamento build Gradle a `v2.9.0` (`versionCode = 15`).
  - Aggiornamento portale web `docs/` e script di build per la generazione e download di `YarisHvFanControl-v2.9.0.apk`.

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

