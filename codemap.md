# Yaris HV Hybrid Control System — Architectural Codemap

High-performance Android telemetry, HV battery thermal regulation, and UDS ECU coding suite for **Toyota Yaris Hybrid MK4 (XP210 / TNGA-B platform, MY2020–2025+)**.

---

## 1. System Topology & Communication Flow

The application coordinates multi-ECU communications across Bluetooth Low Energy (BLE) and Bluetooth Classic SPP to the vehicle ISO 15765-4 CAN bus (500 kbps, 11-bit ID).

```text
[ Android Presentation Layer (Jetpack Compose M3) ]
                       ▲
                       │ MutableStateFlow<ObdLiveState>
                       ▼
[ FanControlForegroundService (Connected Device Service) ]
                       ▲
                       │ Serialized Command Pipeline (obdTransactionMutex)
                       ▼
[ ObdController (Dual-Rate Adaptive Scheduler) ]
         ├── Engine Telemetry (~140ms fast loop: Speed, RPM, Throttle)
         ├── Coolant & Warm-Up (4000ms loop: ECT, IAT, S0-S4 Detection)
         ├── Battery Thermal (3500ms slice: BatteryDiscoveryEngine)
         └── UDS ECU Coding (On-Demand Mutex-Protected Sessions)
                       ▲
                       │ Flow Control & AT/ISO-TP Framing
                       ▼
[ BleManager / Bluetooth Serial Transport ]
                       ▲
                       │ BLE 4.0+ (MTU 247) / RFCOMM SPP
                       ▼
[ OBD-II Hardware Adapter (STN / ELM327 / vLinker / Vgate) ]
                       ▲
                       │ ISO 15765-4 High-Speed CAN (500 kbaud)
                       ▼
[ Vehicle Multi-ECU Bus ]
   ├── 0x7E0 / 0x7E8 : Engine / Hybrid Synergy Drive (HSD)
   ├── 0x7E2 / 0x7EA : HV Battery Management System (Denso BMS)
   ├── 0x750 / 0x758 : Main Body ECU
   ├── 0x7C0 / 0x7C8 : Combination Meter ECU
   ├── 0x7C4 / 0x7CC : Air Conditioning Amplifier
   └── 0x7A0 / 0x7A8 : ADAS / Camera & Radar Gateway
```

---

## 2. Directory & Module Organization

| Path | Purpose |
| :--- | :--- |
| `app/src/main/java/com/yaris/hvfan/` | Primary Android application module |
| `app/src/main/java/com/yaris/hvfan/ble/` | BLE GATT engine, UUID abstraction, MTU negotiation, connection watchdog |
| `app/src/main/java/com/yaris/hvfan/obd/` | OBD-II / UDS protocol logic, multi-rate scheduler, PID discovery engine |
| `app/src/main/java/com/yaris/hvfan/service/` | FanControlForegroundService maintaining background loop & notifications |
| `app/src/main/java/com/yaris/hvfan/ui/` | Jetpack Compose M3 UI: Cockpit, Fan Management, ECU Coding, Terminal |
| `app/src/test/java/com/yaris/hvfan/` | Comprehensive test suite (~100 unit & integration tests, zero-device mocks) |
| `sniffer/` | Standalone :sniffer module: MITM TCP proxy (127.0.0.1:35000) & trace logger |
| `docs/` | GitHub Pages deployed site: interactive simulator, release downloads, specifications |

---

## 3. Core Component Reference

### 3.1 Protocol & Communication (`com.yaris.hvfan.obd`)
- `ObdController.kt`: Central orchestrator executing a single-flight dual-rate loop. Ensures fast engine telemetry is never starved by battery multi-frame queries. Manages atomic hardware CAN filter switching (`AT SH` + `AT CRA`).
- `ToyotaYarisCommands.kt`: Automotive diagnostic catalog covering TNGA Mode 21/22/2F/30/3B/2E commands, deterministic ECU-to-filter mappings, and payload parsers.
- `BatteryDiscoveryEngine.kt`: Candidate fallback engine (`2228C1` → `2228C0` → `2101` → `21C3` → `2161`) with candidate cooldowns, latching, and hardware adapter limitation detection.
- `Elm327Parser.kt`: Robust ASCII-to-hex response cleaner, negative response (`0x7F`) handler, and header-agnostic payload extractor.
- `ObdLogger.kt`: File-based rotating trace logger writing millisecond-accurate TX/RX frames for field troubleshooting.

### 3.2 Bluetooth & Transport Layer (`com.yaris.hvfan.ble`)
- `BleManager.kt`: Multi-vendor GATT discovery (Nordic NUS, Microchip ISSC, TI CC2540, vLinker), automatic MTU 247 exchange, mutexed single-flight command queue, and exponential backoff auto-reconnect.

### 3.3 Background Execution & UI Layer
- `FanControlForegroundService.kt`: Background lifecycle host holding partial wake-locks, ongoing notification updates, and thermal safety trip enforcement.
- `MainActivity.kt` & `MainScreen.kt`: Compose UI presentation featuring Cockpit MoTeC gauges, Dragy sprint timer, Denso fan speed stepper, and ECU configuration tables.

### 3.4 Standalone Sniffer (`:sniffer`)
- `BridgeForegroundService.kt`: Local TCP proxy bridging third-party apps (Dr. Prius, Car Scanner) to connected OBD dongles with bidirectional raw logging.

---

## 4. Operational Invariants

1. **Zero Telemetry Starvation**: Fast engine telemetry (speed, RPM, throttle) executes in an isolated time slice, unaffected by slow or timing-out battery multi-frame requests.
2. **Deterministic Filter Pairing**: Every `AT SH <ECU>` command is paired with its atomic `AT CRA <FILTER>` counterpart to prevent dropped CAN frames on hardware filter masks.
3. **Serial Mutex Exclusion**: All read and write operations on the OBD channel are serialized via `obdTransactionMutex` to avoid UART buffer corruption.
4. **Fail-Safe OEM Thermal Restoration**: On app shutdown or disconnect, control of the cooling fan is explicitly released back to the OEM hybrid control computer (`2F 58 00` / `30 08 00`).
