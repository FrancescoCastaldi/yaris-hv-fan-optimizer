# Implementation Roadmap & Verification Milestones

**Core Objective:** Resolve vehicle READY (13V) $\leftrightarrow$ Standby/Sleep cycling on Toyota Yaris TNGA XP210 with uncalibrated OBD dongle ADCs, and establish single-flight CAN scheduling.

---

## Task Progress

| Milestone | Objective | Status | Implementation Details |
| :--- | :--- | :---: | :--- |
| **F1** | Eliminate premature standby before Stage 1 | Completed | Guarantee Engine ECU (`7E0`) query before evaluating sleep triggers |
| **F2** | Bypass 4-second timeout on `7DF` broadcast wake-up | Completed | Direct, immediate connection binding to `7E0` |
| **F3** | Robust anti-flapping hysteresis on `AT RV` | Completed | Decouple transient voltage drops from immediate sleep state entry |
| **F4** | Automated test suite verification (100% passing) | Completed | Unit & integration test execution under mocked transports |
| **F5** | Version synchronization across build and deployment | Completed | Synchronized across Gradle, scripts, and documentation |
| **F6** | RSA-signed release APK compilation | Completed | Generated via `build_apk.bat` for both main and sniffer apps |
| **F7** | Staging and release sign-off | Completed | Repository ready for production deployment |

---

## Technical Context
- Eliminates flapping between READY and SLEEP modes caused by OBD dongle ADCs measuring between 12.8V and 12.9V.
- Ensures uninterrupted telemetry streaming and predictable fan control overrides under all vehicle driving conditions.
