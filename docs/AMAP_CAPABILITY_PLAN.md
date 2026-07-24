# AMap capability and integration plan

Reviewed against the official AMap documentation on 24 July 2026.

## What is integrated now

The app already bundles the native Android Navigation, 3D Map, Location and
Search SDKs:

- Navigation SDK `11.2.000`
- 3D Map SDK `11.2.000`
- Location SDK `11.2.000`
- Search SDK `9.8.0`

The current implementation uses them for:

- continuous high-accuracy positioning with accuracy/source confidence and a
  converted Android-GPS fallback;
- location-biased input tips, POI search, exact POI-ID lookup and individually
  presented candidates;
- full POI detail requests, including AMap category, mapped entrance/exit,
  indoor floor, business tags and child POIs when the dataset supplies them;
- typed AMap suggestions pinned directly on the full-screen map, with manual
  map-point selection available in the same screen;
- POI-ID and mapped-entrance walking-route calculation and simulated or GPS
  navigation;
- native multi-route walking calculation, exact AMap route-ID selection and
  one-route-at-a-time accessible comparison;
- a pre-start route walkthrough built from ordered AMap walking steps, including
  manoeuvre, distance, road name, compass orientation, approximate turn angle
  and mapped traffic-light count;
- an on-device mental-map summary with initial direction, turn count,
  crosswalk count, traffic-light count, level changes and bridge/passage
  complexity;
- live navigation callbacks converted into wearable direction packets;
- metres to the next action calculated from the matched walking position and
  route geometry rather than the SDK's driving-only step-distance field;
- mapped crosswalk, stairs, elevator, escalator, ramp, bridge, tunnel,
  overpass, underpass, pedestrian-way, building entry/exit, subway-passage,
  ferry and roundabout manoeuvres exposed by the walking route;
- weak-GPS, GPS-disabled and off-route recalculation status;
- native AMap voice announcements;
- native map pan/zoom, current-location controls, indoor-map support, map-point
  selection and reverse geocoding to a nearby POI/address.
- native full-screen `AMapNaviView` guidance UI with route overlays, auto-zoom,
  traffic, compass, lane/crossing information and overview controls.

Point selection now always uses the native AMap renderer. Use an Android 16/API
36 ARM64 emulator or a physical ARM64 phone. The AMap 11.2 renderer failed to
create an OpenGL context on the tested Android 17/API 37.1 16-KB preview image.

The app uses directional swipes for its own menus. Map screens are an intentional
exception: AMap receives all pan, zoom and tap gestures, while accessible Back,
Clear/Repeat and Confirm/Pause actions float over the bottom of the full-screen
SDK view.

## Recommended next integrations

| Priority | User need | AMap capability | Decision |
| --- | --- | --- | --- |
| 1 | Resolve ambiguous spoken or typed places | Search SDK input tips, POI keyword search and exact POI lookup | Integrated with location-biased suggestions and one accessible result at a time |
| 2 | Route to the correct entrance of a large place | Rich POI NAVI fields and POI-based walking route requests | Integrated; mapped entrance and POI ID are preferred with POI coordinates as fallback |
| 3 | Detect unreliable outdoor guidance | Continuous Location SDK updates, accuracy, location type and failure codes | Integrated in the app status model |
| 4 | Recover when the user leaves the route | Navigation SDK GPS, off-route and recalculation callbacks | Integrated as concise guidance status events |
| 5 | Let a helper choose a visible point | Native map, markers and Search SDK reverse geocoding | Integrated now |
| 6 | Find useful nearby categories | POI around-search | Add only for focused requests such as entrances, bus stops or toilets; do not continuously announce everything |
| 7 | Analyze routes on a server | Web Route Planning 2.0 | Out of scope: this project has no backend and does not embed Web Service keys |
| 8 | Normalize raw GPS coordinates | Android SDK coordinate conversion | Integrated for the system-GPS fallback |
| 9 | Correct uploaded vehicle traces | Web trajectory correction | Do not use for immediate pedestrian safety; it is not an obstacle-avoidance system |

AMap's map/search stack can display indoor and outdoor maps, search POIs, draw
markers and routes, and perform geocoding. Coverage-dependent indoor maps may
help a user form a spatial overview, but they cannot replace the cameras, IMU
and local obstacle model. AMap provides route context; the backpack remains the
authority for immediate hazards.

## Blind-friendly walking guidance layers

1. **Route layer — AMap:** current road, next manoeuvre, metres to the action,
   remaining route, mapped crosswalk/stairs/elevator instructions and mapped
   traffic lights.
2. **Immediate-safety layer — backpack:** live obstacle class, distance, height
   and left/centre/right direction from the cameras and local model.
3. **Decision layer — user:** crossing information is never phrased as
   permission to cross. The system should align the user with the crossing,
   describe the mapped context, report current camera observations and ask the
   user to verify traffic or a trusted audible signal.

Recommended cue cadence is a concise early warning, a preparation cue near the
action and a final action cue. Local obstacle cues always interrupt route
speech. Repeated map updates should be filtered into distance buckets so the
system does not speak continuously.

## SDK versus Web Service

Use the Android SDKs for the real-time app flow. They already provide location,
POI search, map interaction and walking navigation with native callbacks.

This project intentionally has no backend. Web Route Planning 2.0, Web
geocoding, coordinate conversion and trajectory correction are therefore not
used: their Web Service key must not be embedded in the APK. The Android app
uses only the signed native SDK integration and keeps route analysis on-device.
The Android key remains restricted to the application ID and signing
certificates.

## Current boundary

The on-device app can describe only what AMap maps. It cannot know whether a
temporary barrier, parked scooter, construction zone, crowd, open drain, silent
vehicle or signal state is present. A mapped crosswalk is not permission to
cross. Every route summary and special-feature instruction therefore uses
“mapped” language and asks the user to confirm the real environment.

No new wearable or robotics interface is part of this implementation. Existing
mock/BLE engineering code remains isolated for later team testing.

## Official references

- [Android Map SDK overview](https://lbs.amap.com/api/android-sdk/summary)
- [Android SDK downloads and versions](https://lbs.amap.com/api/android-sdk/download/)
- [Android reverse geocoding](https://lbs.amap.com/api/android-sdk/guide/map-data/geo/)
- [Android location guide](https://lbs.amap.com/api/android-location-sdk/guide/android-location/getlocation)
- [Android GPS and simulated navigation](https://lbs.amap.com/api/android-navi-sdk/guide/navigation-map/gps-navi)
- [Independent route calculation](https://lbs.amap.com/api/android-navi-sdk/guide/route-plan/independentcalculateroute)
- [Web Route Planning 2.0](https://lbs.amap.com/api/webservice/guide/api/newroute)
- [Web geocoding and reverse geocoding](https://lbs.amap.com/api/webservice/guide/api/georegeo/)
- [AMap API capability index](https://lbs.amap.com/api/)
