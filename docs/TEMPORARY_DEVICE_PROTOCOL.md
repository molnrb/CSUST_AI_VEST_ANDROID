# Temporary command protocol

Status: engineering placeholder, version 1.

This contract exists so the Android and robotics teams can test independently.
The intended lower-level ESP32-WROOM-32 connects to the upper controller over
USB-to-TTL. The production Android-to-upper-controller transport is still open.
Therefore the BLE endpoint below is only a temporary Android test transport; it
must not be treated as the final physical architecture.

Replace it when the hardware team supplies the upper-controller transport and
message interface. The Android simulator implements the same command boundary
without BLE, an upper controller or an ESP32.

## Temporary BLE endpoint

- Service UUID: `5c10a001-9c1b-4c7f-9c6a-43d42f2d1000`
- Writable command characteristic:
  `5c10a002-9c1b-4c7f-9c6a-43d42f2d1000`
- Maximum raw packet accepted by the test screen: 20 bytes
- Structured multi-byte values are little-endian

The Android app uses write-without-response when the characteristic supports it;
otherwise it uses a normal write.

## Shared 12-byte frame

| Byte | Meaning |
|---:|---|
| 0 | Magic `0x53` (`S`) |
| 1 | Protocol version `0x01` |
| 2 | Packet type |
| 3–4 | Unsigned sequence number, little-endian |
| 5–10 | Type-specific payload |
| 11 | XOR of bytes 0–10 |

Packet types:

| Value | Command |
|---:|---|
| `0x01` | Navigation |
| `0x10` | Vibration |
| `0x11` | Speaker/audio cue |
| `0x12` | Stop all device outputs |

The receiver should reject a structured packet with the wrong size, magic,
version or checksum. Sequence numbers wrap after `65535`.

## Side mask

| Value | Target |
|---:|---|
| `0x01` | Left |
| `0x02` | Right |
| `0x03` | Both |

## Vibration packet (`0x10`)

| Byte | Meaning |
|---:|---|
| 5 | Side mask |
| 6 | Intensity, `0–100` percent |
| 7 | Pattern code |
| 8–9 | Duration, `10–10000` ms |
| 10 | Repeat count, `1–10` |

Pattern codes:

| Value | Pattern |
|---:|---|
| `0` | Continuous |
| `1` | Pulse |
| `2` | Double pulse |
| `3` | Triple pulse |

Example: both motors, 70%, pulse, 500 ms, once:

`53 01 10 00 00 03 46 01 F4 01 01 F2`

## Speaker cue packet (`0x11`)

| Byte | Meaning |
|---:|---|
| 5 | Side mask |
| 6 | Cue code |
| 7 | Volume, `0–100` percent |
| 8 | Repeat count, `1–10` |
| 9–10 | Reserved, send `0` |

Cue codes:

| Value | Cue |
|---:|---|
| `1` | Test tone |
| `2` | Turn left |
| `3` | Turn right |
| `4` | Go straight |
| `5` | Obstacle |
| `6` | Stop |
| `7` | Arrived |

The packet carries only a cue ID, not audio samples or text. Firmware should map
each cue to its local sound or voice clip.

Example: both speakers, test tone, 70%, once:

`53 01 11 00 00 03 01 46 01 00 00 06`

## Stop-all packet (`0x12`)

| Byte | Meaning |
|---:|---|
| 5 | Both-side mask `0x03` |
| 6–10 | Reserved, send `0` |

This asks firmware to stop active motors and speaker output immediately. Firmware
should make this command idempotent. A physical emergency cutoff is still needed
for hardware safety.

Example:

`53 01 12 00 00 03 00 00 00 00 00 43`

## Navigation packet (`0x01`)

| Byte | Meaning |
|---:|---|
| 5 | Maneuver code |
| 6–7 | Distance in metres |
| 8–9 | Time-to-live in 100 ms units |
| 10 | Source: `1` AMap, `2` manual test |

The full maneuver code list is defined in
`app/src/main/java/com/csust/soleprecision/navigation/Maneuver.kt`.
Firmware should discard expired navigation guidance. Immediate obstacle warnings
must be calculated locally and override phone navigation.

## Raw mode

The raw sender transmits exactly the 1–20 bytes typed into the test screen. It
does not add a header, sequence number or checksum. It is an engineering escape
hatch for experimenting with the hardware team's future interface.
