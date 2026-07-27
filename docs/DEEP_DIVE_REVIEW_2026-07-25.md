# Deep-Dive Review — 25 July 2026

Scope: full code review from a blind/low-vision user perspective, feasibility and
design assessment, plus a **verified** procedure for testing walking navigation in
the Android Studio emulator. All findings were produced against the current
uncommitted working tree and verified where noted.

> **Resolution status (same day):** the implementation pass described in
> CLAUDE.md “Update — 25 July 2026” fixed B.1.1–B.1.3, B.2.4–B.2.6 (partially
> B.2.7: search-browse announcements are detail-aware; no listening countdown
> yet), B.3.8–B.3.9, C.1–C.6, D.1–D.3 and D.5-adjacent transport issues
> (wire format intentionally unchanged), and added the A.2 production
> simulated-movement toggle (verified live: full flow map-pin → route →
> simulated navigation → arrival, in Simplified Chinese, with live weather).
> Still open: B.2.7 countdown, B.3.10 map-helper note, B.3.12 rotation
> strategy, D.4 protocol-level ACK/CRC (needs the real device contract),
> participant validation, native-speaker translation review.

---

## Part A — Testing navigation in the emulator (VERIFIED WORKING)

### A.1 What was verified live on 25 July 2026

Emulator: AVD `Pixel_8_2` (API 36, ARM64, `google_apis_playstore`). The other AVD
(`Pixel_8`) is the known-bad Android 37.1 16 KB-page image — do not use it.

Procedure executed and confirmed end-to-end:

1. Install `app-debug.apk`, grant location/microphone/BLE permissions.
2. (Optional) `adb emu geo fix 112.9388 28.2282` to park the emulator GPS in
   Changsha. **Not required** for the simulated mode.
3. In app: Home → swipe left (Settings) → App settings → scroll down →
   **Open engineering console** → scroll to
   **“5. AMap navigation without movement”** → keep **Simulated movement** ON →
   **Start stationary AMap simulation**.
4. Result: AMap calculated a real walking route between the prefilled Changsha
   coordinates, `AMapNavi.startNavi(NaviType.EMULATOR)` advanced the position
   along the route with no GPS movement, live instructions appeared
   (“Turn right”, “Crosswalk ahead”), native AMap voice spoke guidance
   (audio-focus activity from `com.amap.api` visible in logcat), navigation
   packets were forwarded to the simulated ESP32, and the run ended with
   “Destination reached toward 目的地”.

Why this works with zero GPS: per AMap docs, `NaviType.EMULATOR` (“模拟导航”)
needs no positioning at all — the SDK drives the position itself. Walking
emulation speed via `setEmulatorNaviSpeed(int)` defaults to 20 km/h for walking
(valid 10–30), i.e. the simulation runs 2–4× real walking pace.

### A.2 Key code-path facts

- The emulator navi type is set only in
  `AmapNavigationController.calculateWalkingRoute(..., simulateMovement)`
  (`AmapNavigationController.kt:279`), reachable only from the engineering
  console (`SolePrecisionApp.kt:442`).
- The **production flow never simulates**: `planWalkingRoute()` hard-codes
  `simulateMovement = false` (`AmapNavigationController.kt:255`), so the full
  swipe flow (Route preview → Start → `ACTIVE_NAVIGATION` with `AMapNaviView`)
  always starts `NaviType.GPS` and will sit motionless on an emulator.
- The engineering console keeps you on the `ENGINEERING` screen
  (`ProductionApp.kt:213–228`) — you get instruction text, TTS, and packets, but
  **not** the full-screen `AMapNaviView` experience.

**Recommended small improvement:** a developer-only preference (e.g. “Simulate
movement when starting navigation”, default off, or auto-offered when
`isProbablyEmulator()`), which makes `startPlannedRoute()` use
`NaviType.EMULATOR`. That would let the complete production UX — route options,
preview, walkthrough, active map navigation, arrival — be exercised in the
emulator.

### A.3 Alternative methods (for the paths simulation cannot test)

`NaviType.EMULATOR` never deviates from the route, so it cannot test off-route
recalculation, weak-GPS states, or the location pipeline. For those:

1. **`adb emu geo fix` script** (tests the real location pipeline):
   - Syntax: `adb emu geo fix <LONGITUDE> <LATITUDE> [alt] [nsats] [velocity_knots]`
     — **longitude first**; no console auth token needed via `adb emu`.
   - Walking pace ≈ 1.4 m/s: send one fix per second, points ~1.4 m apart:
     ```bash
     while read lng lat; do
       adb emu geo fix "$lng" "$lat" 45 8 2.7
       sleep 1
     done < route_wgs84.txt
     ```
   - **Coordinate trap:** the emulator speaks WGS-84. `AMapLocationClient`
     converts to GCJ-02 itself for fixes inside mainland China (mock fixes
     included; `setMockEnable` defaults to true since SDK 3.4.0). Therefore
     inject *true WGS-84* points. Never feed AMap route geometry (GCJ-02)
     directly into `geo fix`/GPX — it gets converted a second time and lands
     100–600 m off. To derive test points from an AMap polyline, apply an
     inverse GCJ-02→WGS-84 transform first (e.g. `eviltransform`).
   - Deliberately steering the point stream off the polyline is the only
     emulator way to trigger `onReCalculateRouteForYaw`.
2. **GPX/KML playback** (Extended Controls → Location → Routes → Load GPX/KML):
   same fidelity as the script once authored; timestamps drive pacing (1×–3×).
   Author the file in WGS-84.
3. **Extended Controls “Routes” tab with Google Maps search** — effectively
   unusable for Changsha (no Google walking directions in mainland China and
   GCJ-shifted Google tiles make clicked points wrong). Use only “Single
   points” with typed coordinates.

Recommended combo: built-in simulation (A.1) for routine testing; a `geo fix`
replay once per milestone to validate weak-GPS/off-route/remaining-distance
logic.

---

## Part B — Findings from a blind/low-vision user perspective

### B.1 Critical

1. **TalkBack users cannot operate the app at all.** The swipe screens use raw
   `detectDragGestures` with consumed pointer events and provide no
   `semantics { customActions }` or button fallback
   (`GestureNavigation.kt:135–195`). With TalkBack on, TalkBack intercepts all
   swipes and the screen is one inert pane. The app even detects TalkBack
   (`ScreenNarrator.kt:38`) — but only to mute narration, not to offer an
   operable UI. Fix direction: expose each available direction as an
   accessibility custom action, and/or auto-switch to a button layout when
   touch exploration is active. Until then, the app is only usable by blind
   users who do *not* run a screen reader — a small subset.
2. **The “Navigation speech detail” setting is dead.** `SpeechDetail`
   (CONCISE/STANDARD/DETAILED, `UserPreferences.kt:11–14`) is offered in
   Settings but never read anywhere; all narration is one verbosity. For this
   user group, speech density is a primary safety/usability control (docs
   explicitly warn that feedback must not mask environmental sound).
3. **Language support is cosmetic.** Simplified/Traditional Chinese only switch
   recognition/TTS locale; every UI string, error message, narration line, and
   the recognizer prompt are hard-coded English (e.g. `MainActivity.kt:262–315`,
   `ProductionApp.kt:809–822`). Blocks Chinese participant testing entirely.
   Fix: move all user-facing/spoken strings to resources and localize.

### B.2 High

4. **No audio focus management in the app’s own narration.**
   `ScreenNarrator` never requests audio focus; AMap’s native voice does
   (observed in logcat), so app TTS can talk over the AMap route voice, media,
   or calls. Also, everything is spoken with `QUEUE_FLUSH`
   (`ScreenNarrator.kt:45–50`): any new utterance kills the current one, and
   only a single `pendingMessage` survives TTS init. There is no priority
   model — this directly contradicts the project’s own 5-level cue priority
   ladder. Fix: audio-focus request with ducking; small priority queue
   (safety > status > narration), `QUEUE_ADD` for low-priority lines.
5. **TTS init failure is silent** (`ScreenNarrator.kt:19–21`): a blind user gets
   a completely silent app with no fallback signal (vibration/visual banner).
6. **Two competing voices by design.** Native AMap route voice is enabled and
   `ScreenNarrator` also speaks instructions/status. The ownership question
   (“who speaks during navigation”) is unresolved in code, and both can speak
   simultaneously.
7. **Search results / screen announcements repeat or vanish by state quirks**:
   narration fires only on `screen` change so re-entering the same screen says
   nothing (`ProductionApp.kt:183–206`); rapid swipes through results queue
   announcement spam (`ProductionApp.kt:1477–1485`).

### B.3 Medium / notable

8. Emoji/Unicode glyphs (🎙, ✓, ✕, arrows) used as controls — inconsistent
   rendering and scaling; use vector icons with semantics.
9. Unavailable swipe directions are visually identical to available ones
   (`GestureNavigation.kt:284–304`) — low-vision users can’t tell which of the
   four directions are active.
10. Map point-picking has no non-visual alternative and no stated assumption
    that a sighted helper is needed (`ProductionApp.kt:970–1254`).
11. Voice input has no listening countdown/timeout feedback; timeout error is a
    generic English string (`MainActivity.kt:303–317`).
12. Rotation recreates the activity and can replay/lose narration; no
    configuration-change strategy.

### B.4 Genuine strengths worth keeping

TalkBack-aware narration muting; `paneTitle` on every screen; live regions on
dynamic status; ≥72 dp touch targets; high-contrast black/yellow palette
exceeding WCAG AA; consistent down=Back mental model; switches with explicit
state descriptions; deliberate safety phrasing (“Confirm the real surroundings
before continuing” on crosswalk steps); mock/simulation clearly labelled.

---

## Part C — Navigation-layer correctness findings

1. **`selectRoute()` never resets `routeCompletionHandled`**
   (`AmapNavigationController.kt:320–328`): after one route completes, choosing
   an alternative can leave the summary stale.
2. **Listener leak on partial init**: `initializeAfterConsent()` adds the
   AMapNavi listener before the try-block completes
   (`AmapNavigationController.kt:209–224`); a later exception leaks it.
3. **Mock→real switch races location** (`MainActivity.kt:683–702`):
   `currentLocation` is nulled and may stay null if AMap responds slowly,
   wedging route planning on “Finding current location…”.
4. **`activeWalkingSteps` mutated from SDK callback thread and read elsewhere
   without synchronization** (`AmapNavigationController.kt:29,146,346`).
5. **Engineering console coordinates are unvalidated**
   (`SolePrecisionApp.kt:441–462`) — no `LocationValidity` check, no
   start≠end check; bad input yields cryptic AMap failures.
6. **Duplicate `onCalculateRouteSuccess` overloads** can double-process routes
   (`AmapNavigationController.kt:54–78`).
7. Mock navigation is a static demo (single “continue straight 80 m”
   instruction, `MainActivity.kt:625–634`) — fine, but it should not be
   mistaken for a progressing simulator (the AMap emulator mode is the real
   simulator).
8. Solid parts: 5 m instruction bucketing, POI-entrance preference, WGS-84→
   GCJ-02 conversion on the system-GPS fallback, null-island/stale-fix
   rejection, comprehensive maneuver mapping with blind-user wording,
   correct haversine/projection geometry in `RouteGeometry`.

---

## Part D — Transport & hardware feasibility findings

For a safety-critical cue channel (a missed turn/obstacle cue is a hazard),
the current BLE layer is demo-grade — acceptable as a placeholder, but the gaps
below define the real integration work:

1. **Fire-and-forget writes**: no `onCharacteristicWrite` callback; pre-API 33
   path returns `true` unconditionally (`BleWearableTransport.kt:147–187`).
   The app cannot know whether the backpack heard anything.
2. **No reconnect logic** after unexpected disconnect
   (`BleWearableTransport.kt:69–82`); no connect timeout after `connectGatt`.
3. **No GATT write queue** — Android allows one in-flight write; back-to-back
   instructions can silently drop.
4. **No ACK/heartbeat in the protocol**; XOR checksum is weak (paired bit flips
   cancel) — use CRC16 and an ACK-with-sequence for critical packet types.
5. **No MTU negotiation**; 20-byte limit enforced only in the raw-hex path.
6. **Zero tests for `BleWearableTransport`** (encoders are well-tested).
7. Manifest/permissions are clean (`neverForLocation`, correct maxSdkVersion
   scoping); the `WearableTransport` abstraction boundary is the right design
   and should be preserved when the real contract arrives.

Broader feasibility (unchanged from handoff, reaffirmed): pseudo-binocular
depth from two uncalibrated monocular shoulder cameras is the highest-risk
technical claim; the priority ladder exists in documentation but no inbound
obstacle event path exists yet; phone-side software must never be the only
emergency stop.

---

## Part E — Suggested priority order

1. Accessibility floor: TalkBack custom actions / button fallback (B.1.1).
2. Wire up `speechDetail` + narration priority queue + audio focus (B.1.2, B.2.4).
3. String resources + Chinese localization (B.1.3).
4. Developer toggle: simulated movement for the production navigation flow (A.2).
5. Navigation-layer fixes C.1–C.5 (small, mechanical).
6. BLE hardening batch D.1–D.5 when the real device contract lands.
