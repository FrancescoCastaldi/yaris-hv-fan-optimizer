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
- Manages protocol initialization sequences (`AT Z`, `AT E0`, `AT SP 6`, `AT SH 7E2`, `AT CRA 7EA`).
- Executes cyclic interleaved polling every 1.5 seconds:
  1. HV Battery Pack Diagnostics (Mode 22 `22 28 C1` or legacy Mode 21 `21 61`).
  2. Active Test IO Control (`30 08 06` / `2F 58 03 06` for Fan Level 6) or Tester Present keep-alive (`3E 00`).
  3. Engine Coolant Temperature (Mode 01 `0105`), Intake/Ambient Temperature (Mode 01 `010F`), and Engine RPM (Mode 01 `010C`).
- Emits real-time state updates through `MutableStateFlow<ObdLiveState>`.

### 1.3 FanControlForegroundService
- Android Foreground Service with type `FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE`.
- Maintains active BLE connection and IO control polling regardless of activity lifecycle state (screen off, split screen, navigation apps in foreground).
- Displays persistent notification with real-time temperature and fan level telemetry.
- Implements `onTaskRemoved` cleanup to ensure BLE disconnecting and vehicle ECU returning to factory baseline when user swipes app away.

### 1.4 Jetpack Compose Presentation Layer
- High-contrast OLED dark interface utilizing Material3 design guidelines.
- Dynamic color-coded stage badges and progress animations for Toyota HSD Warm-Up Phases S0 through S4.
- Real-time diagnostic stream terminal with automatic auto-scroll for OBD protocol verification.
