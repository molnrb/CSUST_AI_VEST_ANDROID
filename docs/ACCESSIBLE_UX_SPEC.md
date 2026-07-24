# Accessible navigation UX specification

Status: first implemented production-flow baseline.

## Interaction hierarchy

1. Home contains only Navigation and Settings.
2. Navigation offers spoken destination, recent destinations and typed fallback.
3. AMap results are presented as individually labelled place candidates.
4. The user confirms the full place description before route calculation.
5. Route preview reports destination, distance, duration and readiness.
6. Active navigation exposes only repeat, route status and pause.
7. End navigation is behind the paused screen to reduce accidental cancellation.

## Accessibility rules

- Use native Material controls and Compose semantics.
- Give every screen a unique pane title and visible heading, except the deliberate
  two-button home screen.
- When TalkBack touch exploration is off, announce each newly opened screen once
  with its title, main button names and spatial order.
- When TalkBack touch exploration is on, suppress application screen narration so
  it does not compete with TalkBack. Do not duplicate routine focus speech.
- Use live regions only for important search, route and navigation changes.
- Keep primary touch regions at least 96 dp high. The platform minimum is 48 dp.
- Preserve logical top-to-bottom focus order.
- Keep the large-button layout as the default while the directional-swipe concept
  is evaluated with participants.
- Do not use icon-only actions, color-only state, dragging-only controls or hidden
  gesture requirements.
- Support Android font and display scaling without losing actions.
- Keep button names understandable when heard without surrounding visual context.
- Keep destructive actions separate and clearly labelled.

The optional swipe-only experiment deliberately does not satisfy the preceding
gesture-independence rules and must not be used as the TalkBack interface. Its
direction zones are visual instructions rather than buttons. All navigation
actions require a drag gesture. The entire screen follows the finger on one
locked horizontal or vertical axis and never rotates or fades. Down consistently
means Back, and confirmation screens use right for Confirm and left for Decline.
No experimental screen may expose more than four directions; use another screen
when more choices are needed.

## Guidance priority

1. Immediate local obstacle/stop warning
2. Hardware, connection, GPS or route failure
3. Turn instruction
4. Route progress
5. Optional context and landmarks

Local obstacle detection must keep running when phone guidance is paused or fails.
The app is route guidance and does not replace a cane, guide dog, or orientation
and mobility skills.

## Spoken interaction

- Destination recognition is user-initiated and not always listening.
- Recognition uses the system Android service.
- A typed destination is always available.
- Search results include place, address and administrative area.
- In the experimental directional layout, present one result at a time and speak
  its list position, name, address and straight-line distance. Left declines and
  advances, right confirms and down goes back.
- A sighted helper may point to a destination on AMap; the app must still require
  explicit destination confirmation.
- Navigation begins only after explicit place confirmation and route preview.
- AMap built-in speech handles route instructions.
- Screen introductions are enabled by default, remain user-configurable, and are
  automatically suppressed while TalkBack touch exploration is active.

## Manual test matrix

Before participant use, complete each flow with:

- TalkBack enabled
- TalkBack disabled
- 200% font size
- Largest practical display size
- High-contrast dark theme
- Portrait and landscape orientation
- Bluetooth connected and disconnected
- Location granted, denied and temporarily unavailable
- Network available and unavailable
- Speech recognition available and unavailable
- Empty, ambiguous and successful POI searches
- Route calculation failure and off-route recalculation
- Backpack low battery or lost connection

Test with Chinese blind and low-vision participants and an orientation-and-mobility
specialist before finalizing cue vocabulary, vibration patterns, speech density or
any safety-related setting.
