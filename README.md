# Sole Precision Android

Android app for the Sole Precision backpack prototype. The default interface is
an accessibility-first walking-navigation flow for blind and low-vision users.
Its home screen has only two whole-screen controls: Navigation and Settings.

The phone provides route-level walking instructions through AMap and can prepare
temporary commands over Bluetooth Low Energy (BLE). The planned backpack will
independently handle fast obstacle detection after its real interface and
perception system are integrated.

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

1. Open the `CSUST_AI_VEST_ANDROID` directory in Android Studio.
2. Let Android Studio install the requested SDK and sync Gradle.
3. Copy `local.properties.example` to `local.properties`.
4. Keep the `sdk.dir` line Android Studio creates and replace the example
   `AMAP_API_KEY` value with an Android key from the AMap developer console.
5. Register the application ID `com.csust.soleprecision` and the debug/release
   signing certificate fingerprints in AMap.
6. Connect an arm64 Android device, run the app, accept the prototype map notice
   and grant location/Bluetooth permissions.

The AMap key stays out of Git because `local.properties` is ignored.

## Accessible production flow

- Two-control, high-contrast home screen
- Large controls, scalable text and explicit Compose accessibility semantics
- Inline Android voice recognition with an audible start cue and visible
  recording state
- Mock voice recognition with three deterministic nearby results in simulator mode
- Native AMap input suggestions, POI search and exact POI-ID lookup
- Rich AMap destination details including category, mapped entrance/exit and
  indoor floor when available
- Native full-screen map combining AMap typed suggestions, pan/zoom, point
  selection and reverse geocoding
- POI-ID and mapped-entrance walking routes for more accurate approaches
- One-at-a-time comparison and exact selection of native AMap walking
  alternatives
- Continuous AMap location confidence, weak-GPS and rerouting status
- Swipe-only app menus with directional screen announcements
- Full-screen native AMap point picking and live navigation, with large floating
  controls instead of app swipes on map screens
- Swipe-only results show one place at a time with its address and distance
- Explicit destination confirmation before route calculation
- Current-location walking route preview with a memorization summary: initial
  direction, turns, mapped crossings, traffic lights and level changes
- Optional pre-start walkthrough of every AMap walking step
- Walking guidance derives metres to the next action from matched walking-route
  geometry and includes mapped crossings, vertical transitions, passages,
  bridges, tunnels, building transitions and traffic-light context when AMap
  supplies it
- Active navigation with repeat, route status and pause controls
- Planned architecture keeps future local obstacle detection independent when
  phone guidance is paused
- Recent destination history with an erase control
- Device settings using understandable presets rather than raw timings
- Application settings for recognition language, speech detail and screen introductions
- Concise Android text-to-speech screen introductions are enabled by default
- Screen introductions automatically stay silent while TalkBack touch exploration is active

The approved interaction rules and manual accessibility checklist are in
[`docs/ACCESSIBLE_UX_SPEC.md`](docs/ACCESSIBLE_UX_SPEC.md).
The reviewed SDK/Web Service capability decisions are in
[`docs/AMAP_CAPABILITY_PLAN.md`](docs/AMAP_CAPABILITY_PLAN.md).

The saved hardware architecture, needs analysis, six innovation directions and
two MVP concepts are in
[`docs/SYSTEM_CONCEPT_AND_INNOVATION.md`](docs/SYSTEM_CONCEPT_AND_INNOVATION.md).

## Emulator hardware simulation

Android Emulator automatically starts with **Use simulated system** enabled.
The simulator stands in for the upper controller, USB-to-TTL link,
ESP32-WROOM-32, upper-controller GPS, two speakers and two vibration modules.
It supplies a simulated Changsha position and, after destination confirmation,
creates a clearly labelled software-only demo route. This prevents navigation
screens from waiting for emulator GPS or a valid real-world walking route.
Open **Navigation → Start navigation demo** for the guaranteed offline test path;
it also skips speech recognition and AMap destination search.

Production menus use the approved swipe-only layout: non-clickable
up/left/right/down instruction zones and a draggable full-screen surface. The
surface locks to one axis and translates straight in the swipe direction without
rotation or fading. Destination methods are split across pages so no screen
presents more than four directions. Map screens are the exception and use native
AMap gestures plus floating buttons. Swipe-only interaction is still
experimental and conflicts with TalkBack touch exploration; a validated
TalkBack-compatible alternative remains future work.

Open **Settings → Device settings** to switch between simulated and real
transport. Commands in Device settings or the engineering console then:

- use the normal command encoder;
- show the packet that would be sent;
- report which simulated output was activated; and
- use Android speech to make simulated output tests observable.

This does not simulate camera images, YOLO accuracy, pseudo-binocular depth or
cloud-model latency. Those require recorded datasets or the future upper-level
software.

## Temporary engineering console

The engineering UI is intentionally designed for developers, not blind participants.
Open it from **Settings → App settings → Developer tools**. It provides:

- Explicit AMap privacy gate before SDK initialization
- Independent left, right or both-side vibration commands
- Independent left, right or both-side speaker cue commands
- Vibration intensity, pattern, duration and repeat controls
- Speaker cue, volume and repeat controls
- Manual navigation packet generation without starting AMap
- Walking route calculation from editable coordinates
- AMap emulator navigation that progresses without physical movement
- Live AMap `NaviInfo` conversion into simple direction instructions
- Built-in AMap voice enabled for route announcements
- BLE scan, connect, service discovery and characteristic writes
- Raw 1–20 byte BLE sender for firmware experiments
- Prominent stop-all-outputs command
- Packet preview even when the wearable is offline
- Unit-tested 12-byte temporary command encoders

The UUIDs and command protocol are placeholders until the robotics team provides
the production interface. See
[`docs/TEMPORARY_DEVICE_PROTOCOL.md`](docs/TEMPORARY_DEVICE_PROTOCOL.md) for the
firmware contract.

## Temporary Android transport contract

The current BLE test receiver must advertise:

- Service UUID: `5c10a001-9c1b-4c7f-9c6a-43d42f2d1000`
- Writable command characteristic:
  `5c10a002-9c1b-4c7f-9c6a-43d42f2d1000`

All structured packets are currently 12 bytes and use a shared header, sequence
number and XOR checksum. The packet types are navigation (`0x01`), vibration
(`0x10`), speaker cue (`0x11`) and stop all (`0x12`). Full byte tables and codes
are in the temporary protocol document.

## Safety architecture

Route guidance and obstacle detection must not be treated as equal-priority data.

1. The backpack detects immediate obstacles locally.
2. Local obstacle warnings override map turns.
3. The phone sends only route-level direction cues.
4. If the phone, GPS, AMap or BLE fails, obstacle detection keeps running.
5. Loss of connection must create a clear status signal, not silence that could
   be confused with a safe path.

The test console prepares and displays packets even while the wearable is offline,
so Android and firmware development can proceed independently. The stop-all
command is useful during testing but is not a substitute for a hardware cutoff.

## Stationary AMap test

1. Accept the AMap notice and location permission.
2. Open section **5. AMap navigation without movement**.
3. Leave **Simulated movement** enabled.
4. Enter start and destination coordinates, or use the defaults.
5. Tap **Start stationary AMap simulation**.
6. Watch **Current AMap instruction** and **Last packet bytes** at the top.

AMap calculates a real walking route, then its emulator advances along that route
without requiring GPS movement. Disable simulated movement when testing real
outdoor navigation.

## AMap API choice

The app currently uses the bundled native Android Search, Location and Navigation
SDKs. They provide signed Android-key authentication, POI results, GCJ-02
coordinates, current location and live walking navigation in one client-side
integration.

Do not embed an AMap Web Service key in the APK. This project intentionally has
no backend, so it does not use Web Route Planning 2.0, Web geocoding or Web
trajectory correction. Multi-route walking analysis, POI details, route
geometry and live navigation stay in the native Android SDKs. The native
coordinate converter is used only when falling back from AMap Location to an
Android GPS fix, converting WGS-84 GPS coordinates to AMap coordinates without a
Web Service call.

## Before testing with blind participants

- Replace the prototype notice with a complete Chinese privacy policy and the
  disclosures required by AMap.
- Add a way to withdraw consent and erase stored settings.
- Test every screen with TalkBack and large font/display scaling.
- Add Chinese strings and avoid relying on English text.
- Replace the prototype recognition-language selector with complete localized
  UI resources before Chinese participant testing.
- Validate turn cues and vibration patterns with orientation-and-mobility
  specialists; do not imply that the system replaces a cane or guide dog.
- Add BLE reconnect, acknowledgement/heartbeat and explicit fault states.
- Replace the temporary UUIDs, packet definitions and canned speaker cue IDs with
  the robotics team's interface.
- Treat AMap walking instructions as route guidance, not proof that a crossing
  or path is physically safe.

## Useful commands

After Android Studio has installed a JDK and SDK:

```bash
./gradlew test
./gradlew lint
./gradlew assembleDebug
```
