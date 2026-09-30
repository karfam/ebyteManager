# ebyteManager

An Android app for reading and configuring Ebyte LoRa modules over USB serial. The application ID is `gr.enorasys.loramanager`.

## Current scope and limitations

- The app recognizes the E-22-400T22U product ID. This is not a claim of verified hardware support; protocol mappings and hardware behavior still need manual and device validation.
- DTU E-90 is not supported by this implementation.
- Air-rate, WOR role/cycle, and channel-RSSI controls are read-only/disabled because their model-specific mappings have not been verified.
- Writes require a successful read of a recognized module, show a preview and confirmation, preserve unmodified register bits from that read, and compare the resulting register data with a follow-up read. The write acknowledgement and whether C2 writes survive a power cycle remain unverified.
- Use a USB OTG-capable Android device and connect only hardware you are prepared to configure. See [release readiness](RELEASE_READINESS.md) before testing or distribution.

## Build requirements

- JDK 17 to run the Android Gradle Plugin; app Java source/target compatibility remains Java 11.
- Android SDK Platform 36 and Build Tools 35.0.0.

```bash
./gradlew assembleDebug
./gradlew test
./gradlew lint
```

## Publication

This is an Android/Google Play project, not an Apple App Store app. It is not store-ready or store-approved. See [RELEASE_READINESS.md](RELEASE_READINESS.md) for remaining validation and owner tasks.
