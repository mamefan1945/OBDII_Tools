# OBDII Tools for iOS

This directory contains the native SwiftUI iOS companion to the Android application.

## Functional parity goal

The Android app in the repository root is the source of truth for expected behavior. The iOS implementation should match its features wherever iOS and the adapter hardware permit, including:

- ELM327 adapter discovery and connection
- Bluetooth LE support, with adapter compatibility documented explicitly
- Vehicle identification and saved vehicle profiles
- Standard OBD-II live PIDs and unit preferences
- DTCs, readiness monitors, freeze-frame data and diagnostics
- Deep scan, supported/unsupported PID tracking and UDS discovery
- CAN monitoring and supported vehicle-specific diagnostics
- Session capture, reports, export and custom PIDs

Do not assume Android Bluetooth Classic RFCOMM support can be reproduced on iOS. iOS Core Bluetooth supports BLE; Bluetooth Classic serial adapters generally require an Apple-supported accessory/MFi path. Adapter compatibility must be tested and stated clearly.

## Build locally on a Mac

Install Xcode and XcodeGen, then run:

```sh
brew install xcodegen
xcodegen generate --spec ios/project.yml --project ios
xcodebuild -project ios/OBDIITools.xcodeproj -scheme OBDIITools \
  -destination 'platform=iOS Simulator,name=iPhone 16' test
```

## GitHub Actions

The `iOS CI` workflow runs on GitHub-hosted macOS. It generates the Xcode project, builds for the iOS Simulator, runs unit tests, and uploads the simulator app as a workflow artifact.

The simulator artifact is **not an installable iPhone IPA**. Physical-device installation requires Apple code signing and provisioning. No Apple Developer credentials or signing secrets are stored in this repository at this stage.

## Implemented connection and VIN identification

The iOS app discovers compatible BLE UART adapters using Core Bluetooth, initializes the ELM327 with BLE-safe commands, and reads the VIN using SAE Mode 09 PID 02 first with UDS DID F190 as a fallback. A valid 17-character VIN is saved into the existing vehicle profile store. When the ELM327 initializes, the app creates a persistent diagnostic session and records the advertised adapter name, CoreBluetooth peripheral UUID, BLE manufacturer data and service UUIDs when advertised, RSSI, and the responses to `ATI` and `AT@1`. VIN is attached to the active session. Disconnecting closes the session. iOS does not expose the BLE MAC address, so the peripheral UUID is stored as a distinct identifier and the MAC field remains unavailable. Adapter model remains unconfirmed unless a future identification rule can establish it. Bluetooth Classic SPP adapters are not supported through Core Bluetooth; use a BLE-capable adapter or an Apple-supported MFi accessory.

## Architecture direction

Keep platform-specific Bluetooth, persistence and UI implementations native to iOS. Port and test protocol/domain behavior against the Android source and existing protocol tests. Avoid copying Android framework code into the iOS target.
