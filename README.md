# ebyteManager

Android application for configuring and managing Ebyte LoRa modules over USB serial.

## What the app does

- Connects to a USB serial device and requests Android USB permission automatically.
- Reads module registers and displays decoded settings (frequency, NET ID, address, etc.).
- Keeps configuration writes disabled until the device protocol is verified.

## Supported devices

The device selector currently includes:

- E-22-400T22 USB

The E-90 entry was removed because it had no model-specific connection or
configuration implementation. Device writes are disabled until the register
protocol, reserved-bit handling, acknowledgements, and read-back behavior are
verified against the manufacturer's documentation and hardware. Treat current
register reads and decoded values as unverified; do not use this build to
change module settings.

## Displayed configuration fields

The UI displays the following fields when they are present in a register read:

- Module baud rate, air rate, parity, packet size, transmit power
- Channel and derived frequency
- TX mode (fixed-point or transparent)
- WOR role and cycle
- Relay, LBT, packet RSSI, and channel RSSI
- Address and NET ID

Fields whose register mappings are not verified are informational only.
Frequency calculation and model-specific ranges also require confirmation
against the exact module datasheet.

## Requirements

- Android Studio or the Android SDK
- Java 11

## Build

```bash
./gradlew assembleDebug
```

## Test

```bash
./gradlew test
```

## Release validation

This repository has not been validated on USB hardware and is not ready for
store publication based on source review alone. Before enabling writes or
publishing, obtain the supported module manuals, verify all register mappings
and command persistence behavior, and test fragmented responses, timeout,
permission denial, disconnect during I/O, read-back, and USB baud-rate changes
on each supported module. Also verify the current Google Play target SDK
requirement and test Android edge-to-edge behavior for the selected target.
