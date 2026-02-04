# ebyteManager

Android application for configuring and managing Ebyte LoRa modules over USB serial.

## What the app does

- Connects to a USB serial device and requests Android USB permission automatically.
- Reads module registers and displays decoded settings (frequency, NET ID, address, etc.).
- Writes configuration changes back to the device.

## Supported devices

The device selector currently includes:

- E-22-400T22 USB
- DTU E-90

## Supported configuration options

The UI exposes the following settings for read/write operations:

- Module baud rate, air rate, parity, packet size, transmit power
- Channel and derived frequency
- TX mode (fixed-point or transparent)
- WOR role and cycle
- Relay, LBT, packet RSSI, and channel RSSI
- Address and NET ID

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
