# Sole Precision Android

Android app for the Sole Precision backpack prototype. The default interface is
an accessibility-first walking-navigation flow for blind and low-vision users.
Its home screen has only two whole-screen controls: Navigation and Settings.

The phone provides route-level walking instructions through AMap. The backpack
receives compact commands over Bluetooth Low Energy (BLE) and independently
handles fast obstacle detection.

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

## Accessible production flow

- Two-control, high-contrast home screen
- Large controls, scalable text and explicit Compose accessibility semantics
- Inline Android voice recognition with an audible start cue and visible
  recording state
- Mock voice recognition with three deterministic nearby results in simulator mode
- Native AMap input suggestions, POI search and exact POI-ID lookup
- Native full-screen map combining AMap typed suggestions, pan/zoom, point
  selection and reverse geocoding
- POI-ID walking routes for more accurate destination entrances
- Continuous AMap location confidence, weak-GPS and rerouting status
- Swipe-only app menus with directional screen announcements
- Full-screen native AMap point picking and live navigation, with large floating
  controls instead of app swipes on map screens
- Swipe-only results show one place at a time with its address and distance
- Explicit destination confirmation before route calculation
- Current-location walking route preview with distance and duration
- Optional pre-start walkthrough of every AMap walking step
- Walking guidance includes metres to the next action and mapped
  crosswalk/stairs/elevator and traffic-light context when AMap supplies it
- Active navigation with repeat, route status and pause controls
- Pausing phone guidance does not stop local obstacle detection
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

To compare interaction designs, open **Settings → App settings → Navigation
control layout**. **Large buttons** remains the default. **Swipe-only experiment**
uses non-clickable up/left/right/down instruction zones and a draggable,
full-screen surface. The surface locks to one axis and translates straight in
the swipe direction without rotation or fading. Destination methods are split
across pages so no screen presents more than four directions. This experimental mode is not
compatible with TalkBack touch exploration; use the default layout for TalkBack.

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

Do not embed an AMap Web Service key in the APK. Route Planning 2.0 may be useful
later behind a project-owned backend for alternative-route analysis. The native
coordinate converter is used only when falling back from AMap Location to an
Android GPS fix, converting WGS-84 GPS coordinates to AMap coordinates without a
Web Service call. Web geocoding and trajectory correction are not currently
required because POI search already returns coordinates and the product does not
upload recorded tracks.

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

# CSUST Sole Precision Project — Complete Handoff

Last consolidated: 24 July 2026
Primary product: **Sole Precision**, an AI-assisted backpack/vest for blind and
low-vision pedestrians
Current software: Android walking-navigation prototype using AMap/Gaode

This is the canonical handoff for the project. It combines the project
conversation, current Android source, saved design documents, research artifacts,
hardware assumptions, safety constraints, build state, and open work.

## Read this first

1. The **authoritative Android project** is:
   `/Users/balazsmolnar/StudioProjects/CSUST_AI_VEST_ANDROID`
2. `/Users/balazsmolnar/Documents/CSUST/sole-precision-android` is an **older,
   stale copy**. Do not implement new work there.
3. Most of the current app is in an **uncommitted working tree**. The GitHub
   branch does not yet contain the latest navigation, AMap, accessibility, mock
   hardware, or protocol work.
4. Never commit the AMap key. It belongs only in the ignored
   `local.properties` file.
5. This is an assistive prototype, not a certified mobility aid. It supplements
   a cane, guide dog, and orientation-and-mobility skills; it does not replace
   them.
6. The backpack's local obstacle detection must always outrank phone/AMap route
   guidance.

## One-paragraph project summary

Sole Precision is a backpack/vest concept intended to give a blind or low-vision
user concise, directional travel assistance. Two front strap cameras and an
upper-level computer are intended to detect and track obstacles or selected
objects. GPS, AMap, and inertial data provide outdoor route context. A lower
ESP32 controller drives left/right vibration modules and speakers. The Android
app currently provides the accessible destination-selection and walking-route
interface, full-screen native AMap location picking and navigation, route
walkthroughs, voice input, mock hardware, and a temporary BLE engineering
protocol. Local camera obstacle detection, target locking, sensor fusion,
cloud-model interaction, and the final robotics interface are still future
hardware/software work.

---

## 1. Project locations and source of truth

| Location | Meaning | Status |
| --- | --- | --- |
| `/Users/balazsmolnar/StudioProjects/CSUST_AI_VEST_ANDROID` | Authoritative Android repo | Active; use this |
| `https://github.com/molnrb/CSUST_AI_VEST_ANDROID` | GitHub remote | Only contains the first two commits, not the latest working tree |
| `/Users/balazsmolnar/Documents/CSUST/sole-precision-android` | Earlier Android copy | Stale; preserve only as historical material |
| `/Users/balazsmolnar/Documents/CSUST/PROJECT_CONTEXT.md` | Earlier research/project consolidation | Useful background, but its final product shortlist predates Sole Precision |
| `/Users/balazsmolnar/Documents/CSUST/outputs` | Research presentations, PDFs, and customer-journey image | Supporting artifacts |
| `/Users/balazsmolnar/Downloads/00021517-2.pdf` | CSUST innovation-camp plan and example materials list | Context only; not the final hardware BOM |

The CSUST root itself is a Git repository on `master` with no commits. Its files
are currently untracked. The active Android repository is a separate Git
repository.

### Current active-repo Git state

- Branch: `main`
- Remote: `origin`
- Remote URL: `https://github.com/molnrb/CSUST_AI_VEST_ANDROID.git`
- `HEAD` and `origin/main`: `1de7821 Fix Android build with AGP 9.3`
- Previous commit: `bed8952 Initial Sole Precision Android app`
- The latest implementation is not committed or pushed.

Modified tracked files at handoff:

- `README.md`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/csust/soleprecision/MainActivity.kt`
- `app/src/main/java/com/csust/soleprecision/bluetooth/BleWearableTransport.kt`
- `app/src/main/java/com/csust/soleprecision/bluetooth/WearableTransport.kt`
- `app/src/main/java/com/csust/soleprecision/navigation/AmapNavigationController.kt`
- `app/src/main/java/com/csust/soleprecision/ui/SolePrecisionApp.kt`

Important untracked additions include:

- accessibility screen narration
- simulated wearable transport
- device-test command and packet code
- AMap input tips, location, search, reverse-geocode, and model classes
- destination history
- user settings
- swipe navigation and the production app flow
- unit tests
- design/protocol documentation
- Gradle daemon-JVM configuration

Do not clean, reset, or replace this working tree. Review it, preserve it, and
commit it when the current milestone is ready.

---

## 2. How the concept evolved

The project began with a broad problem-driven or “bug-driven” design process:
identify real difficulties experienced by blind and low-vision people, group the
causes, and then propose functions rather than starting from a predetermined
device.

Six concern areas were identified:

1. **Static spatial distribution** — understanding room layouts, shelf
   arrangements, entrances, and object relationships.
2. **Dynamic safety** — avoiding moving people and immediate obstacles.
3. **Accurate item categorization** — distinguishing products and household
   objects, reading attributes, meal preparation, and code scanning.
4. **Event judgment** — judging whether cooking is complete or cleaning is
   thorough.
5. **External target identification** — ground hazards, road condition,
   obstructions, entrances, and target vehicles.
6. **Current navigation deficiencies** — route planning, navigation, turn
   angles, and precise target location.

Root causes were analyzed across six interfaces: people, objects, information,
mechanisms, time, and location. Twelve proposed solutions were rated by
importance, feasibility, and innovation.

### The two broad solution families

#### A. Sole Precision — selected

Primary goals:

- dynamic safety
- precise object classification
- precise target location
- automatically identify and locate the requested target
- provide the information actually needed, not continuous descriptions of
  everything
- avoid obstacles precisely

#### B. Comprehensive Observation — deferred

Primary goals:

- help the user build a nonvisual mental map and sense of direction
- record and track object locations
- communicate static layouts
- filter information
- provide a broad or “god's-eye” spatial overview

The team explicitly chose **Sole Precision** as the first product category.
Comprehensive spatial observation may become a later layer, but it must not
delay the precision and obstacle-assistance MVP.

### Generic solution ingredients retained

- computer vision
- a wearable form
- vibration
- sound or voice alerts
- physical interaction
- multiple sensors for precision
- accurate target setting
- automatically created markers

These were integrated into a backpack/vest concept.

---

## 3. Intended product and hardware architecture

### Physical form

- Backpack or vest.
- Two single-lens cameras mounted on the front shoulder straps.
- Left and right vibration modules for directional cues.
- Left and right speaker modules near the shoulders.
- GPS receiver.
- A small upper-level computer mounted in a front compartment.

### Upper-level machine

The planned upper controller connects:

- two forward-facing monocular cameras
- one GPS receiver
- navigation, computer-vision, target-locking, and semantic software
- the lower controller through USB-to-TTL

Planned responsibilities:

1. Combine AMap/Gaode navigation, GPS, and inertial context for outdoor travel.
2. Run the planned **YOLO26** detector, as named in the project brief, over the
   two camera feeds for indoor objects and obstacles.
3. Use pseudo-binocular geometry to estimate relative position and focus on a
   user-selected central target.
4. Maintain a target lock so the system does not switch between visually similar
   nearby objects.
5. Filter observations so only actionable information is delivered.
6. Use a cloud large model for non-time-critical command understanding,
   interaction, and reading/explaining locked-object information.
7. Translate decisions into compact output commands.

The pseudo-binocular method requires real calibration and validation. Two
monocular cameras do not automatically provide reliable depth.

### Lower-level machine

Planned controller: `ESP32-WROOM-32`.

Connections:

- two speaker modules
- two vibration modules
- IMU/gyroscope
- USB-to-TTL connection from the upper controller

Responsibilities:

- execute left, right, or bilateral audio/vibration cues
- obey the priority assigned by the upper controller
- stop output safely
- never let an ordinary route cue mask an immediate obstacle cue

### Android application's boundary

The Android app is the accessible user interface and current AMap prototype. It
handles:

- AMap consent and permissions
- destination input and search
- destination confirmation
- walking-route planning
- route preview and step walkthrough
- active map navigation
- accessible settings and screen narration
- mock hardware and engineering tests
- temporary wearable command transmission

The final Android-to-upper-controller transport is not decided. The current BLE
implementation is a test boundary, not proof that Android will connect directly
to the ESP32.

### Intended end-to-end information flow

1. User gives a destination, target, or semantic command.
2. AMap/GPS/IMU provide route and motion context.
3. Cameras and the local model produce object/obstacle candidates.
4. Filtering removes irrelevant observations.
5. Pseudo-binocular targeting and lock-on logic select the intended object.
6. Local logic handles time-critical obstacle warnings.
7. Cloud semantic services handle non-time-critical interpretation.
8. The upper controller sends prioritized commands over USB-to-TTL.
9. The ESP32 activates the appropriate speaker and vibration outputs.

Cloud availability must never be required for immediate obstacle avoidance.

---

## 4. Safety architecture and non-negotiable language

Priority order:

1. immediate local obstacle/stop warning
2. hardware, connection, GPS, or route failure
3. turn instruction
4. route progress
5. optional context and landmarks

Rules:

- Immediate obstacle detection runs locally on the backpack.
- Obstacle cues interrupt map speech and haptics.
- AMap supplies route context, not obstacle detection.
- A mapped crosswalk or traffic light is not permission to cross.
- The app must say that the real crossing and traffic state must be verified.
- If Android, BLE, GPS, AMap, the network, or the cloud fails, local obstacle
  detection must continue.
- Silence must never be interpreted as “the path is safe.”
- Camera confidence is not physical safety.
- GPS is not reliable indoor positioning.
- Feedback must be concise enough that it does not mask environmental sound.
- A physical emergency cutoff is still required; the software stop command is
  not sufficient.
- Validate cue vocabulary, timing, speech density, and vibration mappings with
  blind/low-vision participants and orientation-and-mobility specialists.

Recommended future walking cue cadence:

1. early warning
2. preparation cue near the action
3. action-now cue

The future camera-to-app/device event should include at least object class,
range, height, confidence, and left/center/right or clock-face direction.

---

## 5. Current Android application

### Technical baseline

- Package/namespace: `com.csust.soleprecision`
- Version: `0.1.0`, version code `1`
- Minimum Android: API 26
- Compile and target SDK: API 36
- Java: 17
- UI: Jetpack Compose + Material 3
- Android Gradle Plugin: 9.3
- Architecture ABI: `arm64-v8a` only
- AMap bundle:
  `com.amap.api:navi-3dmap-location-search:11.2.000_3dmap11.2.000_loc11.2.000_sea9.8.0`

AMap Navigation 11.2 native libraries are ARM64-only in this project, so x86
emulators cannot run the app.

### Permissions and platform integration

The manifest includes:

- internet and network/Wi-Fi state
- fine and coarse location
- microphone recording
- wake lock
- legacy and modern Bluetooth/BLE permissions
- optional GPS and BLE hardware features
- Android speech-recognition query
- AMap location service

The AMap key is injected through a manifest placeholder from ignored
`local.properties`.

### Source structure

Main orchestration:

- `MainActivity.kt` — permissions, AMap services, voice recognition, mock/real
  transport switching, route state, and Compose callbacks

Accessibility:

- `accessibility/ScreenNarrator.kt`

Transport:

- `bluetooth/WearableTransport.kt`
- `bluetooth/BleWearableTransport.kt`
- `bluetooth/MockWearableTransport.kt`

Temporary device protocol:

- `device/DeviceTestCommand.kt`
- `device/DeviceTestPacketEncoder.kt`
- `device/HexPacketCodec.kt`

AMap/navigation:

- `navigation/AmapInputTipsController.kt`
- `navigation/AmapLocationController.kt`
- `navigation/AmapManeuverMapper.kt`
- `navigation/AmapNavigationController.kt`
- `navigation/AmapPlaceSearchController.kt`
- `navigation/AmapReverseGeocodeController.kt`
- `navigation/DestinationHistoryStore.kt`
- `navigation/Maneuver.kt`
- `navigation/NavigationInstruction.kt`
- `navigation/NavigationPacketEncoder.kt`
- `navigation/PlaceCandidate.kt`
- `navigation/RouteSummary.kt`
- `navigation/UserLocation.kt`

Settings and UI:

- `settings/UserPreferences.kt`
- `ui/GestureNavigation.kt`
- `ui/ProductionApp.kt`
- `ui/SolePrecisionApp.kt`

### Implemented application state

The app currently implements:

- AMap privacy gate before SDK initialization
- runtime location, microphone, and BLE permissions
- continuous high-accuracy location
- location confidence based on accuracy
- AMap location with converted Android GPS fallback
- inline Android `SpeechRecognizer`
- recognition language selection
- audible microphone start tone
- location-biased AMap input suggestions
- keyword POI search
- exact POI-ID lookup
- one-at-a-time accessible search results
- full-screen native AMap point selection
- reverse geocoding for manually pinned map points
- POI-ID walking-route calculation
- route distance and duration
- ordered walking steps for pre-start review
- live AMap navigation events
- native AMap route voice
- weak-GPS, GPS-off, and off-route/recalculation status
- destination history
- mock hardware, location, voice results, and route
- real temporary BLE transport
- engineering controls for vibration, speakers, navigation packets, raw packets,
  and stop-all

### Current preferences

Saved with Android `SharedPreferences`:

- guidance mode: vibration + speech, vibration only, or speech only
- vibration strength
- speaker volume
- speech detail: concise, standard, or detailed
- language: English, Simplified Chinese, or Traditional Chinese
- extra screen introductions

The language selection currently changes recognition/TTS language behavior, but
the visible application copy is still mostly hard-coded English. Complete
localization is not implemented.

---

## 6. Current UX decisions and screen flow

### Interaction model

The current production code uses **swipe-only navigation menus**. The previous
large-button navigation mode was discarded.

Rules:

- whole screen behaves as one card/surface
- card moves only on one straight horizontal or vertical axis
- no rotation, Tinder-style tilt, or fade
- an unavailable direction does not move
- down always means Back
- right means confirm, continue, or preferred option
- left means decline, previous, or alternative
- up is reserved for a meaningful fourth action
- no more than four directional choices on one screen
- large arrows and labels
- black, white, yellow, and red high-contrast palette
- no divider lines are required

Settings remain conventional buttons and switches because helpers may configure
them.

### Important accessibility conflict

The saved accessibility specification correctly notes that a gesture-only
interface is not compatible with normal TalkBack touch exploration and violates
the rule against hidden/drag-only interaction. The user deliberately chose the
swipe-only experiment as the current app direction, but this remains a major
usability risk to test rather than an established best practice.

Do not claim the swipe-only system is proven accessible. It needs participant
testing, and a TalkBack-compatible alternative may still be required.

### Map exception

When a map is visible:

- AMap owns pan, zoom, tap, and map gestures.
- The app does not intercept directional swipes.
- The map fills the screen.
- Large floating buttons appear at the bottom.

This applies to both destination picking and active navigation.

### Current screen flow

1. **AMap consent**
   - explicit privacy/data notice
2. **Home**
   - right: Navigation
   - left: Settings
3. **Destination**
   - right: New destination
   - left: Recent/saved destinations
   - down: Back
4. **New destination**
   - right: Voice destination
   - left: Search AMap or point on map
   - down: Back
5. **Voice destination**
   - giant microphone button
   - yellow idle, red listening
   - tap starts recognition and an audible tone
   - down swipe: Back
6. **Search/map**
   - full-screen native AMap
   - query overlay uses AMap input tips
   - suggestion selection pins and zooms to the place
   - map tap creates a pin and reverse-geocodes it
   - bottom controls: Back, Clear, Use Point
7. **Search results**
   - one candidate at a time
   - announces list position, name, address, and straight-line distance
   - right: Confirm
   - left: Decline and hear next
   - down: Back
8. **Confirm destination**
   - right: Confirm
   - left: Choose another
   - down: Back
9. **Route preview**
   - destination, route distance, and duration
   - right: Start
   - left: Decline
   - up: Review complete route, when AMap returned steps
   - down: Back
10. **Route walkthrough**
    - one ordered AMap walking step at a time
    - maneuver, distance, road name, mapped traffic-light context
    - right: Next
    - left: Previous
    - down: Route preview
11. **Active navigation**
    - full-screen native `AMapNaviView`
    - floating Repeat and Pause controls
12. **Paused**
    - route map remains visible
    - floating Continue and End controls
    - local obstacle detection is explicitly described as still active
13. **Arrival**
    - finish/back-to-home interaction

The code also retains typed-destination and developer/engineering screens as
fallback/support paths.

### Native AMap navigation UI options currently enabled

- automatic route drawing
- automatic zoom
- compass
- traffic layer and traffic line
- route list button
- lane information
- real/model crossing display
- secondary action
- automatic overview

Some AMap UI flags were designed mainly for driving and may be ignored or have
limited value during walking navigation. Verify behavior on a real walking route.

---

## 7. Walking guidance and AMap data

### Walking-route model

`RouteSummary` contains:

- total distance
- total duration
- ordered `WalkingRouteStep` list
- total mapped traffic-light count

Each `WalkingRouteStep` contains:

- mapped maneuver
- distance
- duration
- road name
- mapped traffic-light count
- generated spoken instruction

AMap icon types are mapped to the app's maneuver vocabulary, including
crosswalk, stairs, and elevator where supplied.

### Live guidance

`NaviInfo` is converted to:

- maneuver
- metres to the next action
- next road
- optional mapped traffic-light context within 50 metres
- a safety warning when the maneuver is a crosswalk

Frequent AMap updates are filtered so an instruction is forwarded only when the
maneuver/road changes or the distance enters a new five-metre bucket.

### What AMap is used for now

The app bundles and uses native Android Navigation, 3D Map, Location, and Search:

- continuous positioning
- coordinate conversion for system-GPS fallback
- input tips
- POI search and POI-ID lookup
- native map interaction
- markers
- reverse geocoding
- POI-based walking routes
- GPS and emulator navigation
- native route overlays and navigation UI
- voice guidance
- weak-GPS and off-route callbacks
- indoor-map support when coverage exists

### SDK versus Web Service decision

Use the Android SDKs for the real-time on-phone flow. Do not add Web APIs merely
to duplicate location, POI search, maps, geocoding, or walking navigation already
provided by the SDK.

Possible later Web Service use:

- Route Planning 2.0 behind a project-owned backend for server-side alternative
  route analysis
- backend geocoding or analysis where justified

Rules:

- Web Services require a separate Web Service key.
- Never embed a Web Service key in the APK.
- Trajectory correction is not an immediate pedestrian-safety tool.
- Nearby-category search should be added only for focused needs such as
  entrances, toilets, or bus stops; never announce all nearby POIs.

Official AMap references already reviewed:

- <https://lbs.amap.com/api/android-sdk/summary>
- <https://lbs.amap.com/api/android-sdk/download/>
- <https://lbs.amap.com/api/android-sdk/guide/map-data/geo/>
- <https://lbs.amap.com/api/android-location-sdk/guide/android-location/getlocation>
- <https://lbs.amap.com/api/android-navi-sdk/guide/navigation-map/gps-navi>
- <https://lbs.amap.com/api/android-navi-sdk/guide/route-plan/independentcalculateroute>
- <https://lbs.amap.com/api/webservice/guide/api/newroute>
- <https://lbs.amap.com/api/webservice/guide/api/georegeo/>
- <https://lbs.amap.com/api/>

---

## 8. Simulation and engineering tools

The emulator defaults to **Use simulated system**.

The simulation stands in for:

- upper controller
- upper-controller GPS
- USB-to-TTL link
- lower ESP32
- left/right speakers
- left/right vibration motors

It provides:

- deterministic Changsha coordinates
- three deterministic mock voice-search results
- a clearly labelled software-only route
- mock walking steps, including a mapped crossing and traffic light
- packet generation through the same temporary encoders
- observable mock device events and spoken feedback

It does not simulate:

- camera imagery
- YOLO accuracy
- pseudo-binocular depth
- target-lock stability
- IMU fusion
- cloud-model latency
- real firmware timing or failures

The engineering console supports:

- vibration by left/right/both side
- intensity, pattern, duration, and repeats
- speaker cue by left/right/both side
- volume and repeats
- manual navigation instructions
- stationary AMap simulated navigation
- BLE scan/connect/write
- packet preview
- raw 1–20 byte packet transmission
- stop-all output

---

## 9. Temporary device protocol

Status: engineering placeholder, version 1.

### Temporary BLE endpoint

- Service UUID: `5c10a001-9c1b-4c7f-9c6a-43d42f2d1000`
- Writable characteristic:
  `5c10a002-9c1b-4c7f-9c6a-43d42f2d1000`
- Raw payload limit in the test screen: 20 bytes
- Multi-byte values: little-endian
- Use write-without-response when available, otherwise normal write

### Shared 12-byte frame

| Byte | Meaning |
| ---: | --- |
| 0 | Magic `0x53` (`S`) |
| 1 | Protocol version `0x01` |
| 2 | Packet type |
| 3–4 | Unsigned sequence number |
| 5–10 | Type-specific payload |
| 11 | XOR checksum of bytes 0–10 |

Packet types:

| Value | Meaning |
| ---: | --- |
| `0x01` | Navigation |
| `0x10` | Vibration |
| `0x11` | Speaker/audio cue |
| `0x12` | Stop all outputs |

Side masks:

| Value | Side |
| ---: | --- |
| `0x01` | Left |
| `0x02` | Right |
| `0x03` | Both |

Vibration payload:

- byte 5: side
- byte 6: intensity, 0–100%
- byte 7: continuous/pulse/double/triple pattern
- bytes 8–9: duration, clamped to 10–10000 ms
- byte 10: repeats, 1–10

Speaker payload:

- byte 5: side
- byte 6: cue ID
- byte 7: volume, 0–100%
- byte 8: repeats, 1–10
- bytes 9–10: reserved

Current cue IDs:

1. test tone
2. turn left
3. turn right
4. go straight
5. obstacle
6. stop
7. arrived

Navigation payload:

- byte 5: maneuver code
- bytes 6–7: distance in metres
- bytes 8–9: time-to-live in 100 ms units
- byte 10: source (`1` AMap, `2` manual/demo)

Firmware should reject invalid size, magic, version, or checksum and discard
expired route commands. Raw mode intentionally adds no header or checksum.

### Production protocol still needed

The robotics team must define:

- Android-to-upper-controller transport: BLE, Wi-Fi, USB, or another choice
- framing and versioning
- acknowledgement and retry
- heartbeat
- reconnect behavior
- failure/low-battery states
- GPS/IMU ownership and fused-pose schema
- coordinate frames and camera calibration
- object, obstacle, target-lock, and confidence event schemas
- priority/interruption rules
- speech ownership: Android, upper controller, or local speaker clips
- privacy and cloud boundaries

The preferred software architecture is to replace the transport adapter while
keeping the accessible app flow and domain command boundary stable.

---

## 10. Build, emulator, and key setup

### Recommended tools

Use Android Studio as the primary environment. It provides Gradle sync, SDK and
AVD management, Logcat, device controls, and Compose tools. VS Code can be a
secondary editor.

### JDK on this Mac

Use Android Studio's bundled JDK:

```text
/Applications/Android Studio.app/Contents/jbr/Contents/Home
```

Command-line example:

```bash
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew check
```

### AMap key

1. Copy `local.properties.example` to ignored `local.properties`.
2. Keep Android Studio's `sdk.dir`.
3. Add `AMAP_API_KEY=...`.
4. Register package `com.csust.soleprecision` in the AMap console.
5. Register the correct debug and release signing SHA fingerprints.
6. Never put the actual key in this handoff, source code, README, or Git.

### Known-good emulator

- Android 16 / API 36
- ARM64
- tested AVD: Pixel 8 class, `sdk_gphone64_arm64`

### Known-bad emulator combination

The tested Android 17 / API 37.1 preview image with 16 KB memory pages caused
AMap 11.2 native/OpenGL renderer failure. This was an SDK/emulator compatibility
problem, not a reason to replace AMap with a hand-built fake map.

Use API 36 ARM64 or a physical ARM64 phone.

ADB occasionally reported the emulator offline during long sessions. Restarting
the ADB server restored the connection:

```bash
adb kill-server
adb start-server
```

### Useful verification commands

```bash
./gradlew test
./gradlew lint
./gradlew assembleDebug
./gradlew check
git diff --check
```

The most recent recorded verification before this handoff passed Gradle `check`
and `git diff --check`. Unit tests and lint were successful.

---

## 11. Existing automated tests

Current unit-test areas:

- route distance and duration formatting
- place spoken-description formatting
- walking-step speech with mapped traffic-light context
- stable little-endian navigation packet encoding
- distance clamping
- vibration packet encoding
- audio packet encoding
- stop-all packet encoding
- raw hex parsing and validation
- 20-byte BLE raw limit
- mock transport offline/connected behavior

Still missing:

- Compose UI tests
- swipe threshold/direction tests
- map-screen lifecycle/instrumentation tests
- permission denial/recovery tests
- SpeechRecognizer instrumentation tests
- BLE reconnect and fault tests
- AMap route integration tests
- real hardware tests
- participant accessibility/usability tests

---

## 12. Known inconsistencies, limitations, and risks

### Documentation drift

The active repo's `README.md` still contains older statements that large buttons
are the default and swipe-only is optional. Current source hard-wires the
directional/swipe layout and removes the stored navigation-layout setting.
Treat `ProductionApp.kt`, `GestureNavigation.kt`, and this handoff as the current
truth. Update the README before the next commit.

### Saved destinations

The UI distinguishes “saved” and “recent” destinations, but the current
implementation has only recent-history storage. A separate saved/favorites model
is not complete.

### Localization

English, Simplified Chinese, and Traditional Chinese appear as language choices,
but most UI text is still English. Full Android string resources, Chinese copy,
and localized accessibility labels are required before Chinese participant
testing.

### Swipe accessibility

Swipe-only menus are visually simple but conflict with TalkBack touch exploration
and gesture-independence guidance. Test with real blind participants. Do not use
internal preference as evidence that it is accessible.

### Hardware

- final Android-to-upper-controller interface is unknown
- current BLE UUIDs and frames are placeholders
- no inbound obstacle event path exists
- no acknowledgement, heartbeat, or automatic reconnect
- no real low-battery/fault model
- no camera, YOLO, depth, target-lock, or sensor-fusion implementation in Android

### Navigation/safety data

- mapped traffic lights/crosswalks may be incomplete
- AMap route data is not real-time obstacle or traffic verification
- some native navigation UI options may not be meaningful for walking
- map point selection should be validated on a physical device
- offline behavior is not production-ready

### Privacy

- AMap consent exists, but a complete Chinese privacy policy does not
- no consent-withdrawal and stored-data erasure flow
- cloud-model data boundaries are not defined
- microphone, location, camera, and future cloud processing require clear
  purpose limitation and retention rules

### Research evidence limits

Earlier field research includes strong design signals but is not a substitute
for direct validation:

- questionnaires in the workspace appear to be templates rather than completed
  response datasets
- one employee interview is not representative
- no durable direct blind-user interview dataset is present
- several public/company statistics are dated and should be reverified before
  publication
- a dedicated current competitive review of similar camera wearables and blind
  navigation products is not preserved as a final artifact in the workspace

---

## 13. Recommended next work

### Immediate software hygiene

1. Review and commit the authoritative dirty working tree.
2. Push it to `origin/main` only after review.
3. Update README sections that still describe the discarded button layout.
4. Keep the stale CSUST Android copy clearly marked or remove it only with the
   owner's explicit approval.

### Next product/software priorities

1. Obtain the robotics team's actual Android/upper-controller interface.
2. Replace/adapt the temporary transport and packet contract.
3. Add inbound obstacle and target-lock events with explicit priority.
4. Add ACK, heartbeat, reconnect, low-battery, and fault states.
5. Complete Chinese localization and privacy materials.
6. Implement real saved/favorite destinations.
7. Add focused nearby searches such as entrance, toilet, or bus stop only when
   requested.
8. Test full-screen point picking and walking navigation on a physical ARM64
   phone outdoors.
9. Add early/prepare/action-now cue timing without over-speaking.
10. Build a recorded-camera test harness before attempting live YOLO integration.
11. Define calibration, coordinate frames, and target-lock confidence.

### Validation priorities

Test with:

- TalkBack on and off
- maximum practical font/display scaling
- portrait and landscape
- network available/unavailable
- location granted/denied/unavailable
- recognition available/unavailable
- empty, ambiguous, and successful searches
- route calculation failure and off-route recalculation
- BLE connected/disconnected
- weak GPS
- backpack low battery/lost connection
- Chinese blind and low-vision participants
- an orientation-and-mobility specialist

Do not finalize gesture mappings, haptic patterns, speaker placement, or speech
density before participant evaluation.

---

## 14. Earlier research background

The work initially included a Cofoe accessibility/medical-device field-research
stream. That material helped establish general principles:

- assistive-product value depends on fit, trust, daily usability, maintenance,
  education, and service—not only hardware capability
- the customer journey and failure recovery matter
- information overload can be as harmful as missing information
- configuration and training should involve helpers without making the user
  dependent on them

The later vision-accessibility journey considered tasks from travel and shopping
through product identification, cooking, cleaning, and navigation. It contributed
the six concern areas listed earlier.

`PROJECT_CONTEXT.md` contains the detailed earlier timeline, Cofoe public/company
baseline, visit/interview notes, research limitations, QFD concepts, and artifact
history. Its old final shortlist—including concepts such as AccessOS—predates and
is superseded by the explicit decision to build Sole Precision.

### Important research artifacts

- `PROJECT_CONTEXT.md`
- `CSUST_Accessible_Medical_Equipment_Questionnaire.docx`
- `CSUST_Accessible_Medical_Equipment_User_Needs_Questionnaire.docx`
- `CSUST_Cofoe_Research_Brief_and_Questionnaire.docx`
- `CSUST_Cofoe_Quality_Function_Expense_Questionnaire.docx`
- `outputs/vision_accessibility_customer_journey.png`
- `outputs/COFOE_Company_Visit_Field_Research_Bilingual_Mobile_Safe.pptx`
- `outputs/COFOE_Company_Visit_and_Field_Research_Merged.pptx`
- `outputs/COFOE_Company_Visit_and_Field_Research_Merged_Web_Mobile.pdf`
- `outputs/COFOE_Merged_Cross_Platform.pptx`
- `outputs/COFOE_Merged_Fixed_Layout.pdf`
- `outputs/Cofoe_Gaoqiao_Field_Research_Bilingual.pptx`
- `outputs/Cofoe_Gaoqiao_Field_Research_Bilingual_Revised.pptx`

The `rendered*` directories contain visual-QA outputs for those documents.

---

## 15. Innovation-camp PDF and available hardware context

`/Users/balazsmolnar/Downloads/00021517-2.pdf` is a six-page CSUST
China–Europe Youth Inclusive Technology Assistive Design Innovation Camp plan,
dated 27 April 2026. It describes a 14-day AIoT accessibility prototype/showcase
program and gives a smart blind cane as an example.

Its materials list includes items relevant to prototyping:

- Arduino Nano/UNO
- ESP32 Type-C board and expansion
- Raspberry Pi 5, 4 GB
- edge-AI kits such as Raspberry Pi + accelerator, Jetson Nano, or Sophgo
- STM32
- UNIHIKER M10
- motors and servos
- 0.5 W speaker/buzzer and PWM amplifier
- 1 MP USB camera
- sensor kits
- EMG, NFC, TDS
- GPS/BeiDou module
- Bluetooth module
- fabrication and prototyping tools

This is an availability/reference list, not a confirmed final BOM for Sole
Precision.

---

## 16. Active repo documentation

Read these before changing the relevant area:

- `docs/SYSTEM_CONCEPT_AND_INNOVATION.md` — system architecture, innovation
  analysis, two MVPs, and open interfaces
- `docs/ACCESSIBLE_UX_SPEC.md` — UX hierarchy, narration, guidance priority, and
  manual test matrix
- `docs/AMAP_CAPABILITY_PLAN.md` — integrated AMap capabilities and SDK/Web
  Service decisions
- `docs/TEMPORARY_DEVICE_PROTOCOL.md` — exact temporary BLE and packet contract

The protocol document is authoritative for the current temporary hardware
frames. The implementation and tests should remain consistent with it.

---

## 17. Instructions for future collaborators and coding agents

- Work only in the authoritative `StudioProjects` Android repository unless the
  owner explicitly changes the source of truth.
- Inspect `git status` before editing. Preserve all existing uncommitted work.
- Do not reset, checkout over, clean, or delete the current working tree.
- Do not expose or commit `local.properties` or AMap credentials.
- Use Android Studio and API 36 ARM64 for emulator work.
- Keep map screens native and full-screen; do not reintroduce a hand-drawn fake
  map.
- Keep map gestures separate from app directional swipes.
- Keep unavailable swipe directions physically stationary.
- Keep down = Back, right = Confirm/Next, left = Decline/Previous.
- Settings may use conventional controls.
- Keep route and obstacle signals separate in both code and language.
- Never phrase map data as proof that a crossing is safe.
- Keep mock behavior explicitly labelled as simulation.
- Do not silently invent the robotics interface. Mark open fields and obtain the
  team's real contract.
- Run unit tests, lint, build/check, and `git diff --check` after material
  changes.
- Update this file when architecture, protocol, source location, or major UX
  decisions change.

## Current definition of success

The near-term success state is not “replace a cane” or “describe everything.”
It is:

> A blind or low-vision user can choose and confirm a walking destination,
> understand the route before beginning, receive concise AMap turn context, and
> receive higher-priority left/right local obstacle cues from a wearable whose
> failure states are explicit.
