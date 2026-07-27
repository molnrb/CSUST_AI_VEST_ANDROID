# Sole Precision — Raspberry Pi voice-navigation core

`sole_precision_core.py` is the single-file Python implementation of the
voice-operated walking-navigation product for the Raspberry Pi, per the
hardware team's confirmations of 27 July 2026: software runs on the Pi (no
phone app), **navigation only** (no device feature control), STT/TTS and
command triggering belong to the hardware team, network comes from a
pre-connected hotspot, and **product voice commands are Chinese only**.
Pure standard library, Python 3.8+, no pip installs.

```bash
python3 sole_precision_core.py --selftest       # 67 internal checks / 自检
python3 sole_precision_core.py --demo           # full simulated run / 完整模拟演示
python3 sole_precision_core.py --fake-amap      # interactive, simulated map data / 模拟数据交互
python3 sole_precision_core.py                  # interactive, real AMap when a key is configured
python3 sole_precision_core.py --simulate-walk  # laptop testing: real map data, simulated walking
```

### Testing on a laptop (no GNSS receiver) / 电脑上测试（无接收机）

With the key configured, `--simulate-walk` seeds one starting fix (default
central Changsha; override with `--at <lat,lon>` in WGS-84) and, once you say
开始, feeds synthetic GPS along the *real* planned route at 1.3 m/s, printing
every cue live. This mirrors the Android app's "simulate movement when
navigating" option — real AMap data, simulated movement only:

```bash
python3 sole_precision_core.py --simulate-walk
# then type: 带我去橘子洲头   → 确认   → 开始   and watch the cues stream
```

Verified live on 27 July 2026 with a real key: POI search, a 5 km / 19-step
walking route, reverse-geocoded address + landmark, nearby accessible toilets,
live weather, and staged cues with real road names and clock directions.

## 0. 中文速览（给硬件组）

- 单文件、纯标准库、Python 3.8+：树莓派系统自带的 Python 直接运行，**无需安装任何包**。
- **对话**：`process_text("带我去岳麓山")` → 返回要朗读的句子。完整流程：
  搜索地点 → 说"确认"规划步行路线 → 说"开始"出发 → 沿途自动播报 → 到达。
  语音识别、语音合成、指令触发方式都由你们负责；本脚本只做文字进、文字出。
- **定位**：`update_gps()` / `update_imu()` 喂入传感器数据，`current_position()`
  返回融合位置（WGS-84 与高德 GCJ-02 同时给出）。
- **主动播报**：导航中位置更新会产生分级提示（提前预告/准备/立即执行/转弯确认/
  进度/偏离路线/即将到达）。用 `pop_announcements()` 读取，每条带优先级：
  **critical 建议打断当前正在播放的语音**，high 优先排队，normal 正常排队。
  导航时请约每秒轮询一次（JSON 模式下从回复的 `announcements` 字段读取）。
- **高德 Web 服务 key**（与安卓 SDK key 不是同一个）三选一：
  环境变量 `AMAP_WEB_KEY`；命令行 `--key <key>`；脚本旁的 `amap_key.txt` 文件。
  key 切勿提交到 git（`amap_key.txt` 已被忽略）。个人 key 有每日配额和并发限流，
  脚本已控制请求频率；建议在高德控制台为 key 配置 IP 白名单。
- 两个关键约定：
  1. GPS 输入必须是接收机**原始的 WGS-84 坐标**（NMEA 解析结果即是），切勿预先加偏；
  2. GPS 与 IMU 的时间戳必须来自**同一个时钟**（建议 `time.monotonic()`，你们说"应该
     可以保持一致（待调试）"——联调时务必验证这一点）。
- 已确认硬件：Hiwonder GPS Module V1.0（北斗+GPS，有源天线）→ NMEA 输出，正合适；
  常见 IMU（无融合罗盘航向）→ 室外航向以 GPS 航迹为主，脚本已按此调整。
- 本脚本只提供路线信息，不检测障碍物；背包本地避障永远优先，
  地图数据不能证明过街是安全的（过街提示会明确要求停下自行确认）。

## 1. AMap Web Service key

Get a **Web 服务** key from the AMap console (the Android SDK key does not
work for REST calls). Provide it one of three ways, highest priority first:

1. `--key <key>` on the command line;
2. environment variable `AMAP_WEB_KEY`;
3. a file `amap_key.txt` next to the script (gitignored — never commit it).

Without a key everything still runs; the dialog explains that search is
unavailable. `--fake-amap` uses built-in simulated Changsha data; every
simulated reply is prefixed 「（模拟）」 so mock data can never impersonate the
real map. Personal-tier keys have daily quotas and ~3 QPS limits; the script
spaces background requests and caches reverse geocodes. Consider an IP
allowlist for the key in the AMap console.

## 2. Voice commands (Chinese product interface)

| 指令 | 作用 |
|---|---|
| 带我去/我要去/导航到 + 地名 | 搜索地点，逐个播报候选（名称、地址、距离、方向） |
| 确认 / 下一个 | 选定当前候选并规划路线 / 听下一个候选 |
| 开始 / 出发 | 沿已规划路线出发，开始分级播报 |
| 我在哪里 | 逆地理编码地址 + 最近地标 + 定位精度 + 安全提醒 |
| 还有多远 | 剩余路程与预计时间 |
| 附近的厕所/公交站/地铁站/药店/医院/超市 | 单类别周边搜索（1 公里内，最近两个，含方向） |
| 天气 | 当前区县实时天气 |
| 暂停 / 继续 / 停止 | 控制导航 |
| 重复 | 重复上一条引导提示（或上一句回复） |
| 状态 | 对话状态、GPS 状态、剩余路程、地图服务状态 |
| 简洁模式 / 标准模式 / 详细模式 | 播报详细程度（简洁只留准备/执行/危险提示） |
| 帮助 | 播报指令列表 |

English keywords are still recognised for engineering tests, but every reply
is Chinese — the product interface is Chinese only.

## 3. Sensor input formats — v0.1, hardware confirmed 2026-07-27

Confirmed hardware: **Hiwonder GPS Module V1.0** (BeiDou+GPS, active antenna,
NMEA output — parse `RMC`/`GGA`, coordinates are already WGS-84) and a common
accel/gyro IMU **without a fused compass heading** — so outdoor heading comes
mainly from GPS course over ground; the fusion is tuned accordingly.
Timestamps: one shared clock for both streams (team: "should be consistent,
to be debugged" — please verify during integration).

| GPS field (~1 Hz) | Type | Notes |
|---|---|---|
| `timestamp` | float | seconds, shared clock |
| `latitude`, `longitude` | float | **WGS-84 degrees, raw GNSS output — do not pre-convert** |
| `accuracy_m` | float, optional | 1-sigma horizontal accuracy |
| `speed_mps` | float, optional | ground speed |
| `bearing_deg` | float, optional | course over ground, 0–360, 0 = north — **send it; it is the main heading source** |
| `satellites` | int, optional | used in fix |

| IMU field (20–100 Hz) | Type | Notes |
|---|---|---|
| `timestamp` | float | seconds, shared clock |
| `accel` | [x, y, z] float | m/s², device frame, gravity included |
| `gyro` | [x, y, z] float, optional | rad/s (used for heading between GPS fixes) |
| `heading_deg` | float, optional | only if your IMU ever provides a fused compass heading |

| Position output | Meaning |
|---|---|
| `latitude`, `longitude` | fused position, WGS-84 |
| `gcj_latitude`, `gcj_longitude` | same point in GCJ-02 — where it sits on an AMap map |
| `heading_deg`, `speed_mps`, `moving` | motion state |
| `source` | `gps` \| `gps+imu` \| `imu-dead-reckoning` |
| `horizontal_accuracy_m` | fix accuracy + drift allowance while dead reckoning |
| `gps_age_s` | seconds since the last accepted fix |

## 4. Guidance cues (unprompted speech during navigation)

Position updates drive the staged pedestrian engine ported from the Android
app (verified live on the emulator): **EARLY** (~120 m before a maneuver),
**PREPARE** (~30 m), **ACT** (≤8 m; ≤12 m for crossings and other hazards),
**CONFIRM** (after the turn: the new road, its length, and a **hazard
preview** — the first mapped crossing/stairs/bridge before the next turn:
"前方约420米有人行横道，请提前留意"; the departure sentence gets the same
preview), **PROGRESS** (long-segment reassurance only: roughly every 250 m,
every 500 m beyond 1.2 km — about one cue per three walking minutes; below
150 m the early/prepare/act ladder densifies naturally and progress goes
silent), **OFF_ROUTE** (≥8 m perpendicular drift; ≥20 m also triggers an
automatic reroute), **ARRIVAL** (≤25 m).

Honest limit: previews cover **mapped special segments** only. AMap's walking
data does not enumerate unmarked side roads crossing the path, so detecting
those remains the job of the cane and the backpack's local obstacle sensing —
never this script.
Each stage speaks at most once per step. Cues carry clock-face directions
("在9点钟方向"), the road being entered, a nearby landmark, and explicit
stop-and-verify wording at crossings. Close to an action (≤40 m), distances
also include an approximate **step count** ("准备：左转，约45步") — the pace
convention blind travellers train with. Counts over 20 are rounded to the
nearest 5, and they use the same configurable step length as the dead
reckoning (0.70 m default — tune both at once per user via
`SolePrecisionCore(step_length_m=...)`).

Read cues with `pop_announcements()` → `[{"priority", "text"}]`, oldest
first. Priorities: `critical` (act-now, off-route, arrival, hazards — should
interrupt running TTS), `high` (prepare, reroute status), `normal`
(everything else). Poll at ~1 Hz while navigating. `pop_pending_announcement()`
returns the same content joined into one string (back-compat).

## 5. JSON line protocol (interactive mode, or any pipe/socket)

```json
{"type":"gps","timestamp":10.0,"latitude":28.2282,"longitude":112.9388,"accuracy_m":8.0,"speed_mps":1.3,"bearing_deg":270.0}
{"type":"imu","timestamp":10.02,"accel":[0.3,0.1,11.2],"gyro":[0.0,0.0,0.01]}
{"type":"text","text":"带我去岳麓山"}
{"type":"set_destination","name":"湖南大学","latitude":28.1817,"longitude":112.9443}
{"type":"get_position"}
{"type":"poll"}
```

Every line except `imu` prints one JSON reply containing `position`, plus
`reply` for `text` lines and `announcements`/`announcement` whenever cues are
queued. `imu` lines are silent (high rate). Plain non-JSON lines are treated
as spoken text. `set_destination` coordinates are **GCJ-02** (they come from
AMap data); when a key and a position are available it plans the route and
starts guidance immediately.

## 6. How the fusion works (and its limits)

GPS fixes anchor the position (a fix better than 15 m replaces the estimate;
a worse fix only pulls it partway). Between fixes, pedestrian dead reckoning
advances it: heading from GPS course when walking (>0.8 m/s — the main source
given the compass-less IMU), gyro-z integration between fixes; speed from
step detection (accelerometer peaks × 0.70 m step length) or fresh GPS speed.
≤2 m error on the synthetic walk. **Step length, thresholds and mounting
orientation are placeholders to tune on the real backpack IMU.** No
barometer/stairs handling, no magnetic-disturbance rejection; indoor
positioning is out of scope.

## 7. Coordinate systems — the one trap

Raw GNSS chips output **WGS-84**. Chinese map providers (AMap included) draw
in **GCJ-02**, offset 100–700 m. Feed this script raw WGS-84; it converts
internally (both directions — `wgs84_to_gcj02` / `gcj02_to_wgs84`) and does
all route matching in GCJ-02, AMap's own frame. If you pre-convert the GPS
input, everything will be double-shifted.

## 8. Running on the Raspberry Pi

- Raspberry Pi OS's bundled Python runs it directly (Bullseye 3.9 /
  Bookworm 3.11; anything ≥3.8 works). CPU cost is negligible.
- Integration options: **import it** (recommended — call `update_gps` /
  `update_imu` from your sensor loop, `process_text` from your speech
  pipeline, and poll `pop_announcements()` ~1 Hz), or **pipe JSON lines**
  from a separate process over stdin/stdout, a FIFO or a local socket.
- GPS: parse NMEA from `/dev/serial0` (or gpsd); send each fix once.
- Landmark lookups and reroutes run on a background thread inside the script —
  `update_gps`/`update_imu` never block on the network. Route planning inside
  `process_text`("确认") is a blocking call, typically well under a second.
- Network: connect the hotspot before heading out. During navigation the
  route is cached — cues keep working offline; only new searches and
  off-route replanning need the network, and failures are spoken, not fatal.

## 9. Open integration points (not silently decided)

- Transport between this script and your speech/sensor processes is yours
  (import, pipe, socket, serial) — the JSON protocol works over any of them.
- Critical-cue interruption: your TTS should cut ongoing speech for
  `critical` announcements; confirm this is possible in your pipeline.
- Shared GPS/IMU clock is still "to be debugged" on your side.
- Obstacle events, target locking, health/battery reporting: waiting on the
  robotics interface, per `docs/TEMPORARY_DEVICE_PROTOCOL.md`.
- Command languages: implemented Chinese-only per your answer; English exists
  only as a debug convenience.

**Safety / 安全:** this script provides route context only. It does not
detect obstacles; local obstacle detection on the backpack must always
outrank it, and map data never proves a crossing is safe — crossing cues
explicitly tell the user to stop at the kerb and verify.
