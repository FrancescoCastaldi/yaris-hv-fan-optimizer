# Architecture & Technical Specifications

## 1. System Architecture

The application follows Modern Android Development (MAD) architecture principles utilizing Jetpack Compose, Kotlin Coroutines, StateFlow, and Android Services.

### 1.1 BleManager
- Manages GATT connection lifecycle via `BluetoothGattCallback`.
- Auto-negotiates 247-byte MTU (`requestMtu(247)`) upon connection to enable high-speed transmission of multi-frame CAN responses without chunking delays.
- Automatically discovers standard OBD BLE characteristic UUIDs across multiple vendor specifications (Nordic NUS `6E40...`, Microchip ISSC `4953...`, TI CC2540 `FFE0/FFE1`, vLinker/Carista `FFF0/FFF1/FFF2`).
- Implements thread-safe asynchronous command dispatching using Kotlin `Mutex` and `CompletableDeferred` with 3-second timeout protection.
- Supports cyclic auto-reconnect fallback with 3.5-second polling if connection drops or when vehicle ignition turns on after app startup.

### 1.2 ObdController
- Background asynchronous orchestrator running on `Dispatchers.IO`.
- Manages protocol initialization sequences (`AT Z`, `AT E0`, `AT SP 6`, `AT SH 7E2`, `AT AR`) with automatic clone/STN hardware detection (`ATI`, `ST DI`) to decide whether custom `AT FC` flow-control commands are safe to send.
- Runs a dual-rate adaptive scheduler (`runDualRateScheduler`) instead of a fixed cyclic interval:
  1. Battery slice (every 3500ms, or on-demand): delegates PID selection to `BatteryDiscoveryEngine`, which probes exactly one candidate of the fallback chain (`2228C1` → `2228C0` → `220101` → `2101` → `21C3` → `2161`) per eligible slice, places failed candidates in a 30s cooldown, and latches the first valid responder.
  2. Fast engine telemetry (every scheduler tick, ~140ms nominal): Vehicle Speed (`010D`), Engine RPM (`010C`), Throttle (`0111`), dispatched via a child coroutine so a slow/timed-out battery slice never starves it (VAL-OBD-007).
  3. Coolant/IAT warm-up slice (every 4000ms): Engine Coolant Temperature (`0105`) and Intake/Ambient Temperature (`010F`), also immune to battery-slice timeouts (VAL-OBD-012).
- Applies a conservative ELM-internal timeout (`AT ST C8`, ~819ms) specifically when the CAN header is switched to the battery ECU (`7E2`), to accommodate slow/partial ISO-TP flow-control implementations on ELM327 clone/Vlinker adapters without penalizing the fast engine telemetry loop (`AT ST 20`).
- Surfaces a dedicated `batteryAdapterLimitationWarning` in `ObdLiveState` when the entire battery fallback chain fails for >= 2 consecutive full cycles, distinguishing a likely OBD adapter hardware limitation from a transient/software issue.
- Emits real-time state updates through `MutableStateFlow<ObdLiveState>`.

#### 1.2.1 Test Coverage & Resilience Validation
`ObdController`, `BatteryDiscoveryEngine`, and `Elm327Protocol` are covered by ~90 simulated unit/integration tests against a fake `ObdTransport` (no Android context or real hardware required), including a dedicated `AdapterErrorHandlingIntegrationTest` suite exercising persistent NODATA, malformed payloads, transport exceptions, negative UDS responses, and flaky-adapter recovery scenarios end-to-end through the controller.

### 1.3 FanControlForegroundService
- Android Foreground Service with type `FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE`.
- Maintains active BLE connection and IO control polling regardless of activity lifecycle state (screen off, split screen, navigation apps in foreground).
- Displays persistent notification with real-time temperature and fan level telemetry.
- Implements `onTaskRemoved` cleanup to ensure BLE disconnecting and vehicle ECU returning to factory baseline when user swipes app away.

### 1.4 Jetpack Compose Presentation Layer
- High-contrast OLED dark interface utilizing Material3 design guidelines.
- Dynamic color-coded stage badges and progress animations for Toyota HSD Warm-Up Phases S0 through S4.
- Real-time diagnostic stream terminal with automatic auto-scroll for OBD protocol verification.
