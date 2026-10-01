# Release readiness audit

**Status: not release-ready, not store-approved, and not validated with physical LoRa hardware.** This repository is an Android application with application ID `gr.enorasys.loramanager`. It is being prepared for Google Play; an Apple App Store release would require a separate iOS app.

## Changes in the branch

- Register responses are accumulated to their requested length under a deadline and must match the complete command/address/length header. Truncated, timed-out, and mismatched responses do not reach the parsers.
- Register parsers check exact frame sizes and hex content. The UI uses the decoded transmission mode and clears unsupported spinner values rather than retaining a stale selection.
- Configuration writes are disabled in the UI and rejected by the activity until exact-model command/register mappings and behavior have been verified. The retained write implementation is not enabled for use.
- The E-90 claim was removed. WOR role/cycle and channel-RSSI controls are disabled pending a verified mapping. USB serial devices and ports can be selected when there are multiple choices; permission is checked against the selected device. User-triggered USB open/read/write/close operations use the serial executor, and an explicit Disconnect action is available.
- Air-rate values are shown as raw register codes and are not editable; the previous decoder/spinner mapping could not be verified against the exact model manual.
- The USB indicator follows actual port state. The app now accounts for system-bar and display-cutout insets when targeting current Android versions.

## Protocol and hardware limitations

No model-specific Ebyte manual or physical module was available for this review. Existing C1/C2 command bytes, register-field mappings, channel-to-frequency formula, supported rate values, write acknowledgement, and module UART baud/parity transition behavior therefore remain **unverified**. Do not interpret model-ID recognition as verified product support.

The C2 temporary-versus-permanent behavior is unknown, as are write acknowledgement and persistence. The app currently disables writes rather than relying on speculative protocol semantics. Before enabling writes, compare every command/register mapping to the exact official manual and validate on hardware.

USB permission denial and selected-device matching are handled. Physical testing is still required for detach during blocking I/O and activity destruction while I/O is active; activity destruction still interrupts the executor and closes the port directly, so cancellation/close ordering is not proven race-free. USB permission, device selection, serial-port setup, and dynamic receiver behavior must be checked on supported Android versions.

## Platform/build status

As checked on September 30, 2026, Google Play requires new apps and updates to target API 36 (Android 16) beginning August 31, 2026. The app is configured for `compileSdk 36` and `targetSdk 36`; the application ID and minSdk 30 are unchanged. AGP 8.10.0, Gradle 8.11.1, and JDK 17 are paired for API 36 according to the [AGP 8.10 release notes](https://developer.android.com/build/releases/agp-8-10-0-release-notes). Edge-to-edge insets are applied in the main layout.

Sources:

- [Google Play target API requirements](https://support.google.com/googleplay/android-developer/answer/11926878)
- [AGP 8.10.0 compatibility notes](https://developer.android.com/build/releases/agp-8-10-0-release-notes)
- [Android edge-to-edge guidance](https://developer.android.com/develop/ui/views/layout/edge-to-edge)

Local validation was attempted with `bash ./gradlew test` before the toolchain update and with the updated wrapper afterward. Gradle could not resolve AGP 8.7.3 or 8.10.0 from the configured plugin repositories in this environment. The updated AGP/Gradle/SDK configuration and API-36 edge-to-edge behavior therefore still require a successful build and emulator/device verification. No successful build, lint, release bundle, or USB test is claimed here.

## Observed application data flow

The app requests Android USB permission, reads module register/product bytes over the selected USB serial port, and displays decoded settings locally. Configuration writes are currently disabled. No app network calls, remote services, analytics, or user-file/profile storage were found in the inspected source; the manifest also declares no Internet or broad storage permission. Serial command headers and operational errors are logged, but raw register-response bytes are not logged.

The manifest enables Android backup, and the checked-in backup/extraction rule files contain only the template defaults. No profile/configuration files are currently written by the app. The owner should reassess backup rules and describe the USB/local data flow accurately before adding persistent profiles or publishing a privacy policy.

## Required validation before release

Run with network access, JDK 17, Android SDK Platform 36, and Build Tools 36.0.0:

```bash
./gradlew clean test lint assembleDebug bundleRelease
```

Then validate on actual devices/modules:

- USB permission grant and denial; explicit device selection with multiple devices.
- Repeated connect/disconnect, unplug during read/write, activity destruction during I/O, and rotation/recreation.
- Fragmented replies, short/wrong/stale headers, timeout, and malformed product/register data.
- Before enabling writes, confirm exact command/write semantics from the manufacturer manual; then validate readback and persistence after power cycle.
- Changes to the module's UART baud and parity, including how the host should reconnect.
- Small screens, keyboard/insets, dark theme, and Android API 35/36 behavior.

Automated protocol tests use a fake byte reader and do not substitute for USB hardware testing.

## Deferred work and store-owner tasks

- Named local configuration profiles and bounded SAF import/export.
- A bounded diagnostics screen/export/share flow and user-facing USB troubleshooting/help.
- Broader accessibility/resource-string cleanup and release minification evaluation.
- A least-privilege build/test CI workflow; the repository currently has no workflow configured.
- Confirm the app's actual data flows and dependencies before writing a hosted privacy policy or completing Play Data safety declarations. The inspected manifest does not request Internet or broad storage permissions; this is not a substitute for the store owner's legal/privacy review.
- Create and protect the upload key outside source control; configure signing securely, increment version code/name, and build/test a signed bundle outside this agent session.
- Provide the actual support contact and hosted privacy-policy URL, store listing text/screenshots, content rating, data-safety declarations, and any required hardware/accessory disclosures.

No signing secrets, privacy-policy identity/contact details, screenshots, or store declarations were added. Store submission and approval remain the owner's responsibility.
