# CSUST Sole Precision Project — Complete Handoff

Last audited and consolidated: 24 July 2026
Major updates: 25 July 2026 — see “Update — 25 July 2026” and “Update 2 — 25 July
2026” below. Where those sections conflict with older text in this file, the
updates win; Update 2 wins over Update 1.
Primary product: **Sole Precision**, an AI-assisted backpack/vest for blind and
low-vision pedestrians
Current software: Android walking-navigation prototype using AMap/Gaode

This is the canonical handoff for the project. It combines the project
conversation, current Android source, saved design documents, research artifacts,
hardware assumptions, safety constraints, build state, and open work.

## Update — 25 July 2026

A large accessibility/feature/localization pass was implemented, built, unit-
tested, linted, and verified end-to-end on the API 36 ARM64 emulator
(`Pixel_8_2`). Where statements below contradict older sections, this list is
authoritative:

1. **TalkBack operability**: swipe screens now expose every direction as a
   Compose accessibility custom action, and when TalkBack touch exploration is
   active the same screens automatically render as large conventional buttons
   (`ui/GestureNavigation.kt`). Accepted swipes give haptic feedback; glyph
   arrows/check/cross were replaced with vector icons
   (`androidx.compose.material:material-icons-core` added).
2. **Narration architecture**: `accessibility/ScreenNarrator.kt` now has a
   priority model (CRITICAL/HIGH/NORMAL — critical speech cannot be cut off by
   routine narration), requests transient-duck audio focus, applies the
   speaker-volume preference, and reports TTS engine failure to the UI (red
   banner on Home). During real AMap navigation the native AMap voice owns
   route audio and app screen narration is suppressed on the map screen; in
   simulation the app narrator carries instructions instead.
3. **Speech detail preference is now real**: CONCISE/STANDARD/DETAILED change
   every screen introduction and item announcement (`ui/NarrationText.kt`).
4. **Full trilingual localization**: all production UI text and narration is
   served from a compile-checked in-Kotlin phrase table
   (`i18n/Phrases.kt`, `EnglishPhrases.kt`, `ChinesePhrases.kt` — English,
   Simplified and Traditional Chinese). Language switching is instant (no
   activity recreation). Controller status strings remain canonical English in
   state and are translated at display time via `Phrases.statusText`
   (dictionary + prefix templates + location-status pattern). Domain classes
   (`Maneuver`, `RouteSummary`, `WalkingRouteStep`, `NavigationInstruction`)
   keep English output for tests/console; the UI rebuilds spoken text from
   structured fields. Chinese wording still needs native-speaker review before
   participant testing. The engineering console intentionally stays English.
5. **New blind-user features**:
   - *Where am I* (Home ↑): reverse-geocoded current address + nearest mapped
     place + accuracy + explicit “map information, not a safety check” caution.
   - *Saved destinations*: real favorites store
     (`navigation/FavoriteDestinationsStore.kt`), save via ↑ on Confirm
     destination, browse/remove under Recent-and-saved → Saved.
   - *Nearby essentials* (Choose destination ↑): focused one-at-a-time nearby
     category search (public toilet, bus stop, metro, pharmacy, hospital,
     supermarket) via `navigation/AmapNearbySearchController.kt` (1 km radius,
     distance-sorted, max 5, never announces everything).
   - *Live local weather* on Route preview via
     `navigation/AmapWeatherController.kt` (AMap WeatherSearch; adcode/city
     from the location fix; also spoken in DETAILED speech mode).
6. **Emulator/production navigation testing**: new developer preference
   “Simulate movement when navigating” (App settings → Developer tools) makes
   the *production* flow start `NaviType.EMULATOR` with walking emulation speed
   10 km/h, so the complete real UX (map pin → confirm → route options →
   preview → walkthrough → active AMapNaviView navigation with native voice →
   arrival) runs indoors/on the emulator. Verified live on 25 July 2026 with a
   real 313 m Changsha route. The engineering-console stationary simulation
   also remains.
7. **Navigation-layer fixes**: listener attach order on init, emulator speed at
   init, `selectRoute` resets the instruction key, `stop()` clears it,
   `@Volatile` on callback-shared state, structured fields added to
   `NavigationInstruction`, console coordinates validated (range + start≠end).
8. **BLE hardening (wire format unchanged)**: serialized GATT write queue with
   `onCharacteristicWrite` confirmation, bounded reconnect with backoff on
   unexpected disconnect, connect timeout, MTU request, permission re-checks,
   failure statuses surfaced. UUIDs and the 12-byte frames still match
   `docs/TEMPORARY_DEVICE_PROTOCOL.md`.
9. **Structure changes**: `ui/ProductionApp.kt` was split (shared widgets to
   `ui/Components.kt`, narration to `ui/NarrationText.kt`); `Phrases` is a
   plain class (a data class with ~250 params exceeds the JVM 255-argument
   limit in `copy$default` — do not convert it back); place encoding shared via
   `PlaceCandidateCodec` in `FavoriteDestinationsStore.kt`; `UserLocation`
   gained `cityName`/`adCode`; AMap location now requests addresses
   (`isNeedAddress = true`).
10. **Superseded statements in older sections**: “saved destinations not
    implemented”, “speech detail not applied”, “UI copy is hard-coded
    English”, and “swipe-only has no TalkBack path” are no longer true. The
    remaining known gaps: Chinese translation review by a native speaker,
    participant validation of the swipe model, instrumentation/Compose tests,
    and everything hardware-related (inbound obstacle events, real transport
    contract, ACK/heartbeat at the protocol level).
11. New unit tests: `i18n/PhrasesTest.kt` (English parity with domain strings,
    status translation, coverage of all maneuvers/directions) and
    `ui/NarrationTextTest.kt` (detail levels, every screen has an intro in
    every language). `./gradlew check assembleDebug` passed on 25 July 2026;
    full flow verified on emulator. See
    `docs/DEEP_DIVE_REVIEW_2026-07-25.md` for the findings this pass resolved
    and the verified emulator-testing procedures.

## Update 2 — 25 July 2026 (precise pedestrian guidance, design system)

Second pass, after the owner tested live navigation and reported that guidance was
"high level, like a car route", that some buttons were not read aloud, and that the
design needed stronger differentiation. All items below are built, unit-tested,
linted and verified on the API 36 ARM64 emulator.

1. **Pedestrian guidance engine** (`navigation/PedestrianGuidance.kt`) replaces the
   old 5-metre-bucket repetition with a staged cue model:
   - `EARLY` (~120 m), `PREPARE` (~30 m), `ACT` (≤8 m; ≤12 m for hazards),
     `CONFIRM` (after a maneuver: new road, heading, length), `PROGRESS`
     (reassurance, 50 m spacing far out and 20 m closer in, with a
     distance-walked guard so bucket edges cannot double-fire), `OFF_ROUTE`
     (perpendicular drift ≥8 m, re-announced per 10 m change), `ARRIVAL` (≤25 m).
   - Each stage speaks at most once per step, so speech density stays low enough to
     hear traffic.
   - Verified live cadence: `Early → Progress → Prepare → Act → Confirm → Arrival`.
2. **Precision content**: cues carry a **clock-face direction** relative to the
   user's actual heading ("at 9 o'clock" — the convention blind travellers are
   trained on), approximate turn angle, the road being entered, mapped
   traffic-light count, a **landmark** near the maneuver point (one-shot nearest-POI
   reverse geocode, cached per step), remaining route distance/time, and explicit
   stop-and-verify wording at crossings.
3. **New geometry helpers** (`RouteGeometry`): `distanceToPathMeters` (drift),
   `initialBearingDegrees`, `relativeBearingDegrees`.
4. **Voice ownership resolved properly**: new preference
   `detailedPedestrianGuidance` (default **on**, Device settings → Guidance
   feedback). On: the app speaks the precise cues and AMap's inner voice is muted.
   Off: AMap's native driving-style voice speaks and the app stays quiet. Verified
   on the emulator — during a run only `USAGE_ASSISTANCE_ACCESSIBILITY` audio focus
   appeared, never AMap's `USAGE_MEDIA`.
5. **Phone haptics** (`feedback/HapticGuidance.kt`): directional turn cues encoded
   as rhythm (left = double tap, right = one long buzz, hazard = triple burst) at
   prepare/act, plus off-route and arrival patterns. Honours `guidanceMode` and
   `vibrationStrength`; needs the new `VIBRATE` permission. A matching left/right
   vibration packet also goes to the wearable at act-now using the existing
   temporary protocol (no wire-format change).
6. **Cue speech priority**: act/off-route/arrival are `CRITICAL` (cannot be cut
   off), prepare is `HIGH`, early/confirm/progress are `NORMAL`. Speech detail
   filters density — concise keeps prepare/act/hazards only, detailed adds progress,
   angles and remaining route.
7. **Unspoken-buttons fix**: `SwipeOnlyScreen` now announces the screen title plus
   **every available action generated from the live action map**
   (`LocalActionAnnouncer` → `GuidancePhrases.actionsSentence`). Screen
   introductions were rewritten to carry only *context* (place, route summary,
   layout of non-directional screens) — menus intentionally return an empty string.
   A control can no longer be shown without being spoken, and the previous
   duplicated gesture prose is gone.
8. **Design system** (`ui/Theme.kt`): colour is now semantic — amber
   `Confirm` (go/right), orange `Optional` (extra action/up, and cautions), red
   `Decline` (decline/stop/left), white `Neutral` (back/down). Direction bands carry
   a coloured edge bar so the action type is readable without reading the label.
   Type is the device's heaviest **condensed** family (`sans-serif-condensed`
   Black, no bundled or downloaded font since the app must work offline) with wide
   tracking; **caps are applied only to short labels** (`capsLabel`, ≤24 chars/≤3
   words) because all-caps destroys word shape for low-vision readers and screen
   readers can spell short all-caps strings letter by letter. Sentences stay in
   sentence case. Chinese is unaffected by `uppercase()`, so the rule is safe across
   languages.
9. **Live cue banner** on the navigation map shows the same sentence the user hears,
   in large condensed type, turning orange for act-now and off-route.
10. New string group `i18n/GuidancePhrases.kt` (English/Simplified/Traditional) for
    cue sentences and action lists — kept as its own class because `Phrases` is at
    ~252 constructor parameters and the JVM ceiling is 254. **Add new speech groups
    as new classes, never as more `Phrases` fields.**
11. New tests: `PedestrianGuidanceEngineTest` (stage progression, once-per-step,
    cadence spacing, hazard thresholds, drift, clock maths) and
    `GuidancePhrasesTest` (per-stage wording, hazard text surviving concise mode,
    action-list generation, all three languages). 61 unit tests pass;
    `./gradlew check assembleDebug` passes; lint clean.

Still open after this pass: native-speaker review of the Chinese, participant
testing of the swipe model and cue vocabulary, Compose/instrumentation tests,
listening-countdown for voice input, rotation strategy, and everything gated on the
real robotics interface (inbound obstacle events, ACK/heartbeat/CRC at protocol
level).

## Update 5 — 27 July 2026 (Pi voice app BUILT; pivot confirmed)

The hardware team answered the clarifying questions and the owner obtained an
AMap **Web Service** key, so the Pi pivot is now CONFIRMED and implemented.
Their answers: (1) Pi-not-phone confirmed; (2) navigation only, no device
feature control; (3) STT/TTS are theirs — we stay text-in/text-out; (4) network
via pre-connected hotspot; (5) command triggering logic is theirs; (6) GPS is a
Hiwonder GPS Module V1.0 (BeiDou+GPS, active antenna, NMEA) and a common IMU
with no fused compass heading; shared GPS/IMU timestamps "should be consistent
(to be debugged)". The owner additionally decided **Chinese-only voice
commands** (English keywords remain as an undocumented debug convenience; all
replies are Chinese).

`tools/upper_controller/sole_precision_core.py` (still single-file, stdlib-only,
Python 3.8+) is now the full Pi voice-navigation app:

1. **AMap Web Service client** (v3 REST: place/text, direction/walking,
   geocode/regeo, place/around, weather) — all GCJ-02 on that boundary; handles
   the v3 `[]`-for-empty-string quirk; errors never contain the URL/key. Key
   resolution: `--key` > `AMAP_WEB_KEY` env > `amap_key.txt` beside the script
   (now gitignored, incl. root `.gitignore` entries — never commit the key).
   The old "no web services" rule is superseded by this owner decision.
2. **Guidance engine port** — `PedestrianGuidanceEngine`, stages/thresholds and
   once-per-step semantics identical to the Kotlin original, plus a new
   `prime_progress()` and a maneuver-cue arming of the walked-guard so a
   PROGRESS cue can never immediately repeat a departure/EARLY sentence (this
   redundancy exists in the Kotlin app too — candidate future fix there).
3. **Chinese cue speech** — port of `GuidancePhrases` zh-Hans incl. detail
   levels (简洁/标准/详细), clock directions, landmarks, kerb-stop crossing
   wording; CONFIRM deliberately names the *current* road (SDK vs own-tracker
   semantics differ — documented in code).
4. **RouteTracker** — projects the fused GCJ position onto step polylines,
   advances steps (end-of-step snap or better-match-on-next), computes drift,
   relative bearing → clock, turn angle, remaining distance/time. Replaces the
   AMap Android SDK navigation callbacks.
5. **Voice dialog state machine** — IDLE → CHOOSING (candidates one at a time
   with distance + clock/compass direction) → READY (route summary incl.
   crossing count) → NAVIGATING ↔ PAUSED → ARRIVED. Commands: 带我去X, 确认,
   下一个, 开始, 我在哪里 (regeo + landmark + accuracy + safety line), 还有多远,
   附近的厕所/公交站/地铁站/药店/医院/超市 (single category, 1 km, top-2),
   天气, 暂停/继续/停止, 重复, 状态, 简洁/标准/详细模式, 帮助.
6. **Unprompted announcements** get priorities (critical = act/off-route/
   arrival/hazards, should interrupt TTS; high = prepare/reroute; normal) via
   `pop_announcements()`; JSON replies carry `announcements` (+ legacy joined
   `announcement`); JSON gained `text` and `poll` types. `set_destination`
   coordinates are now explicitly **GCJ-02** and plan+start a route when
   possible.
7. **Off-route → auto reroute**: drift ≥8 m announces; ≥20 m requests a reroute
   (20 s cooldown) on a background worker thread (also does landmark regeo
   prefetch, QPS-spaced 0.35 s); sensor calls never block on HTTP. Landmarks
   attach only within 50 m.
8. **Simulation**: `FakeAmapClient` (canonical 302 m Changsha route exercising
   the real parser, incl. crosswalk step and `[]` quirk) behind `--fake-amap`
   and used by `--demo`/`--selftest`; every simulated user-facing reply is
   prefixed （模拟）. `gcj02_to_wgs84` inverse added for test synthesis.
9. **Fusion tweak for the confirmed hardware**: GPS-course heading threshold
   lowered 1.0 → 0.8 m/s since the common IMU has no compass.
10. **Step counts in cues** (owner request, 27 July): distances ≤40 m near an
    action also speak an approximate pace count ("准备：左转，约45步"; counts
    >20 rounded to nearest 5; suppressed under 4 steps). Uses the same
    configurable step length as dead reckoning (`step_length_m`, default
    0.70 m) so per-user tuning adjusts both.
11. **`--simulate-walk` laptop test mode** (+ optional `--at lat,lon`): seeds a
    starting fix and, once navigation starts, feeds synthetic GPS along the
    *real* planned route at 1.3 m/s on a daemon thread, printing cues live —
    the Pi equivalent of the Android "simulate movement when navigating"
    developer option. Real map data, simulated movement, clearly labelled.
12. **Cadence rework after the owner's live 5 km run** (27 July): progress
    reassurance now fires only on long segments — ~every 250 m, 500 m beyond
    1.2 km, silent below 150 m where the early/prepare/act ladder densifies —
    and every emitted cue arms a full-bucket walked-guard so progress can
    never restate a just-spoken sentence. **The Kotlin app still has the old
    dense 50/20 m buckets — port this scheme back before real outdoor use.**
13. **Segment-entry hazard preview** (owner request, 27 July): CONFIRM cues and
    the departure sentence announce the first mapped attention point
    (crosswalk/stairs/bridge/underpass/…) between here and the next turn,
    within 800 m and beyond the 120 m EARLY window:
    "前方约420米有人行横道，请提前留意。" Unmarked side roads are NOT in AMap
    walking data — explicitly out of scope, stays with cane/backpack sensing.
    Arrival approach wording softened (准备阶段说"即将到达目的地").
14. Verification 27 July 2026: `--selftest` 67/67 PASS (incl. 开始-mid-nav
    handling found in the owner's second live run); `--demo` runs the full
    dialog + guided walk (Early(9点钟, road) → Prepare(landmark, steps) →
    Act(critical) → Confirm(new road) → crossing sequence with kerb stop →
    Arrival with steps; progress cues appear only on long segments) + fusion
    walk ≤2 m error; long-segment cadence + hazard preview verified on a
    synthetic 1.6 km route (~20 announcements over 21 minutes, previews at
    departure and turn confirms); JSON piping smoke-tested. **Live-verified with the owner's real Web Service key** (in
    gitignored `tools/upper_controller/amap_key.txt`): real POI search
    (橘子洲头), real 5 km/19-step walking route, regeo address + landmark,
    nearby accessible toilets, live weather, and real-road cues
    ("前方120米，右转，进入岳华路，在3点钟方向") via --simulate-walk.
    Not yet done: on-Pi test, native-speaker review of all Chinese strings,
    walk_type table verification against live AMap responses (no special
    segments appeared in the sampled route).

## Update 4 — 25 July 2026 (pivot announced — CONFIRMED and built, see Update 5)

Superseded by Update 5, kept for history. The hardware team announced they do
**not** want a phone app: the navigation product should run on their Raspberry
Pi, operated purely by voice, with feature parity. Seven clarifying questions
were sent (Pi-not-phone confirmation; feature-control scope; TTS/STT ownership
— our side wants to stay text-in/text-out; mobile network outdoors;
push-to-talk vs wake word; v0.1 GPS/IMU format confirmation + IMU model/fused
heading/shared clock; command languages). Plan once answered: extend
`tools/upper_controller/sole_precision_core.py` into the full Pi app — AMap
**Web Service** REST client (needs a new Web Service key; the old "no web
services" decision was premised on the phone app and must be revisited by the
owner), port `PedestrianGuidance` + `GuidancePhrases` from Kotlin to Python
(pure logic), voice dialog state machine replacing the screen flow. The
Android app then becomes the reference implementation and indoor testing tool —
do not delete it.

## Update 3 — 25 July 2026 (upper-controller Python handoff)

The hardware team requested a Python script: text in → text out (their TTS
speaks the reply) plus reserved GPS/IMU inputs producing a real-time map
position. Delivered as `tools/upper_controller/sole_precision_core.py`
(stdlib-only, Python 3.8+, `--selftest` 11 checks, `--demo` synthetic walk) with
the proposed v0.1 sensor formats documented in `tools/upper_controller/README.md`.
Key decisions: GPS input is **raw WGS-84** and the script converts to GCJ-02 for
map display (double-conversion is the documented trap); GPS+IMU fusion is a
deliberately simple complementary filter (GPS anchor + step-detection dead
reckoning, heading from fused compass/gyro, ≤2 m error on the synthetic walk);
bilingual (EN/zh) deterministic intent matching with no cloud dependency;
place-name→coordinate resolution stays on the phone (AMap), coordinates arrive
via `set_destination`. Formats are marked PROPOSED and await hardware-team
confirmation — the robotics interface is still not silently invented.


## Read this first

1. The **authoritative Android project** is:
   `/Users/balazsmolnar/StudioProjects/CSUST_AI_VEST_ANDROID`
2. `/Users/balazsmolnar/Documents/CSUST/sole-precision-android` is an **older,
   stale copy**. Do not implement new work there.
3. The stable `0.7` baseline is pushed, but the newest AMap enrichment and audit
   fixes are in an **uncommitted working tree**. Preserve them.
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
| `https://github.com/molnrb/CSUST_AI_VEST_ANDROID` | GitHub remote | Contains the `0.7` baseline; newest AMap/audit work is local |
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
- `HEAD` and `origin/main`: `ef6396a 0.7`
- Earlier commits: `1de7821 Fix Android build with AGP 9.3`,
  `bed8952 Initial Sole Precision Android app`
- The AMap enrichment and 24 July audit fixes are not committed or pushed.

Modified tracked files at handoff:

- `README.md`
- `app/src/main/java/com/csust/soleprecision/MainActivity.kt`
- `app/src/main/java/com/csust/soleprecision/accessibility/ScreenNarrator.kt`
- `app/src/main/java/com/csust/soleprecision/navigation/AmapLocationController.kt`
- `app/src/main/java/com/csust/soleprecision/navigation/AmapManeuverMapper.kt`
- `app/src/main/java/com/csust/soleprecision/navigation/AmapNavigationController.kt`
- `app/src/main/java/com/csust/soleprecision/navigation/AmapPlaceSearchController.kt`
- `app/src/main/java/com/csust/soleprecision/navigation/AmapReverseGeocodeController.kt`
- `app/src/main/java/com/csust/soleprecision/navigation/DestinationHistoryStore.kt`
- `app/src/main/java/com/csust/soleprecision/navigation/Maneuver.kt`
- `app/src/main/java/com/csust/soleprecision/navigation/PlaceCandidate.kt`
- `app/src/main/java/com/csust/soleprecision/navigation/RouteSummary.kt`
- `app/src/main/java/com/csust/soleprecision/navigation/UserLocation.kt`
- `app/src/main/java/com/csust/soleprecision/settings/UserPreferences.kt`
- `app/src/main/java/com/csust/soleprecision/ui/ProductionApp.kt`
- `app/src/test/java/com/csust/soleprecision/navigation/AccessibleNavigationModelTest.kt`
- `docs/AMAP_CAPABILITY_PLAN.md`

Current untracked addition:

- `app/src/main/java/com/csust/soleprecision/navigation/RouteGeometry.kt`

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
- `navigation/RouteGeometry.kt`
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
- AMap location with converted Android GPS fallback; stale, invalid, and
  `(0,0)` fallback fixes are rejected
- inline Android `SpeechRecognizer`
- recognition language selection
- audible microphone start tone
- location-biased AMap input suggestions
- keyword POI search
- exact POI-ID lookup
- request-scoped AMap search/reverse-geocode callbacks so stale asynchronous
  results cannot overwrite newer screens
- one-at-a-time accessible search results
- full-screen native AMap point selection
- reverse geocoding for manually pinned map points
- full POI detail retrieval where AMap supplies it: type, entrance/exit,
  indoor-floor, business tags, and child POIs
- route calculation to the mapped POI entrance when available
- multiple AMap walking-route alternatives and explicit route selection
- route distance, duration, route label, starting compass direction, turn
  count, mapped crossings, traffic lights, level changes, bridges/tunnels, and
  passages
- ordered walking steps with compass orientation and approximate turn angle for
  pre-start mental-map review
- live AMap navigation events
- live remaining walking distance estimated along the active step geometry
- native AMap route voice
- weak-GPS, GPS-off, and off-route/recalculation status
- off-route route-model refresh after AMap recalculation
- destination history
- mock hardware, location, voice results, and route
- real temporary BLE transport
- engineering controls for vibration, speakers, navigation packets, raw packets,
  and stop-all

### What is genuinely usable without the device

With a valid Android AMap key, network, permissions, and supported ARM64 device,
the current app can:

1. accept a typed, spoken, suggested, or map-pinned destination;
2. retrieve and confirm a precise AMap POI;
3. compare available walking routes;
4. explain the selected route before travel as a mental-map walkthrough;
5. show full-screen AMap walking navigation and speak native route guidance;
6. report weak GPS and route recalculation status;
7. generate the temporary outbound navigation/device packets;
8. demonstrate the full flow with deterministic mock location, search, route,
   haptic, and speaker behavior.

It cannot currently see obstacles, verify a crossing, track a physical object,
receive camera/IMU events, or prove that a temporary packet was executed by real
hardware.

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
9. **Route options**
   - one AMap walking alternative at a time
   - announces its mental-map summary
   - right: Choose route
   - left: Next alternative, when more than one exists
   - down: Back
10. **Route preview**
   - destination, route distance, and duration
   - right: Start
   - left: Decline
   - up: Review complete route, when AMap returned steps
   - down: Back
11. **Route walkthrough**
    - one ordered AMap walking step at a time
    - maneuver, distance, road name, orientation, approximate turn angle, and
      mapped traffic-light context
    - right: Next
    - left: Previous
    - down: Route preview
12. **Active navigation**
    - full-screen native `AMapNaviView`
    - floating Repeat and Pause controls
13. **Paused**
    - route map remains visible
    - floating Continue and End controls
    - route output is paused; the AMap engine may retain route position so it
      can resume
    - local obstacle detection is a future independent device behavior, not a
      current phone capability
14. **Arrival**
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
- AMap route ID and label
- initial compass direction
- complete route geometry

Each `WalkingRouteStep` contains:

- mapped maneuver
- distance
- duration
- road name
- mapped traffic-light count
- compass orientation
- approximate turn angle
- step geometry
- generated spoken instruction

AMap icon types are mapped to the app's maneuver vocabulary, including
crosswalk, stairs, elevator, escalator, ramp, overpass, underpass, bridge,
tunnel, subway passage, pedestrian facility, building, ferry, and roundabout
where supplied.

### Live guidance

`NaviInfo` is converted to:

- maneuver
- metres remaining along the matched active walking step when usable geometry
  and location are available
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

The owner explicitly decided that there will be **no project backend**. Use the
native Android AMap SDK bundle for the real-time on-phone flow. Do not add Web
APIs merely to duplicate location, POI search, maps, geocoding, or walking
navigation already provided by the SDK.

Do not embed a Web Service key in the APK. AMap Web Services are therefore
outside the current architecture unless the owner later changes the no-backend
decision and provides a secure server-side design. Trajectory correction is not
an immediate pedestrian-safety tool. Add nearby-category search only for a
focused user request such as an entrance, toilet, or bus stop; never announce
all nearby POIs.

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

### Planned app capabilities after real device integration

These are the intended product capabilities, not claims about the current
build:

| Capability | Expected integrated behavior |
| --- | --- |
| Device setup | Discover/pair the upper controller, show connection, firmware, battery, camera/IMU/GPS health, and calibration state |
| Outdoor route guidance | Continue using native AMap walking routes, route alternatives, route preview, turn countdown, rerouting, and arrival |
| Immediate obstacle guidance | Receive local obstacle events and issue short left/right/both haptic or audio cues without waiting for Android, AMap, network, or cloud inference |
| Priority arbitration | Enforce emergency/stop and hardware-fault cues above obstacle cues, obstacle cues above route turns, and turns above optional context |
| Target finding | Let the user request an object or destination feature, send the target request to the upper controller, announce lock/lost/reacquired state, and guide toward its direction/range |
| Precise object information | Announce the locked object's class and requested attributes only, with confidence/failure language instead of continuous scene narration |
| Sensor-aware confidence | Combine device-provided camera geometry, IMU pose, and device GPS/fused position with phone route context; expose degraded confidence explicitly |
| Personalized outputs | Apply the chosen guidance mode, vibration intensity, speaker volume, speech detail, and language to real device commands rather than only engineering tests |
| Pause and emergency control | Pause ordinary route output, stop all noncritical outputs, and expose a physical device cutoff; phone software must not be the only emergency control |
| Diagnostics | Provide an accessible device-health and test screen for each camera, speaker, vibration motor, IMU, GPS, link, battery, and protocol version |
| Privacy | Keep immediate vision processing local; make any optional cloud semantic request explicit, noncritical, and governed by consent/retention rules |

The integrated experience should merge two distinct streams without confusing
them:

1. **AMap route intent** — where the user should travel next.
2. **Local device safety perception** — what is physically in the user's
   immediate path now.

The app may coordinate and present both streams, but AMap must never be described
as detecting an obstacle and the vision system must never turn uncertain camera
output into permission to cross.

### Minimum interface the robotics team must deliver

The implementation can preserve its `WearableTransport` boundary if the device
team supplies a versioned bidirectional contract with:

- transport choice and discovery/pairing procedure;
- device identity, firmware/protocol version, capabilities, and MTU/frame limit;
- heartbeat, acknowledgement, retry, timeout, reconnect, and duplicate-command
  rules;
- monotonic timestamp and sequence semantics;
- device health events for battery, cameras, IMU, GPS, compute temperature,
  lower-controller link, speakers, and vibration motors;
- pose schema: latitude/longitude when present, heading, speed, accuracy,
  coordinate system, IMU orientation, and whether the pose is raw or fused;
- obstacle schema: stable track ID, class, range, bearing or left/center/right,
  relative motion/time-to-collision, height/ground-zone, confidence, timestamp,
  and severity;
- target schema: request ID, desired class/attributes, candidate list,
  selected/locked/lost/reacquired state, range, bearing, confidence, and cancel;
- command schema for left/right/both vibration and audio, strength/volume,
  pattern/cue, duration, expiry, priority, and interruptibility;
- explicit emergency-stop/clear behavior and which safety functions remain
  autonomous when the phone disconnects;
- ownership of speech synthesis and the vocabulary/clip IDs if speech is
  generated on the device;
- camera calibration and coordinate-frame definitions;
- recorded test fixtures or a simulator that emits the exact production event
  stream before physical hardware is available.

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

Verification on 24 July 2026 after the code audit:

- `./gradlew check assembleDebug` — passed
- unit tests — passed
- Android lint — passed
- `git diff --check` — passed
- debug APK install on API 36 ARM64 emulator — passed
- cold/warm `MainActivity` launch — passed; process remained active

Debug APK:
`/Users/balazsmolnar/StudioProjects/CSUST_AI_VEST_ANDROID/app/build/outputs/apk/debug/app-debug.apk`

---

## 11. Existing automated tests

Current unit-test areas:

- route distance and duration formatting
- route mental-map complexity summary
- POI entrance preference and rich accessibility detail
- route compass direction, turn angle, and remaining step geometry
- coordinate validity and stale-location rejection
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

### Documentation state

The 24 July audit removed an accidentally appended copy of this handoff from the
active repo's `README.md` and updated its old large-button/swipe-toggle text.
`ProductionApp.kt`, `GestureNavigation.kt`, the cleaned README, and this handoff
now agree that swipe-only menus are the current experiment and maps use native
gestures plus floating controls.

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
- native AMap map/navigation-view lifecycle across repeated background/foreground
  transitions needs instrumentation testing
- mock navigation emits only a static demonstration instruction; it is not a
  progressing route simulator
- guidance mode, vibration strength, speaker volume, and speech detail are not
  yet end-to-end production behavior because the real device contract does not
  exist
- the Android pause state suppresses ordinary output/voice while retaining
  navigation state; it does not stop future autonomous device safety processing

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
3. Keep the stale CSUST Android copy clearly marked or remove it only with the
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
