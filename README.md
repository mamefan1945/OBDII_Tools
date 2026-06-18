# OBDII Tools

Android application that connects to a vehicle's OBD-II port via an ELM327 Bluetooth adapter. The app collects diagnostic and operational data, displays live vehicle metrics, and provides a user interface for common OBD-II workflows.

## Key capabilities

- Bluetooth connection to ELM327-compatible OBD-II adapters
- Real-time vehicle data display for engine, fuel, and diagnostic state
- DTC scanning and fault code lookup support
- Live data views for available PIDs and sensor values
- Hilt-based dependency injection with Kotlin and Android Jetpack
- Local Room database for persisted vehicle data and session storage
- Support for OBD-II diagnostics across multiple vehicle systems

## Usage

1. Pair your ELM327 Bluetooth adapter with the Android device.
2. Open the app and grant Bluetooth/location permissions when requested.
3. Select the paired ELM327 adapter from the connection list.
4. Use the main dashboard to view live vehicle data and system status.
5. Navigate to diagnostics screens to scan for trouble codes and view detailed reports.
6. Use stored sessions to review previously collected data.

## Screens

- `HomeScreen` — App landing page with connection status, active alerts, VIN summary, trip and vehicle health overview, and quick navigation to diagnostics, session history, and tuning tools.
- `DashboardScreen` — Real-time instrument-style dashboard that displays RPM, speed, coolant temperature, throttle, fuel economy, and connection status for live driving data.
- `BluetoothScreen` — Bluetooth device manager for scanning, pairing, connecting, and disconnecting ELM327-compatible adapters, with permission handling and paired device discovery.
- `DTCScreen` — Fault code scanner that reads stored DTCs from the vehicle, shows code details, and allows users to clear trouble codes when supported.
- `FreezeFrameScreen` — Freeze frame viewer that displays snapshot data captured at the moment a fault code was stored, including RPM, speed, temperatures, and engine load values.
- `ReadinessScreen` — Emissions readiness monitor display that reports which OBD-II readiness tests are complete or incomplete and summarizes MIL/check-engine status.
- `LiveDataScreen` — Full PID explorer that continuously scans available sensors, lists live parameter values, and supports toggling between all PIDs and supported-only results.
- `SessionScreen` — Recorded drive history and session detail viewer, including session selection, stored telemetry, charts, and export/share support for saved data.
- `SettingsScreen` — Preferences panel for display units, alert thresholds, screen behavior, and other user-configurable app options.
- `CustomPidScreen` — Custom PID manager where users can define their own OBD commands, formulas, and display units to track vehicle-specific or manufacturer-specific parameters.
- `CanMonitorScreen` — CAN bus monitor for raw frame capture, filterable message viewing, and optional decoded signal mode for interpreted CAN payloads.
- `CVTScreen` — Continuously Variable Transmission monitor that reads CVT-specific data, lockup state, and transmission metrics when available.
- `DeepScanScreen` — ECU discovery tool that probes standard ECU addresses, identifies responding modules, and reveals supported OBD/UDS data sources.
- `GlossaryScreen` — In-app glossary of automotive diagnostics terms, protocols, hardware, and OBD concepts to help users understand what the app is reading.
- `UdsScreen` — UDS reader for querying manufacturer-specific ECUs and Data Identifiers (DIDs), including ECU selection, scanning, and raw response viewing.

## Build and run

- Open the project in Android Studio.
- Ensure that Android SDK and Kotlin support are installed.
- Sync Gradle and run the `app` module on an Android device or emulator.

## Project structure

- `app/` – Android application module, including source code, resources, and build configuration
- `gradle/` – Gradle version catalog and wrapper settings
- `settings.gradle.kts` / `build.gradle.kts` – project configuration

