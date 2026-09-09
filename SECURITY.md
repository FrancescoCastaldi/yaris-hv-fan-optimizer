# Security Policy & Vulnerability Reporting

## Supported Versions

The following versions of **Yaris HV Battery Cooling, GR Cockpit & ECU Coding Suite** currently receive security and stability updates:

| Version | Supported          |
| ------- | ------------------ |
| 3.0.x   | :white_check_mark: |
| < 3.0.0 | :x:                |

## Reporting a Vulnerability

We prioritize the security of hardware interactions, vehicle CAN bus integrity, and user data. If you discover a security vulnerability or potential vehicle communication hazard, please report it responsibly.

### How to Report

1. **Do not create public GitHub issues or discussions** for suspected vulnerabilities.
2. Submit your report directly via [GitHub Security Advisories](https://github.com/FrancescoCastaldi/yaris-hv-fan-optimizer/security/advisories/new) or send an email to:
   - **Email**: `info@francescocastaldi.it`
3. Include detailed information in your report:
   - Specific component, OBD command, or service affected.
   - Exact steps or minimal script to reproduce the issue.
   - OBD-II dongle hardware and vehicle model/firmware version used.
   - Any potential impact on vehicle systems or Android host permissions.

### Response Timeline

- **Acknowledgment**: Within 48 hours of receipt.
- **Assessment & Triage**: Within 5 business days.
- **Fix & Disclosure**: Coordinated release with a security patch and release notes.

## Security Architecture

- **Bluetooth Isolation**: Foreground service controls active BLE/SPP channels with scoped Android runtime permissions (`BLUETOOTH_CONNECT`, `BLUETOOTH_SCAN`).
- **CAN Bus Safety Guardrails**: Rate-limited OBD commands, single-flight scheduler, read-only telemetry loop, and strict handshake safeguards prevent bus flooding or unintentional ECU writes.
- **Build Signing**: Release APKs are signed with multi-scheme RSA 2048-bit digital certificates (V1, V2, V3, V4).
