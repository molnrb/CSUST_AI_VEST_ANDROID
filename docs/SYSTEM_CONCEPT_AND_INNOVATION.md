# Sole Precision system concept and innovation brief

Status: saved project context, July 2026. This document records the intended
system and the design analysis behind it. Interfaces that the robotics team has
not finalized are marked as open rather than assumed.

## Intended system architecture

### Upper-level controller

The upper-level controller connects:

- two forward-facing monocular cameras;
- one GPS receiver;
- the software that performs navigation, computer vision, target locking and
  cloud semantic interaction; and
- the lower-level ESP32 controller through a USB-to-TTL serial link.

Its planned responsibilities are:

1. Use AMap/Gaode navigation data, GPS and inertial information for basic outdoor
   route guidance.
2. Run the planned YOLO26 detector over both camera feeds to recognize indoor
   objects and obstacles.
3. Use pseudo-binocular geometry to estimate relative position and focus on a
   user-selected central target.
4. Maintain the target lock so visually similar nearby objects do not cause the
   system to switch identity.
5. Filter observations and send only actionable information.
6. Use a cloud large model for semantic command understanding, conversational
   interaction and reading or explaining information about a locked object.
7. Translate navigation, obstacle and semantic decisions into compact commands
   for the lower-level controller.

### Lower-level controller

The lower-level controller is an ESP32-WROOM-32 connected to:

- two speaker modules;
- two vibration modules; and
- an IMU/gyroscope.

It receives commands from the upper-level controller over USB-to-TTL and produces
left, right or bilateral speech/audio and vibration feedback. Immediate
obstacle/safety cues must have higher priority than ordinary route guidance.

### Android application's boundary

The Android app is the accessible user interface and current AMap prototype. It
handles destination input, confirmation, route state, accessible settings and
screen narration.

The production link between Android and the upper-level controller has not yet
been specified. The present BLE transport and 12-byte command frames are
engineering placeholders, not a claim that Android will connect directly to the
ESP32. When the robotics team supplies the upper-controller API, it should replace
the transport implementation without changing the accessible navigation flow.

For software-only development, the app includes a simulated system that stands in
for the upper controller, USB-to-TTL connection, ESP32, speakers and vibration
motors. It consumes the same app commands, prepares the same temporary packets and
reports the action that the hardware would have performed.

On Android Emulator it also provides a clearly labelled simulated GPS position
and demo route. The demo route exists only to exercise the interface and command
flow; its distance and instructions are not derived from real streets and must
never be presented as safe travel guidance.

Simulator mode also provides deterministic mock voice-recognition results so the
spoken-destination, candidate-selection and navigation flows can be evaluated
without an installed recognizer or network request.

## End-to-end information flow

1. The user gives a destination, target or semantic command.
2. AMap/GPS/IMU provide outdoor route and motion context.
3. The two cameras and detector provide object and obstacle candidates.
4. Information filtering removes irrelevant observations.
5. Pseudo-binocular focusing and target locking select the intended object.
6. Local logic handles time-critical obstacle warnings.
7. Cloud semantic recognition handles non-time-critical interpretation and
   interaction about a locked object.
8. The upper controller sends prioritized output commands over USB-to-TTL.
9. The ESP32 activates the appropriate left/right speakers and vibration motors.

Cloud availability must never be required for immediate obstacle avoidance.

## Innovation-canvas analysis

The needs analysis examined six concern areas:

| Concern | Representative pain points | Selected response |
|---|---|---|
| Static spatial distribution | Understanding indoor layouts and shelf arrangements | Convey indoor maps through nonvisual spatial representations and map feedback |
| Dynamic safety | Avoiding people and obstacles indoors and while crossing | High-precision obstacle avoidance plus strict information filtering |
| Accurate item categorization | Household-product attributes, meal preparation and code scanning | Marking/reminders, locked identification and contextual overview |
| Event judgment | Whether cooking is complete or cleaning is thorough | Convert abstract degrees into measurable states, thresholds and time |
| External target identification | Ground hazards, road condition, road obstructions, entrances and target vehicles | Create an explicit target relationship, then identify and localize it accurately |
| Current-phase deficiencies | Overall route planning, navigation and turning angles | Add and refine missing navigation functions |

Root causes were examined through six interfaces: people, objects, information,
mechanisms, time and location. Twelve candidate solutions were rated for
importance, feasibility and innovation.

The retained solution directions were:

1. alternative nonvisual communication of indoor maps;
2. high-precision obstacle avoidance;
3. information filtering and screening;
4. lock-on recognition;
5. a comprehensive or “god's-eye” spatial representation; and
6. map-based feedback that communicates object and layout relationships.

## Two minimum viable directions

### MVP A — precision lock and obstacle assistance

Primary value: uniqueness and precision.

- Identify and lock the intended target.
- Reduce switching between visually similar objects.
- Estimate the target's relative position.
- Detect immediate obstacles.
- Deliver only actionable left/right voice and vibration cues.

This is the selected first product category (“sole precision”) and should remain
the first implementation priority.

### MVP B — comprehensive spatial observation

Primary value: broad environmental understanding.

- Build a persistent spatial overview.
- Convey indoor maps and relative object positions.
- Track where objects move.
- Filter information to avoid cognitive overload.
- Support a nonvisual “god's-eye” understanding of the environment.

This direction is broader and should follow the precision MVP rather than delay it.

## Design constraints

- The system supplements, and does not replace, a cane, guide dog or trained
  orientation-and-mobility technique.
- Obstacle avoidance is local and time-critical; cloud interpretation is not.
- Feedback must be concise and prioritized to avoid masking environmental sound.
- Camera recognition confidence is not the same as physical safety.
- GPS does not provide reliable indoor positioning.
- The pseudo-binocular approach requires calibration and validation; two
  monocular views do not automatically provide accurate depth.
- User studies with blind and low-vision participants are required before fixing
  cue vocabulary, information density or vibration mappings.

## Open interfaces

The robotics team still needs to specify:

- Android-to-upper-controller transport (for example BLE, Wi-Fi or USB);
- message framing, acknowledgement, heartbeat and reconnect behavior;
- coordinate frames and camera calibration;
- GPS/IMU ownership and fused-pose format;
- object, obstacle, target-lock and confidence event schemas;
- priority and interruption rules for navigation versus obstacle warnings;
- whether speech is synthesized on the upper controller, Android device or lower
  speaker modules; and
- cloud request/privacy boundaries and offline fallback behavior.
