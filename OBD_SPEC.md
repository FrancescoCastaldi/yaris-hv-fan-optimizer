# Toyota Yaris MK4 (TNGA-B) OBD-II Protocol Specification

## 1. CAN Bus Parameters
- **Protocol**: ISO 15765-4 (CAN 11-bit ID, 500 kbaud) -> ELM327 `AT SP 6`
- **High-Voltage Battery ECU Request Header**: `7E2` (`AT SH 7E2`)
- **High-Voltage Battery ECU Response Filter**: `7EA` (`AT CRA 7EA`)
- **Engine / Hybrid Main ECU Request Header**: `7E0` (`AT SH 7E0`)
- **Engine / Hybrid Main ECU Response Filter**: `7E8` (`AT CRA 7E8`)

---

## 2. High-Voltage (HV) Battery Diagnostics
### 2.1 Temperature & Fan Query
- **Service & PID**: `22 28 C1` (Toyota TNGA XP210 UDS Mode 22) or fallback `21 61` (KWP Mode 21)
- **Response Format**: `62 28 C1 [T1] [T2] [T3] [T4] [Intake] [FanLevel] ...`
- **Decoding Formulas**:
  - Module 1 Temp: `T1 - 40` (°C)
  - Module 2 Temp: `T2 - 40` (°C)
  - Module 3 Temp: `T3 - 40` (°C)
  - Module 4 Temp: `T4 - 40` (°C)
  - Intake Air Temp: `Intake - 40` (°C)
  - Fan Speed Level: `FanLevel` (Integer 0 to 6)

### 2.2 Active Test IO Control (Fan Override)
- **Primary Command**: `30 08 06` (Mode 30 IO Control, Parameter `08` Fan Speed, Value `06` Maximum 100%)
- **Alternative Command**: `2F 58 03 06` (Mode 2F IO Control Short-Term Adjustment)
- **Keep-Alive**: `3E 00` (Tester Present)

---

## 3. Engine Warm-Up Telemetry (Mode 01 Standard OBD)
- **Engine Coolant Temperature (ECT)**: `0105` -> Formula: `A - 40` (°C)
- **Intake Air Temperature (IAT)**: `010F` -> Formula: `A - 40` (°C)
- **Engine Speed (RPM)**: `010C` -> Formula: `((A * 256) + B) / 4` (RPM)
- **Catalyst Temperature**: `013C` -> Formula: `((A * 256) + B) / 10 - 40` (°C)

---

## 4. Toyota Hybrid Synergy Drive (HSD) Warm-Up Stages
| Stage | Condition | Description |
|-------|-----------|-------------|
| **S0** | ECT < 40°C, RPM = 0 | Cold Engine Standby |
| **S1a** | ECT < 40°C, RPM > 0 | Catalyst Heating (Ignition Retarded, EV Inhibited) |
| **S1b** | 40°C <= ECT < 55°C | Engine Block & Coolant Warm-up |
| **S2** | 55°C <= ECT < 70°C | Intermediate Stage (Engine Stop Allowed at Idle) |
| **S3** | 70°C <= ECT < 73°C | EV Readiness Verification Transition |
| **S4** | ECT >= 73°C | Full Hybrid Efficiency & Atkinson Gliding Unlocked |
