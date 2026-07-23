# Sole Precision Android

Starter Android app for the backpack navigation prototype. The phone provides
route-level walking instructions through AMap. The ESP32 backpack receives compact
direction packets over Bluetooth Low Energy (BLE) and independently handles
fast obstacle detection.

## Recommended development setup

Use **Android Studio** as the main development environment. It includes the Android
SDK manager, Gradle sync, emulator/device tools, Logcat and Compose previews.
VS Code is fine as a secondary text editor, but it does not replace those Android
tools.

Required locally:

- Android Studio Quail 2 (2026.1.2) or another version supporting AGP 9.3
- JDK 17 (the Android Studio bundled JDK is easiest)
- Android SDK Platform 36 and Build Tools 36
- A physical 64-bit Android phone is preferred for AMap and BLE testing

AMap Navigation SDK 11.2.000 is arm64-only, so this project intentionally builds
only `arm64-v8a`. An x86 emulator will not run the AMap native library.

## Open and run

1. Open this `sole-precision-android` directory in Android Studio.
2. Let Android Studio install the requested SDK and sync Gradle.
3. Copy `local.properties.example` to `local.properties`.
4. Keep the `sdk.dir` line Android Studio creates and replace the example
   `AMAP_API_KEY` value with an Android key from the AMap developer console.
5. Register the application ID `com.csust.soleprecision` and the debug/release
   signing certificate fingerprints in AMap.
6. Connect an arm64 Android device, run the app, accept the prototype map notice
   and grant location/Bluetooth permissions.

The AMap key stays out of Git because `local.properties` is ignored.

## What is implemented

- High-contrast, large-control Compose interface designed to work with TalkBack
- Explicit AMap privacy gate before SDK initialization
- Walking route calculation from start and destination coordinates
- Live AMap `NaviInfo` conversion into simple direction instructions
- Built-in AMap voice enabled for route announcements
- BLE scan, connect, service discovery and characteristic writes
- Hardware test buttons that work without a route or AMap key
- Stable 12-byte BLE packet encoder with unit tests

This is a base, not a finished navigation product. The coordinate fields are
temporary; destination/POI search and current-location start selection are the
next app features.

## Phone ↔ backpack contract

The ESP32 must advertise:

- Service UUID: `5c10a001-9c1b-4c7f-9c6a-43d42f2d1000`
- Writable navigation characteristic:
  `5c10a002-9c1b-4c7f-9c6a-43d42f2d1000`

Navigation packet, 12 bytes, little-endian:

| Byte | Meaning |
|---:|---|
| 0 | Magic value `0x53` (`S`) |
| 1 | Protocol version, currently `1` |
| 2 | Packet type, `1` = navigation |
| 3–4 | Sequence number |
| 5 | Maneuver code |
| 6–7 | Distance in metres |
| 8–9 | Time-to-live in 100 ms units |
| 10 | Source flags: `1` AMap, `2` demo |
| 11 | XOR checksum of bytes 0–10 |

Maneuver codes are defined in
`app/src/main/java/com/csust/soleprecision/navigation/Maneuver.kt`.
The ESP32 should discard packets with a bad magic value, unsupported version,
failed checksum or expired time-to-live.

## Safety architecture

Route guidance and obstacle detection must not be treated as equal-priority data.

1. The backpack detects immediate obstacles locally.
2. Local obstacle warnings override map turns.
3. The phone sends only route-level direction cues.
4. If the phone, GPS, AMap or BLE fails, obstacle detection keeps running.
5. Loss of connection must create a clear status signal, not silence that could
   be confused with a safe path.

The current app prepares and displays a packet even while the wearable is offline,
which makes software and firmware development possible in parallel.

## Before testing with blind participants

- Replace the prototype notice with a complete Chinese privacy policy and the
  disclosures required by AMap.
- Add a way to withdraw consent and erase stored settings.
- Test every screen with TalkBack and large font/display scaling.
- Add Chinese strings and avoid relying on English text.
- Validate turn cues and vibration patterns with orientation-and-mobility
  specialists; do not imply that the system replaces a cane or guide dog.
- Add BLE reconnect, acknowledgement/heartbeat and explicit fault states.
- Treat AMap walking instructions as route guidance, not proof that a crossing
  or path is physically safe.

## Useful commands

After Android Studio has installed a JDK and SDK:

```bash
./gradlew test
./gradlew assembleDebug
```
