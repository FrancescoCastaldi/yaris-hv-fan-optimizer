# Toyota Yaris MK4 Hybrid - HV Battery Cooling & Warm-Up Optimizer 🏎️⚡

[![Android](https://img.shields.io/badge/Platform-Android%208.0%2B%20(API%2026%2B)-3DDC84.svg?style=flat&logo=android)](https://www.android.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.24-7F52FF.svg?style=flat&logo=kotlin)](https://kotlinlang.org/)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose%20M3-4285F4.svg?style=flat&logo=jetpackcompose)](https://developer.android.com/jetpack/compose)
[![Bluetooth](https://img.shields.io/badge/Bluetooth-BLE%204.0%2B%20GATT-0082FC.svg?style=flat&logo=bluetooth)](https://developer.android.com/guide/topics/connectivity/bluetooth/ble-overview)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

A high-performance, native Android application engineered specifically for the **Toyota Yaris MK4 Hybrid (XP210 / TNGA-B platform, 2020+)**. It interfaces directly with the vehicle's High-Voltage Battery ECU and Hybrid Control System via **Bluetooth Low Energy (BLE) OBD-II adapters** to provide active thermal management and real-time hybrid powertrain optimization.

---

## 🌟 Key Features

### 1. 🌀 Automated High-Voltage (HV) Traction Battery Fan Force Control
- **Zero-Click Instant Connection**: Automatically scans and connects to the designated BLE OBD dongle on app launch with cyclic auto-reconnect fallback.
- **Toyota Active Test / IO Control**: Executes continuous Mode 30 / Mode 2F IO Control frames (30 08 06 / 2F 58 03 06) every 1.5 seconds, forcing the rear-seat HV battery cooling fan to **Level 6 (100% Maximum Speed)**.
- **Thermal Throttling Prevention**: Prevents the Lithium-ion battery pack from reaching the critical >36°C-38°C threshold where Toyota's HV ECU heavily derates electrical torque and regenerative braking.
- **Dr. Prius-Style Adaptive Threshold**: Configurable target temperature trigger (default **20°C**) with quick presets (20°C, 25°C, 30°C) and fine-grain slider adjustments.

### 2. 🌡️ Toyota HSD Warm-Up Stage Telemetry & Optimization (S0 ➔ S4)
- **S0 (Cold Engine Init)**: Cold start state detection (ECT < 40°C).
- **S1a (Catalytic Converter Heating)**: Detects ignition timing retardation and EV-inhibition state.
- **S1b (Engine Block & Coolant Warm-up)**: Monitors coolant progression from 40°C to 55°C.
- **S2 (Transition Phase)**: Monitors intermediate stage (55°C - 70°C) allowing idle engine stop.
- **S3 (EV Readiness Transition)**: Tracks final temperature ramp (70°C - 73°C).
- **S4 (Full Hybrid Synergy Drive Efficiency)**: Confirms full Atkinson cycle operation (>73°C) with 100% EV gliding availability.
- **Context-Aware Dynamic Driving & HVAC Advice**:
  - Live alerts recommending HVAC climate adjustments (e.g. keeping cabin heater low for the first 60s to prevent 3x warm-up delays).
  - Ideal 1500–2000 RPM gentle load recommendations to bring engine oil up to operating temperature rapidly.
  - S4 transition trigger tips (releasing accelerator for 5s during coasting).

### 3. 🛡️ Robust Background Execution
- **Persistent Android Foreground Service**: The cooling loop and telemetry polling remain 100% active even with the screen off or while running navigation apps (Google Maps, Waze).
- **Live Status Notification**: Compact notification displaying current battery temperature and active fan speed.

### 4. 📱 Driver-Centric Modern Dark UI
- **OLED High-Contrast Dark Theme**: Deep black (#0F141C) aesthetic with cyan and green accents for optimal visibility in daylight and nighttime driving.
- **Extra-Large Touch Targets**: 56dp+ action buttons and cards for effortless interaction while mounted on vehicle phone holders.
- **Live OBD Diagnostic Terminal**: Embedded streaming log displaying raw CAN frames, PID requests, and ECU responses.

---

## 🔌 Compatible OBD-II BLE Dongles

Supports all standard BLE 4.0+ GATT serial profiles (Nordic UART, Microchip ISSC, TI CC254x, vLinker/Carista FFF0 service):
- **vLinker MC+ / FD+ (BLE)** *(Recommended for high-speed multi-frame CAN handling)*
- **Veepeak OBDCheck BLE / BLE+**
- **Carista OBD BLE**
- **Generic ELM327 BLE 4.0+ adapters**

---

## 🏗️ Architecture Overview

`mermaid
graph TD
    UI[Jetpack Compose UI<br/>DashboardScreen] <-->|StateFlow / Events| Activity[MainActivity]
    Activity <-->|Bound Service| Service[FanControlForegroundService]
    Service --> Controller[ObdController Loop]
    Controller <-->|AT / CAN Frames| Ble[BleManager<br/>GATT Client]
    Ble <-->|BLE 4.0 / 247 MTU| Dongle[OBD-II BLE Adapter]
    Dongle <-->|ISO 15765-4 CAN 500k| ECU[Toyota Yaris MK4<br/>ECU 7E2 / 7E0]
`

---

## 🚀 Getting Started & Build Instructions

### Prerequisites
- **JDK 17** (e.g. OpenJDK 17)
- **Android SDK** (API Level 34, Build-Tools 34.0.0)
- **Gradle 8.7+**

### Building the Debug APK
`ash
# Clone the repository
git clone https://github.com/FrancescoCastaldi/yaris-hv-fan-optimizer.git
cd yaris-hv-fan-optimizer

# Build APK via Gradle
./gradlew assembleDebug
`
The compiled APK will be located at:
pp/build/outputs/apk/debug/app-debug.apk

---

## 📄 License
This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
