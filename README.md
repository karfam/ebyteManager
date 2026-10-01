# ebyteManager

An Android app for reading Ebyte LoRa module registers over USB serial. The application ID is `gr.enorasys.loramanager`.

## Current scope and limitations

- The app recognizes the E-22-400T22U product ID, but this does not confirm protocol or hardware support.
- DTU E-90 is not supported by this implementation.
- Configuration writes are disabled until register mappings, reserved-bit handling, acknowledgement, and read-back behavior are verified against the manufacturer's documentation and hardware.
- Air-rate, WOR role/cycle, and channel-RSSI controls are read-only or disabled because their model-specific mappings have not been verified. Frequency calculations and model-specific ranges also require confirmation.
- Register reads and displayed decoded values remain unverified. Do not use this build to change module settings.
- Use a USB OTG-capable Android device. See [release readiness](RELEASE_READINESS.md) for validation and owner tasks.

## Displayed configuration fields

The UI displays fields available from register reads, including module baud rate, air rate, parity, packet size, transmit power, channel/frequency estimate, TX mode, WOR fields, relay/LBT/RSSI fields, address, and NET ID. Fields with unverified register mappings are informational only.

## Build requirements

- JDK 17 to run the Android Gradle Plugin; app Java source/target compatibility remains Java 11.
- Android SDK Platform 36 and Build Tools 36.0.0.

```bash
./gradlew assembleDebug
./gradlew test
./gradlew lint
```

## Release validation

This repository has not been validated on USB hardware and is not ready for store publication based on source review alone. Obtain the exact supported module manuals, verify all register mappings and command behavior, and test fragmented responses, timeouts, permission denial, disconnect during I/O, and Android edge-to-edge behavior before distribution.

This is an Android/Google Play project, not an Apple App Store app. See [RELEASE_READINESS.md](RELEASE_READINESS.md); no store readiness or approval is claimed.
