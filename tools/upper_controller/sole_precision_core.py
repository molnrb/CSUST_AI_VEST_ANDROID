#!/usr/bin/env python3
"""
Sole Precision — Raspberry Pi voice-navigation core (hardware-team handoff).

WHAT THIS IS
    A single-file, dependency-free implementation of the voice-operated walking
    navigation product for the Raspberry Pi.  Per the hardware team's answers of
    27 July 2026: runs on the Pi (no phone app), navigation only (no device
    feature control), speech-to-text and text-to-speech are theirs, network is a
    pre-connected hotspot, command triggering is theirs, and the product voice
    commands are CHINESE ONLY (English keywords remain as a debug convenience).

      1. TEXT IN -> TEXT OUT (Chinese).  ``core.process_text("带我去岳麓山")``
         drives the whole dialog: AMap place search, candidate confirmation,
         walking-route planning, and starting guidance.  The returned sentence is
         handed to your TTS; this script never produces audio itself.

      2. GPS + IMU IN -> REAL-TIME MAP POSITION OUT.  Feed ``GpsSample`` /
         ``ImuSample`` (or the JSON lines below) into ``update_gps`` /
         ``update_imu`` and read the fused position with ``current_position()``.
         Between GPS fixes the position advances by pedestrian dead reckoning.

      3. UNPROMPTED GUIDANCE CUES.  While navigating, position updates produce
         staged walking cues (early / prepare / act-now / confirm / progress /
         off-route / arrival) with clock-face directions and landmarks — the
         same engine as the Android app.  Cues are queued; read them with
         ``pop_announcements()`` (each has a priority: critical / high / normal)
         or from the ``announcements`` field of JSON replies.  Poll at ~1 Hz.

AMAP WEB SERVICE KEY (required for real map data)
    Provide a Web Service key (NOT the Android SDK key) one of three ways:
        1. environment variable  AMAP_WEB_KEY
        2. command line          --key <key>
        3. a file named          amap_key.txt  next to this script
    Never commit the key to git (amap_key.txt is gitignored).  Without a key the
    dialog explains that search is unavailable; ``--fake-amap`` runs everything
    against built-in simulated Changsha data, clearly labelled 模拟.

PROPOSED DATA FORMATS (v0.1 — please confirm or amend; nothing here is final)

    Timestamps: seconds as float.  GPS and IMU MUST share the same clock
    (monotonic preferred).  Epoch seconds also work if both streams use them.

    GPS sample (suggested rate ~1 Hz)
        timestamp    float   seconds, shared clock
        latitude     float   WGS-84 degrees (raw GNSS output, NOT GCJ-02)
        longitude    float   WGS-84 degrees
        accuracy_m   float?  1-sigma horizontal accuracy, metres
        speed_mps    float?  ground speed, m/s
        bearing_deg  float?  course over ground, 0-360, 0 = true north
        satellites   int?    satellites used in the fix

    IMU sample (suggested rate 20-100 Hz)
        timestamp    float   seconds, shared clock
        accel        [x,y,z] m/s^2, device frame, gravity included
        gyro         [x,y,z] rad/s (optional)
        heading_deg  float?  fused compass heading 0-360 if the IMU computes one
                             (optional but strongly recommended; without it the
                             script integrates gyro z and drift will accumulate)

    Position estimate (what you get back)
        latitude / longitude          WGS-84
        gcj_latitude / gcj_longitude  GCJ-02, i.e. where the point sits on an
                                      AMap/Gaode map inside mainland China
        heading_deg, speed_mps, moving, source, horizontal_accuracy_m, gps_age_s

JSON LINE PROTOCOL (interactive mode, or over any pipe/socket)
    Any line starting with '{' is parsed as JSON:
        {"type":"gps","timestamp":10.0,"latitude":28.2282,"longitude":112.9388,
         "accuracy_m":8.0,"speed_mps":5,"bearing_deg":270.0}
        {"type":"imu","timestamp":10.02,"accel":[0.3,0.1,11.2],
         "gyro":[0.0,0.0,0.01],"heading_deg":270.0}
        {"type":"text","text":"带我去岳麓山"}
        {"type":"set_destination","name":"湖南大学",
         "latitude":28.1817,"longitude":112.9443}
        {"type":"get_position"}
        {"type":"poll"}
    GPS / text / set_destination / get_position / poll lines print one JSON
    reply; IMU lines are silent (they arrive at high rate).  Replies carry an
    ``announcements`` list ([{"text","priority"}]) whenever guidance cues are
    queued — poll at ~1 Hz during navigation so cues reach the TTS promptly.
    Every other line is treated as spoken text and answered with a sentence.

USAGE
    python3 sole_precision_core.py                  # interactive (real AMap if key set)
    python3 sole_precision_core.py --fake-amap      # interactive, simulated map data
    python3 sole_precision_core.py --simulate-walk  # laptop testing: seeds a fix and
                                                    # auto-walks planned routes at
                                                    # 5 m/s (--at lat,lon to move
                                                    # the start; WGS-84)
    python3 sole_precision_core.py --demo           # scripted dialog + guided walk
    python3 sole_precision_core.py --selftest       # internal checks, exit 0 on pass

SCOPE AND SAFETY (project non-negotiables)
    * This script provides route context only.  It does NOT detect obstacles.
      Local obstacle detection on the backpack must always outrank anything
      this script says.
    * Map data never proves that a crossing is safe; crossing cues explicitly
      tell the user to stop and verify.
    * AMap access uses the owner-approved Web Service key (decision of
      27 July 2026, superseding the phone-era "no web services" rule).  The
      simulated client is clearly labelled and never mixed with real data.

中文说明（给硬件组）
    本脚本是树莓派语音导航核心：单文件、纯 Python 标准库、Python 3.8+，
    树莓派系统自带的 Python 可直接运行，无需安装任何依赖。
    产品语音指令为中文（英文关键词仅作工程调试用）。

    功能一：文本输入 -> 文本输出（完整对话流程）。
        core.process_text("带我去岳麓山") 触发高德地点搜索；
        说"确认"规划步行路线，说"开始"出发。
        其他指令：我在哪里 / 还有多远 / 附近的厕所（公交站、地铁站、
        药店、医院、超市）/ 天气 / 暂停 / 继续 / 停止 / 重复 / 状态 /
        简洁模式 / 标准模式 / 详细模式 / 帮助。
        文字转语音由你们的模块完成，本脚本不产生任何音频。

    功能二：GPS + IMU 输入 -> 实时地图位置输出。
        用 update_gps() / update_imu() 喂入数据（格式见 README 第 2 节），
        用 current_position() 读取融合后的位置。两次 GPS 定位之间，
        由 IMU（航向 + 计步）做行人航位推算；新的 GPS 定位会把估计值拉回。

    功能三：导航中的主动语音提示。
        位置更新会产生分级步行提示（提前预告 / 准备 / 立即执行 / 转弯确认 /
        进度播报 / 偏离路线 / 即将到达），含钟点方向与地标。
        用 pop_announcements() 读取（每条带 critical/high/normal 优先级），
        或在 JSON 回复的 announcements 字段中获取；导航时请约每秒轮询一次。
        critical 级提示建议打断当前正在播放的语音。

    高德 Web 服务 Key（真实地图数据必需；与安卓 SDK key 不是同一个）：
        1. 环境变量 AMAP_WEB_KEY；或
        2. 命令行参数 --key <key>；或
        3. 与脚本同目录的 amap_key.txt 文件。
        切勿把 key 提交到 git（amap_key.txt 已在 .gitignore 中）。
        无 key 时对话会提示搜索不可用；--fake-amap 使用内置模拟数据
        （明确标注"模拟"）。

    关键约定：
      * GPS 请输入接收机原始的 WGS-84 坐标，切勿预先加偏；
        脚本内部转换 GCJ-02（高德坐标）用于地图匹配并一并返回。
      * GPS 与 IMU 的时间戳必须来自同一个时钟（建议 time.monotonic()）。
      * 本脚本只提供路线信息，不做障碍物检测；背包本地避障永远优先，
        地图数据不能证明过街是安全的。

    运行方式：
        python3 sole_precision_core.py                  # 交互模式（有 key 则用真实高德）
        python3 sole_precision_core.py --fake-amap      # 交互模式（模拟地图数据）
        python3 sole_precision_core.py --simulate-walk  # 电脑测试：自动注入起始定位，
                                                        # 开始导航后沿路线模拟行走
                                                        # （--at 纬度,经度 可改起点）
        python3 sole_precision_core.py --demo           # 演示对话 + 模拟引导行走
        python3 sole_precision_core.py --selftest       # 自检
"""

import json
import math
import os
import queue
import sys
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
from collections import deque
from dataclasses import dataclass
from typing import Deque, Dict, List, Optional, Tuple

EARTH_RADIUS_M = 6_371_000.0
GRAVITY_MPS2 = 9.80665

# Dead-reckoning tuning — placeholders chosen on synthetic data; tune on the
# real backpack IMU before trusting them.
# 航位推算调参——均为基于合成数据的占位值，接入真实背包 IMU 后必须重新标定。
STEP_LENGTH_M = 0.70          # average adult step / 成人平均步长（米），建议按用户配置
STEP_PEAK_THRESHOLD = 1.2     # |accel| - g counts as a step peak / 判定一步的加速度峰值阈值
STEP_REFRACTORY_S = 0.30      # minimum spacing between steps / 两步之间的最小间隔（秒）
SPEED_WINDOW_S = 2.5          # steps in this window define speed / 用该窗口内的步数估算速度
MAX_PEDESTRIAN_SPEED = 2.5    # m/s clamp for step speed / 步频速度上限（米/秒）
GOOD_FIX_ACCURACY_M = 15.0    # such a fix replaces DR outright / 精度优于此值的定位直接采信
GPS_FRESH_S = 3.0             # fresh fix dominates speed/heading / 此时限内 GPS 主导速度与航向
GPS_STALE_S = 10.0            # older -> pure dead reckoning / 超过则视为纯航位推算
DRIFT_M_PER_S = 0.7           # accuracy penalty while reckoning / 航位推算期间的精度惩罚（米/秒）
ARRIVAL_RADIUS_M = 20.0       # arrival radius / 到达判定半径（米）

# Guidance / dialog tuning. / 引导与对话调参。
GUIDANCE_MIN_INTERVAL_S = 0.5   # cue evaluation cap between GPS fixes / 两次定位间提示判定的最小间隔
ROUTE_FINISH_M = 6.0            # end of route reached / 判定走完路线的剩余距离（米）
REROUTE_DRIFT_M = 20            # drift that triggers a reroute attempt / 触发重新规划的偏离距离（米）
REROUTE_COOLDOWN_S = 20.0       # min spacing between reroute attempts / 两次重新规划的最小间隔（秒）
LANDMARK_MAX_DISTANCE_M = 50.0  # POI counts as a landmark within this / 地标的最大距离（米）
AMAP_TIMEOUT_S = 6.0            # HTTP timeout per request / 单次请求超时（秒）
AMAP_WORKER_SPACING_S = 0.35    # background request spacing (QPS limit) / 后台请求间隔（个人 key 限流）
NEARBY_RADIUS_M = 1000          # nearby-essentials search radius / 周边设施搜索半径（米）


# --------------------------------------------------------------------------
# Geometry / 几何工具
# --------------------------------------------------------------------------

def haversine_m(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    """Great-circle distance in metres. / 两点间大圆距离（米）。"""
    p1, p2 = math.radians(lat1), math.radians(lat2)
    dp, dl = math.radians(lat2 - lat1), math.radians(lon2 - lon1)
    a = math.sin(dp / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dl / 2) ** 2
    return 2 * EARTH_RADIUS_M * math.asin(math.sqrt(min(1.0, a)))


def bearing_deg(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    """Initial great-circle bearing from point 1 to point 2, 0-360, 0 = north.
    从点 1 指向点 2 的初始方位角，0-360，0 = 正北。"""
    p1, p2 = math.radians(lat1), math.radians(lat2)
    dl = math.radians(lon2 - lon1)
    y = math.sin(dl) * math.cos(p2)
    x = math.cos(p1) * math.sin(p2) - math.sin(p1) * math.cos(p2) * math.cos(dl)
    return (math.degrees(math.atan2(y, x)) + 360.0) % 360.0


def offset_position(lat: float, lon: float, heading: float, distance_m: float) -> Tuple[float, float]:
    """Move ``distance_m`` from (lat, lon) along ``heading`` degrees (flat-earth,
    fine for the metre-scale hops dead reckoning makes).
    沿 heading 方向移动 distance_m 米（平面近似，足够米级步进使用）。"""
    dlat = distance_m * math.cos(math.radians(heading)) / EARTH_RADIUS_M
    dlon = (distance_m * math.sin(math.radians(heading))
            / (EARTH_RADIUS_M * math.cos(math.radians(lat))))
    return lat + math.degrees(dlat), lon + math.degrees(dlon)


def angle_diff_deg(a: float, b: float) -> float:
    """Signed smallest difference b-a in degrees, in (-180, 180]. / 最小带符号角差。"""
    return ((b - a + 540.0) % 360.0) - 180.0


def is_valid_coordinate(lat: float, lon: float) -> bool:
    return (math.isfinite(lat) and math.isfinite(lon)
            and -90.0 <= lat <= 90.0 and -180.0 <= lon <= 180.0
            and not (lat == 0.0 and lon == 0.0))


# Path helpers used for matching the walker onto a route polyline.  All work in
# a local flat-earth metre frame, which is accurate to centimetres at the
# sub-kilometre scale of walking steps.
# 路线折线匹配工具。全部在局部平面米制坐标系中计算，步行级距离下误差为厘米级。

def _to_local_m(ref_lat: float, lat: float, lon_diff: float) -> Tuple[float, float]:
    """(north_m, east_m) of a point relative to a reference latitude.
    以参考纬度为基准，将经纬差换算为（北向米，东向米）。"""
    north = math.radians(lat - ref_lat) * EARTH_RADIUS_M
    east = math.radians(lon_diff) * EARTH_RADIUS_M * math.cos(math.radians(ref_lat))
    return north, east


def path_length_m(path: List[Tuple[float, float]]) -> float:
    """Total polyline length in metres. / 折线总长（米）。"""
    return sum(haversine_m(path[i][0], path[i][1], path[i + 1][0], path[i + 1][1])
               for i in range(len(path) - 1))


def project_point_to_path(path: List[Tuple[float, float]],
                          lat: float, lon: float) -> Tuple[float, float]:
    """Project a point onto a polyline.  Returns (drift_m, along_m):
    perpendicular distance to the nearest segment and distance walked along the
    path up to that projection.
    将点投影到折线上，返回（垂直偏离距离，沿线已走距离），单位米。"""
    if len(path) < 2:
        if not path:
            return float("inf"), 0.0
        return haversine_m(lat, lon, path[0][0], path[0][1]), 0.0
    best_drift, best_along = float("inf"), 0.0
    walked = 0.0
    for i in range(len(path) - 1):
        (lat1, lon1), (lat2, lon2) = path[i], path[i + 1]
        pn, pe = _to_local_m(lat1, lat, lon - lon1)
        sn, se = _to_local_m(lat1, lat2, lon2 - lon1)
        seg_len_sq = sn * sn + se * se
        if seg_len_sq <= 1e-9:
            t, seg_len = 0.0, 0.0
        else:
            t = max(0.0, min(1.0, (pn * sn + pe * se) / seg_len_sq))
            seg_len = math.sqrt(seg_len_sq)
        dn, de = pn - t * sn, pe - t * se
        drift = math.sqrt(dn * dn + de * de)
        if drift < best_drift:
            best_drift = drift
            best_along = walked + t * seg_len
        walked += math.sqrt(seg_len_sq)
    return best_drift, best_along


def point_along_path(path: List[Tuple[float, float]],
                     distance_m: float) -> Tuple[float, float, float]:
    """Point ``distance_m`` metres along the polyline; returns (lat, lon,
    segment bearing).  Clamped to the path ends.
    折线上距起点 distance_m 米处的点及该段的方位角；超出范围时取端点。"""
    if len(path) == 1:
        return path[0][0], path[0][1], 0.0
    remaining = max(0.0, distance_m)
    for i in range(len(path) - 1):
        (lat1, lon1), (lat2, lon2) = path[i], path[i + 1]
        seg = haversine_m(lat1, lon1, lat2, lon2)
        seg_bearing = bearing_deg(lat1, lon1, lat2, lon2)
        if remaining <= seg or i == len(path) - 2:
            frac = 0.0 if seg <= 0 else min(1.0, remaining / seg)
            return (lat1 + (lat2 - lat1) * frac,
                    lon1 + (lon2 - lon1) * frac,
                    seg_bearing)
        remaining -= seg
    return path[-1][0], path[-1][1], 0.0


def initial_bearing_of_path(path: List[Tuple[float, float]]) -> Optional[float]:
    """Bearing of the first segment, or None for degenerate paths.
    折线首段的方位角；折线退化时返回 None。"""
    for i in range(len(path) - 1):
        if path[i] != path[i + 1]:
            return bearing_deg(path[i][0], path[i][1], path[i + 1][0], path[i + 1][1])
    return None


_COMPASS_ZH = ("北", "东北", "东", "东南", "南", "西南", "西", "西北")


def bearing_to_compass_zh(deg: float) -> str:
    """0-360 bearing -> 8-way Chinese compass word. / 方位角转八方位中文。"""
    return _COMPASS_ZH[int(((deg % 360.0) + 22.5) // 45.0) % 8]


# --------------------------------------------------------------------------
# WGS-84 -> GCJ-02 (the offset used by all licensed maps in mainland China,
# including AMap).  Standard public transform; error is roughly 1-2 m.
# WGS-84 -> GCJ-02：中国大陆地图（含高德）使用的加偏坐标；公开标准算法，误差约 1-2 米。
# --------------------------------------------------------------------------

_A = 6378245.0
_EE = 0.00669342162296594323


def _transform_lat(x: float, y: float) -> float:
    ret = (-100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y
           + 0.2 * math.sqrt(abs(x)))
    ret += (20.0 * math.sin(6.0 * x * math.pi) + 20.0 * math.sin(2.0 * x * math.pi)) * 2.0 / 3.0
    ret += (20.0 * math.sin(y * math.pi) + 40.0 * math.sin(y / 3.0 * math.pi)) * 2.0 / 3.0
    ret += (160.0 * math.sin(y / 12.0 * math.pi) + 320.0 * math.sin(y * math.pi / 30.0)) * 2.0 / 3.0
    return ret


def _transform_lon(x: float, y: float) -> float:
    ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * math.sqrt(abs(x))
    ret += (20.0 * math.sin(6.0 * x * math.pi) + 20.0 * math.sin(2.0 * x * math.pi)) * 2.0 / 3.0
    ret += (20.0 * math.sin(x * math.pi) + 40.0 * math.sin(x / 3.0 * math.pi)) * 2.0 / 3.0
    ret += (150.0 * math.sin(x / 12.0 * math.pi) + 300.0 * math.sin(x / 30.0 * math.pi)) * 2.0 / 3.0
    return ret


def out_of_china(lat: float, lon: float) -> bool:
    return not (72.004 <= lon <= 137.8347 and 0.8293 <= lat <= 55.8271)


def wgs84_to_gcj02(lat: float, lon: float) -> Tuple[float, float]:
    """Convert a raw GNSS coordinate to the coordinate AMap draws it at.
    将原始 GNSS 坐标转换为高德地图绘制时使用的坐标。"""
    if out_of_china(lat, lon):
        return lat, lon
    dlat = _transform_lat(lon - 105.0, lat - 35.0)
    dlon = _transform_lon(lon - 105.0, lat - 35.0)
    rad_lat = math.radians(lat)
    magic = 1 - _EE * math.sin(rad_lat) ** 2
    sqrt_magic = math.sqrt(magic)
    dlat = (dlat * 180.0) / ((_A * (1 - _EE)) / (magic * sqrt_magic) * math.pi)
    dlon = (dlon * 180.0) / (_A / sqrt_magic * math.cos(rad_lat) * math.pi)
    return lat + dlat, lon + dlon


def gcj02_to_wgs84(lat: float, lon: float) -> Tuple[float, float]:
    """Inverse transform by fixed-point iteration (sub-centimetre after three
    rounds).  Used by the demo/selftest to synthesise raw-GNSS input from AMap
    route geometry; real GNSS receivers already output WGS-84.
    GCJ-02 -> WGS-84 反解（不动点迭代，三轮后误差小于厘米级）。
    演示/自检用它从高德路线几何反推"原始 GNSS"输入；真实接收机本就输出 WGS-84。"""
    if out_of_china(lat, lon):
        return lat, lon
    wlat, wlon = lat, lon
    for _ in range(3):
        glat, glon = wgs84_to_gcj02(wlat, wlon)
        wlat += lat - glat
        wlon += lon - glon
    return wlat, wlon


# --------------------------------------------------------------------------
# Sensor samples / 传感器数据结构
# --------------------------------------------------------------------------

@dataclass
class GpsSample:
    """One GNSS fix, raw WGS-84. / 一次 GNSS 定位（WGS-84 原始坐标）。"""

    timestamp: float
    latitude: float
    longitude: float
    accuracy_m: Optional[float] = None
    speed_mps: Optional[float] = None
    bearing_deg: Optional[float] = None
    satellites: Optional[int] = None

    @staticmethod
    def from_dict(d: Dict) -> "GpsSample":
        sample = GpsSample(
            timestamp=float(d["timestamp"]),
            latitude=float(d["latitude"]),
            longitude=float(d["longitude"]),
            accuracy_m=None if d.get("accuracy_m") is None else float(d["accuracy_m"]),
            speed_mps=None if d.get("speed_mps") is None else float(d["speed_mps"]),
            bearing_deg=None if d.get("bearing_deg") is None else float(d["bearing_deg"]),
            satellites=None if d.get("satellites") is None else int(d["satellites"]),
        )
        if not is_valid_coordinate(sample.latitude, sample.longitude):
            raise ValueError("invalid GPS coordinate")
        return sample


@dataclass
class ImuSample:
    """One IMU sample; accel required, gyro/heading optional.
    一条 IMU 数据：加速度必填，陀螺仪与融合航向可选（强烈建议提供航向）。"""

    timestamp: float
    accel: Tuple[float, float, float]
    gyro: Optional[Tuple[float, float, float]] = None
    heading_deg: Optional[float] = None

    @staticmethod
    def from_dict(d: Dict) -> "ImuSample":
        ax, ay, az = (float(v) for v in d["accel"])
        gyro = d.get("gyro")
        return ImuSample(
            timestamp=float(d["timestamp"]),
            accel=(ax, ay, az),
            gyro=None if gyro is None else tuple(float(v) for v in gyro),
            heading_deg=None if d.get("heading_deg") is None else float(d["heading_deg"]),
        )


@dataclass
class PositionEstimate:
    """Fused position output, WGS-84 and GCJ-02 together.
    融合后的位置输出，同时给出 WGS-84 与高德 GCJ-02 坐标。"""

    timestamp: float
    latitude: float
    longitude: float
    gcj_latitude: float
    gcj_longitude: float
    heading_deg: Optional[float]
    speed_mps: float
    moving: bool
    source: str                 # "gps" | "gps+imu" | "imu-dead-reckoning"
    horizontal_accuracy_m: Optional[float]
    gps_age_s: float

    def to_dict(self) -> Dict:
        return {
            "timestamp": round(self.timestamp, 3),
            "latitude": round(self.latitude, 7),
            "longitude": round(self.longitude, 7),
            "gcj_latitude": round(self.gcj_latitude, 7),
            "gcj_longitude": round(self.gcj_longitude, 7),
            "heading_deg": None if self.heading_deg is None else round(self.heading_deg, 1),
            "speed_mps": round(self.speed_mps, 2),
            "moving": self.moving,
            "source": self.source,
            "horizontal_accuracy_m": None if self.horizontal_accuracy_m is None
            else round(self.horizontal_accuracy_m, 1),
            "gps_age_s": round(self.gps_age_s, 2),
        }


# --------------------------------------------------------------------------
# Position tracker: GPS anchor + pedestrian dead reckoning between fixes.
# Deliberately a small complementary filter, not a Kalman filter: it is easy
# to reason about, and honest about being a prototype.
# 位置跟踪：GPS 作锚点，两次定位之间做行人航位推算。刻意选用简单的互补滤波
# 而非卡尔曼滤波——便于推理，也如实表明这是原型。
# --------------------------------------------------------------------------

class PositionTracker:
    def __init__(self, step_length_m: float = STEP_LENGTH_M) -> None:
        self.step_length_m = step_length_m
        self._lat: Optional[float] = None
        self._lon: Optional[float] = None
        self._heading: Optional[float] = None
        self._last_fix: Optional[GpsSample] = None
        self._last_imu_ts: Optional[float] = None
        self._last_step_ts: Optional[float] = None
        self._step_times: Deque[float] = deque()
        self._now: float = 0.0
        self._used_imu_since_fix = False

    # -- GPS ---------------------------------------------------------------

    def update_gps(self, fix: GpsSample) -> None:
        if not is_valid_coordinate(fix.latitude, fix.longitude):
            return
        self._now = max(self._now, fix.timestamp)
        acc = fix.accuracy_m if fix.accuracy_m is not None else 25.0

        if self._lat is None or acc <= GOOD_FIX_ACCURACY_M:
            # First fix, or a fix good enough to trust outright.
            # 首次定位，或精度足够好、可直接采信的定位。
            self._lat, self._lon = fix.latitude, fix.longitude
        else:
            # Poor fix: pull the dead-reckoned estimate part-way toward it so a
            # single 40 m outlier cannot teleport the walker.
            # 精度较差的定位只把估计值拉近一部分，避免一个 40 米级野值让行人"瞬移"。
            weight = max(0.3, min(0.8, GOOD_FIX_ACCURACY_M / acc))
            self._lat += (fix.latitude - self._lat) * weight
            self._lon += (fix.longitude - self._lon) * weight

        # Course over ground beats integrated heading once actually walking.
        # Threshold 0.8 m/s: the confirmed IMU is a common accel/gyro part with
        # no fused compass, so GPS course is the main heading source outdoors.
        # 一旦真正行走，GPS 航迹方向优于积分航向。阈值 0.8 m/s：硬件组确认的
        # IMU 为常见型号（无融合罗盘航向），室外航向主要依赖 GPS 航迹。
        if fix.bearing_deg is not None and (fix.speed_mps or 0.0) > 0.8:
            self._heading = fix.bearing_deg % 360.0
        self._last_fix = fix
        self._used_imu_since_fix = False

    # -- IMU ---------------------------------------------------------------

    def update_imu(self, sample: ImuSample) -> None:
        ts = sample.timestamp
        dt = 0.0 if self._last_imu_ts is None else max(0.0, min(0.5, ts - self._last_imu_ts))
        self._last_imu_ts = ts
        self._now = max(self._now, ts)

        # Heading: prefer a fused compass heading; otherwise integrate gyro z.
        # 航向：优先使用 IMU 的融合罗盘航向；否则对陀螺仪 z 轴积分。
        if sample.heading_deg is not None:
            if self._heading is None:
                self._heading = sample.heading_deg % 360.0
            else:  # low-pass toward the compass to damp jitter / 向罗盘低通，抑制抖动
                self._heading = (self._heading
                                 + 0.25 * angle_diff_deg(self._heading, sample.heading_deg)) % 360.0
        elif sample.gyro is not None and self._heading is not None and dt > 0.0:
            self._heading = (self._heading + math.degrees(sample.gyro[2]) * dt) % 360.0

        # Step detection: peaks of |accel| above gravity with a refractory gap.
        # 计步：加速度模长超过重力阈值的峰值，并加不应期防止重复计数。
        magnitude = math.sqrt(sum(v * v for v in sample.accel))
        if magnitude - GRAVITY_MPS2 > STEP_PEAK_THRESHOLD:
            if self._last_step_ts is None or ts - self._last_step_ts >= STEP_REFRACTORY_S:
                self._last_step_ts = ts
                self._step_times.append(ts)
        while self._step_times and ts - self._step_times[0] > SPEED_WINDOW_S:
            self._step_times.popleft()

        # Advance the estimate between fixes. / 两次定位之间推进位置估计。
        speed = self._current_speed()
        if (self._lat is not None and self._heading is not None
                and speed > 0.0 and dt > 0.0):
            self._lat, self._lon = offset_position(self._lat, self._lon,
                                                   self._heading, speed * dt)
            self._used_imu_since_fix = True

    # -- Read-out ----------------------------------------------------------

    def _gps_age(self) -> float:
        if self._last_fix is None:
            return float("inf")
        return max(0.0, self._now - self._last_fix.timestamp)

    def _current_speed(self) -> float:
        fix = self._last_fix
        if (fix is not None and fix.speed_mps is not None
                and self._gps_age() <= GPS_FRESH_S):
            return max(0.0, fix.speed_mps)
        if len(self._step_times) >= 2:
            span = max(0.5, self._step_times[-1] - self._step_times[0])
            cadence = (len(self._step_times) - 1) / span
            return min(MAX_PEDESTRIAN_SPEED, cadence * self.step_length_m)
        return 0.0

    def current_position(self) -> Optional[PositionEstimate]:
        if self._lat is None or self._lon is None:
            return None
        age = self._gps_age()
        speed = self._current_speed()
        moving = speed > 0.4 or (self._last_step_ts is not None
                                 and self._now - self._last_step_ts < 2.0)
        base_acc = (self._last_fix.accuracy_m
                    if self._last_fix is not None and self._last_fix.accuracy_m is not None
                    else None)
        drift = min(30.0, DRIFT_M_PER_S * min(age, 120.0)) if age > GPS_FRESH_S else 0.0
        accuracy = None if base_acc is None else base_acc + drift
        if age > GPS_STALE_S:
            source = "imu-dead-reckoning"
        elif self._used_imu_since_fix:
            source = "gps+imu"
        else:
            source = "gps"
        gcj_lat, gcj_lon = wgs84_to_gcj02(self._lat, self._lon)
        return PositionEstimate(
            timestamp=self._now,
            latitude=self._lat, longitude=self._lon,
            gcj_latitude=gcj_lat, gcj_longitude=gcj_lon,
            heading_deg=self._heading,
            speed_mps=round(speed, 2),
            moving=moving,
            source=source,
            horizontal_accuracy_m=accuracy,
            gps_age_s=0.0 if math.isinf(age) else age,
        )


# --------------------------------------------------------------------------
# Maneuver vocabulary.  Ported from the Android app (Maneuver.kt); labels are
# the reviewed Simplified-Chinese strings from the app's phrase table.
# 动作词表。移植自安卓端 Maneuver.kt；中文标签取自应用短语表。
# --------------------------------------------------------------------------

class Maneuver:
    UNKNOWN = "UNKNOWN"
    STRAIGHT = "STRAIGHT"
    SLIGHT_LEFT = "SLIGHT_LEFT"
    LEFT = "LEFT"
    SHARP_LEFT = "SHARP_LEFT"
    SLIGHT_RIGHT = "SLIGHT_RIGHT"
    RIGHT = "RIGHT"
    SHARP_RIGHT = "SHARP_RIGHT"
    U_TURN = "U_TURN"
    CROSSWALK = "CROSSWALK"
    STAIRS = "STAIRS"
    ELEVATOR = "ELEVATOR"
    ARRIVED = "ARRIVED"
    OVERPASS = "OVERPASS"
    UNDERPASS = "UNDERPASS"
    ESCALATOR = "ESCALATOR"
    RAMP = "RAMP"
    BRIDGE = "BRIDGE"
    TUNNEL = "TUNNEL"
    PEDESTRIAN_WAY = "PEDESTRIAN_WAY"
    ENTER_BUILDING = "ENTER_BUILDING"
    SUBWAY_PASSAGE = "SUBWAY_PASSAGE"
    FERRY = "FERRY"
    PARK_OR_SQUARE = "PARK_OR_SQUARE"


MANEUVER_LABEL_ZH: Dict[str, str] = {
    Maneuver.UNKNOWN: "谨慎前行",
    Maneuver.STRAIGHT: "直行",
    Maneuver.SLIGHT_LEFT: "稍向左转",
    Maneuver.LEFT: "左转",
    Maneuver.SHARP_LEFT: "向左急转",
    Maneuver.SLIGHT_RIGHT: "稍向右转",
    Maneuver.RIGHT: "右转",
    Maneuver.SHARP_RIGHT: "向右急转",
    Maneuver.U_TURN: "掉头",
    Maneuver.CROSSWALK: "前方人行横道",
    Maneuver.STAIRS: "前方楼梯",
    Maneuver.ELEVATOR: "前方电梯",
    Maneuver.ARRIVED: "已到达目的地",
    Maneuver.OVERPASS: "前方人行天桥",
    Maneuver.UNDERPASS: "前方地下通道",
    Maneuver.ESCALATOR: "前方自动扶梯",
    Maneuver.RAMP: "前方坡道",
    Maneuver.BRIDGE: "前方桥梁",
    Maneuver.TUNNEL: "前方隧道",
    Maneuver.PEDESTRIAN_WAY: "前方步行道",
    Maneuver.ENTER_BUILDING: "进入建筑物",
    Maneuver.SUBWAY_PASSAGE: "前方地铁通道",
    Maneuver.FERRY: "前方轮渡衔接",
    Maneuver.PARK_OR_SQUARE: "前方开阔步行区",
}

_LEFT_MANEUVERS = {Maneuver.SLIGHT_LEFT, Maneuver.LEFT, Maneuver.SHARP_LEFT}
_RIGHT_MANEUVERS = {Maneuver.SLIGHT_RIGHT, Maneuver.RIGHT, Maneuver.SHARP_RIGHT}

# Crossings and level changes always deserve stop-and-verify wording.
# 过街与上下层动作必须附加"停下确认"类措辞。
HAZARD_MANEUVERS = {
    Maneuver.CROSSWALK, Maneuver.OVERPASS, Maneuver.UNDERPASS,
    Maneuver.STAIRS, Maneuver.ESCALATOR, Maneuver.ELEVATOR, Maneuver.FERRY,
}

# Bare nouns for the segment-entry hazard preview ("前方约420米有人行横道").
# 路段预告用的名词形式（"前方约420米有人行横道"）。
HAZARD_NOUN_ZH: Dict[str, str] = {
    Maneuver.CROSSWALK: "人行横道",
    Maneuver.OVERPASS: "人行天桥",
    Maneuver.UNDERPASS: "地下通道",
    Maneuver.STAIRS: "楼梯",
    Maneuver.ESCALATOR: "自动扶梯",
    Maneuver.ELEVATOR: "电梯",
    Maneuver.FERRY: "轮渡",
    Maneuver.SUBWAY_PASSAGE: "地铁通道",
}


def turn_side(maneuver: str) -> str:
    """'LEFT' | 'RIGHT' | 'NONE' — used for clock positions and device cues.
    转向侧别，用于钟点方向与设备振动提示。"""
    if maneuver in _LEFT_MANEUVERS:
        return "LEFT"
    if maneuver in _RIGHT_MANEUVERS:
        return "RIGHT"
    return "NONE"


# AMap v3 walking-route ``walk_type`` -> special segment maneuver.  Values from
# the official Web Service docs (reviewed 2026-07); verify against live
# responses during integration — unknown values simply mean "normal road".
# 高德 v3 步行路径 walk_type -> 特殊路段动作。取值来自官方文档（2026-07 审阅）；
# 联调时请对照真实返回校验，未知值一律按普通道路处理。
_WALK_TYPE_MANEUVER: Dict[int, str] = {
    1: Maneuver.CROSSWALK,        # 人行横道
    3: Maneuver.UNDERPASS,        # 地下通道
    4: Maneuver.OVERPASS,         # 过街天桥
    5: Maneuver.SUBWAY_PASSAGE,   # 地铁通道
    6: Maneuver.PARK_OR_SQUARE,   # 公园
    7: Maneuver.PARK_OR_SQUARE,   # 广场
    8: Maneuver.ESCALATOR,        # 扶梯
    9: Maneuver.ELEVATOR,         # 直梯
    12: Maneuver.ENTER_BUILDING,  # 建筑物穿越通道
    13: Maneuver.PEDESTRIAN_WAY,  # 行人通道
    20: Maneuver.STAIRS,          # 阶梯
    21: Maneuver.RAMP,            # 斜坡
    22: Maneuver.BRIDGE,          # 桥
    23: Maneuver.TUNNEL,          # 隧道
    30: Maneuver.FERRY,           # 轮渡
}

# AMap ``action`` text -> turn maneuver.  Longest / most specific first.
# 高德 action 文本 -> 转向动作。按最具体的模式优先匹配。
_ACTION_MANEUVERS: Tuple[Tuple[str, str], ...] = (
    ("向左前方", Maneuver.SLIGHT_LEFT),
    ("向右前方", Maneuver.SLIGHT_RIGHT),
    ("向左后方", Maneuver.SHARP_LEFT),
    ("向右后方", Maneuver.SHARP_RIGHT),
    ("左转", Maneuver.LEFT),
    ("右转", Maneuver.RIGHT),
    ("靠左", Maneuver.SLIGHT_LEFT),
    ("靠右", Maneuver.SLIGHT_RIGHT),
    ("掉头", Maneuver.U_TURN),
    ("调头", Maneuver.U_TURN),
    ("直行", Maneuver.STRAIGHT),
    ("人行横道", Maneuver.CROSSWALK),
    ("过街天桥", Maneuver.OVERPASS),
    ("地下通道", Maneuver.UNDERPASS),
)


def maneuver_from_amap(action_text: str, next_walk_type: Optional[int],
                       is_last_step: bool) -> str:
    """Maneuver performed at the END of a step: a special next segment (e.g. a
    crossing) outranks the turn text; the last step means arrival.
    某一段末端要执行的动作：下一段若是特殊路段（如过街）优先于转向文本；
    最后一段即到达。"""
    if next_walk_type is not None and next_walk_type in _WALK_TYPE_MANEUVER:
        return _WALK_TYPE_MANEUVER[next_walk_type]
    for pattern, maneuver in _ACTION_MANEUVERS:
        if pattern in action_text:
            return maneuver
    if is_last_step:
        return Maneuver.ARRIVED
    return Maneuver.STRAIGHT


# --------------------------------------------------------------------------
# Pedestrian guidance engine.  Direct port of the Android app's
# PedestrianGuidance.kt (verified live on the emulator): staged cues, spoken at
# most once per stage per step, so speech density stays low enough to hear
# traffic.  Stages: EARLY(120m) PREPARE(30m) ACT(8m; hazards 12m) CONFIRM
# PROGRESS OFF_ROUTE ARRIVAL.
# 行人引导引擎。逐行移植自安卓端 PedestrianGuidance.kt（已在模拟器实测）：
# 分级提示，每段每级最多播报一次，保证语音密度低到能听清路面交通。
# --------------------------------------------------------------------------

STAGE_EARLY = "EARLY"
STAGE_PREPARE = "PREPARE"
STAGE_ACT = "ACT"
STAGE_CONFIRM = "CONFIRM"
STAGE_PROGRESS = "PROGRESS"
STAGE_OFF_ROUTE = "OFF_ROUTE"
STAGE_ARRIVAL = "ARRIVAL"

# Announcement priorities: critical cues should interrupt whatever the TTS is
# currently saying; normal ones queue behind it.
# 播报优先级：critical 建议打断当前语音，normal 排队等待。
_STAGE_PRIORITY = {
    STAGE_ACT: "critical",
    STAGE_OFF_ROUTE: "critical",
    STAGE_ARRIVAL: "critical",
    STAGE_PREPARE: "high",
    STAGE_EARLY: "normal",
    STAGE_CONFIRM: "normal",
    STAGE_PROGRESS: "normal",
}


@dataclass
class GuidanceSnapshot:
    """Everything known about the current moment on the route.
    当前时刻在路线上的全部已知信息。"""

    step_index: int
    maneuver: str
    distance_to_maneuver_m: int
    next_road_name: str = ""
    current_road_name: str = ""
    step_distance_m: int = 0
    orientation: str = ""
    relative_bearing_deg: Optional[int] = None
    turn_angle_deg: Optional[int] = None
    landmark: str = ""
    needs_confirmation: bool = False
    remaining_route_m: int = 0
    remaining_route_s: int = 0
    off_route_m: Optional[int] = None   # None while matched to the route / 贴合路线时为 None
    # First mapped attention point between here and the next turn (crossing,
    # stairs, bridge...), for the segment-entry preview.
    # 从此处到下一个转弯之间第一个需要留意的地图要素（过街、楼梯、天桥……），
    # 用于进入路段时的预告。
    hazard_ahead_maneuver: Optional[str] = None
    hazard_ahead_m: int = 0


@dataclass
class GuidanceCue:
    """One thing to say at one moment. / 某一时刻要说的一句话（结构化）。"""

    stage: str
    maneuver: str
    distance_m: int
    road_name: str = ""
    current_road_name: str = ""
    step_distance_m: int = 0
    clock_position: Optional[int] = None
    turn_angle_deg: Optional[int] = None
    orientation: str = ""
    landmark: str = ""
    needs_confirmation: bool = False
    remaining_route_m: int = 0
    remaining_route_min: int = 0
    off_route_m: int = 0
    side: str = "NONE"
    hazard_ahead_maneuver: Optional[str] = None
    hazard_ahead_m: int = 0

    @property
    def is_hazard(self) -> bool:
        return self.maneuver == Maneuver.CROSSWALK or self.needs_confirmation

    @property
    def priority(self) -> str:
        if self.is_hazard and self.stage in (STAGE_PREPARE, STAGE_ACT):
            return "critical"
        return _STAGE_PRIORITY.get(self.stage, "normal")


def clock_position_for(relative_bearing_deg: int) -> int:
    """Clock face relative to the current heading: 12 straight ahead, 3 hard
    right, 9 hard left — the convention blind travellers train on.
    相对当前航向的钟点方向：12 点正前，3 点右侧，9 点左侧——盲人定向行走的
    标准训练用法。"""
    normalized = ((relative_bearing_deg % 360) + 360) % 360
    hour = int(round(normalized / 30.0)) % 12
    return 12 if hour == 0 else hour


class PedestrianGuidanceEngine:
    EARLY_METERS = 120
    PREPARE_METERS = 30
    ACT_METERS = 8
    HAZARD_ACT_METERS = 12
    ARRIVAL_APPROACH_METERS = 25
    EARLY_MINIMUM_STEP_METERS = 60
    CONFIRM_MINIMUM_STEP_METERS = 15
    OFF_ROUTE_MINIMUM_METERS = 8
    OFF_ROUTE_STEP_METERS = 10

    def __init__(self) -> None:
        self.reset()

    def reset(self) -> None:
        self._announced: Dict[int, set] = {}
        self._last_step_index: Optional[int] = None
        self._last_progress_bucket: Optional[int] = None
        self._last_progress_distance: Optional[int] = None
        self._last_off_route_m: Optional[int] = None
        self._arrival_announced = False

    def prime_progress(self, distance_m: int) -> None:
        """Mark the current distance as just-spoken so the first PROGRESS cue
        does not repeat the departure sentence a second later.
        把当前距离标记为"刚刚说过"，避免出发播报一秒后又来一条进度提示。"""
        bucket_size = self.progress_bucket_size(distance_m)
        self._last_progress_distance = distance_m
        self._last_progress_bucket = (None if bucket_size is None
                                      else distance_m // bucket_size)

    @staticmethod
    def progress_bucket_size(distance_m: int) -> Optional[int]:
        """Reassurance spacing: sparse far from the action (~every 250 m, or
        500 m beyond 1.2 km — a cue every three-to-four walking minutes), and
        below 150 m none at all, because the EARLY(120)/PREPARE(30)/ACT ladder
        takes over and densifies naturally.  None = no progress cues here.
        进度播报间距：离动作远时稀疏（约每 250 米一次，超过 1.2 公里每 500 米，
        即步行三四分钟一次）；150 米以内完全交给预告/准备/执行阶梯自然加密。
        返回 None 表示该距离段无进度播报。"""
        if distance_m > 1200:
            return 500
        if distance_m > 150:
            return 250
        return None

    def on_snapshot(self, s: GuidanceSnapshot) -> Optional[GuidanceCue]:
        step_changed = (self._last_step_index is not None
                        and self._last_step_index != s.step_index)
        if step_changed:
            self._last_progress_bucket = None
            self._last_progress_distance = None
        previous_step = self._last_step_index
        self._last_step_index = s.step_index

        # 1. Drift outranks everything: a blind walker must hear immediately
        # that they have left the mapped path.
        # 1. 偏离最优先：盲人步行者必须第一时间知道自己离开了地图路径。
        if s.off_route_m is not None and s.off_route_m >= self.OFF_ROUTE_MINIMUM_METERS:
            previous = self._last_off_route_m
            if previous is None or abs(s.off_route_m - previous) >= self.OFF_ROUTE_STEP_METERS:
                self._last_off_route_m = s.off_route_m
                return self._emit(s, STAGE_OFF_ROUTE, off_route_m=s.off_route_m)
            return None
        if s.off_route_m is None:
            self._last_off_route_m = None

        # 2. Final approach. / 2. 即将到达。
        if not self._arrival_announced and 1 <= s.remaining_route_m <= self.ARRIVAL_APPROACH_METERS:
            self._arrival_announced = True
            return self._emit(s, STAGE_ARRIVAL)

        # 3. Maneuver completed: confirm the new road. / 3. 转弯完成：确认新道路。
        if (step_changed and previous_step is not None
                and s.step_distance_m >= self.CONFIRM_MINIMUM_STEP_METERS
                and self._mark(s.step_index, STAGE_CONFIRM)):
            return self._emit(s, STAGE_CONFIRM)

        # 4-6. Act / prepare / early, closest first, once per step.
        # 4-6. 执行 / 准备 / 预告：先近后远，每段各一次。
        is_hazard = s.maneuver == Maneuver.CROSSWALK or s.needs_confirmation
        act_threshold = self.HAZARD_ACT_METERS if is_hazard else self.ACT_METERS
        if s.distance_to_maneuver_m <= act_threshold and self._mark(s.step_index, STAGE_ACT):
            return self._emit(s, STAGE_ACT)
        if s.distance_to_maneuver_m <= self.PREPARE_METERS and self._mark(s.step_index, STAGE_PREPARE):
            return self._emit(s, STAGE_PREPARE)
        if (s.distance_to_maneuver_m <= self.EARLY_METERS
                and s.step_distance_m > self.EARLY_MINIMUM_STEP_METERS
                and self._mark(s.step_index, STAGE_EARLY)):
            return self._emit(s, STAGE_EARLY)

        # 7. Reassurance on long segments, plus a distance-walked guard so
        # bucket edges cannot fire twice in a row.
        # 7. 长路段进度播报；加"已走距离"保护，防止在分桶边界连发两次。
        bucket_size = self.progress_bucket_size(s.distance_to_maneuver_m)
        if bucket_size is None:
            return None
        bucket = s.distance_to_maneuver_m // bucket_size
        if bucket == self._last_progress_bucket:
            return None
        # Require a full bucket of real walking since the last spoken cue, so
        # spacing is genuinely ~250/500 m regardless of bucket-edge crossings.
        # 距上一条播报需真正走满一个分桶距离，保证间距实打实是 250/500 米，
        # 与分桶边界的具体位置无关。
        if (self._last_progress_distance is not None
                and self._last_progress_distance - s.distance_to_maneuver_m < bucket_size):
            return None
        self._last_progress_bucket = bucket
        return self._emit(s, STAGE_PROGRESS)

    def _emit(self, s: GuidanceSnapshot, stage: str,
              off_route_m: int = 0) -> GuidanceCue:
        """Every spoken cue counts as "just talked": arm the walked-guard so a
        PROGRESS cue cannot immediately restate what was just announced.
        任何一条播报都视为"刚刚说过"：武装"已走距离"保护，防止进度提示
        紧跟着复读一遍。"""
        self._last_progress_distance = s.distance_to_maneuver_m
        return self._cue(s, stage, off_route_m)

    def _mark(self, step_index: int, stage: str) -> bool:
        stages = self._announced.setdefault(step_index, set())
        if stage in stages:
            return False
        stages.add(stage)
        return True

    @staticmethod
    def _cue(s: GuidanceSnapshot, stage: str, off_route_m: int = 0) -> GuidanceCue:
        return GuidanceCue(
            stage=stage,
            maneuver=s.maneuver,
            distance_m=s.distance_to_maneuver_m,
            road_name=s.next_road_name,
            current_road_name=s.current_road_name,
            step_distance_m=s.step_distance_m,
            clock_position=(None if s.relative_bearing_deg is None
                            else clock_position_for(s.relative_bearing_deg)),
            turn_angle_deg=s.turn_angle_deg,
            orientation=s.orientation,
            landmark=s.landmark,
            needs_confirmation=s.needs_confirmation,
            remaining_route_m=s.remaining_route_m,
            remaining_route_min=max(0, (s.remaining_route_s + 59) // 60),
            off_route_m=off_route_m,
            side=turn_side(s.maneuver),
            hazard_ahead_maneuver=s.hazard_ahead_maneuver,
            hazard_ahead_m=s.hazard_ahead_m,
        )


# --------------------------------------------------------------------------
# Cue speech, Chinese.  Ported from the Android app's GuidancePhrases
# (Simplified-Chinese table).  The product voice interface is Chinese only per
# the hardware team's decision of 27 July 2026; still needs native-speaker
# review before participant testing.
# 提示语音（中文）。移植自安卓端 GuidancePhrases 简体中文表。按硬件组
# 2026-07-27 的决定，产品语音仅中文；参与者测试前仍需母语者审校。
# --------------------------------------------------------------------------

DETAIL_CONCISE = "CONCISE"
DETAIL_STANDARD = "STANDARD"
DETAIL_DETAILED = "DETAILED"

# Step counts: blind travellers are trained to think in paces for short
# distances, so cues close to an action also give an approximate step count.
# Beyond ~40 m (or under 4 steps) a count is noise, not information.
# 步数播报：盲人定向行走训练中短距离以"步"为单位，因此接近动作点的提示
# 会附带近似步数。超过约 40 米（或不足 4 步）时步数没有意义，不播报。
STEP_ANNOUNCE_MAX_M = 40.0
STEP_ANNOUNCE_MIN_STEPS = 4


def steps_for(distance_m: float, step_length_m: float = STEP_LENGTH_M) -> Optional[int]:
    """Approximate step count for a short distance, or None when a count would
    be meaningless.  Counts above 20 are rounded to the nearest 5 — the value
    is an estimate and must not sound more precise than it is.
    短距离的近似步数；无意义时返回 None。超过 20 步取 5 的倍数——这是估计值，
    不能听起来比实际更精确。"""
    if step_length_m <= 0 or distance_m <= 0 or distance_m > STEP_ANNOUNCE_MAX_M:
        return None
    steps = distance_m / step_length_m
    if steps < STEP_ANNOUNCE_MIN_STEPS:
        return None
    if steps > 20:
        return int(round(steps / 5.0) * 5)
    return int(round(steps))


def cue_message(cue: GuidanceCue, detail: str = DETAIL_STANDARD,
                step_length_m: float = STEP_LENGTH_M) -> str:
    """Build the sentence for one guidance cue.  The action is always stated
    first; detail level only controls the anchoring context around it.  Close
    to the action, distances also get an approximate step count based on the
    configured step length.
    生成一条引导提示的句子。动作永远先说；详细程度只影响附加的定位信息。
    接近动作点时，距离会按配置步长附带近似步数。"""
    label = MANEUVER_LABEL_ZH.get(cue.maneuver, MANEUVER_LABEL_ZH[Maneuver.UNKNOWN])
    # "准备：已到达目的地" reads oddly — announce approach, not completion.
    # "准备：已到达目的地"语感生硬——预告阶段说"即将到达"。
    if cue.maneuver == Maneuver.ARRIVED and cue.stage in (STAGE_EARLY, STAGE_PREPARE):
        label = "即将到达目的地"
    parts: List[str] = []

    if cue.stage == STAGE_EARLY:
        parts.append("前方{d}米，{m}".format(d=cue.distance_m, m=label))
    elif cue.stage == STAGE_PREPARE:
        parts.append("准备：{m}".format(m=label))
        prepare_steps = steps_for(cue.distance_m, step_length_m)
        if prepare_steps is not None:
            parts.append("，约{n}步".format(n=prepare_steps))
    elif cue.stage == STAGE_ACT:
        parts.append("现在：{m}".format(m=label))
    elif cue.stage == STAGE_PROGRESS:
        parts.append("继续走{d}米，然后{m}".format(d=cue.distance_m, m=label))
    elif cue.stage == STAGE_CONFIRM:
        # The road just entered; falls back to the walking direction when the
        # segment is unnamed.  (The Android SDK delivered the new road in its
        # "next road" field; with our own tracker the current step's road is
        # the road just entered.)
        # 刚进入的道路；无名路段退化为行进方向。（安卓 SDK 在"下一道路"字段
        # 里给出新道路；自建跟踪器中，当前段的道路即刚进入的道路。）
        where = cue.current_road_name or cue.road_name
        if where:
            parts.append("您现在位于{w}".format(w=where))
        elif cue.orientation:
            parts.append("您现在朝{o}方向行进".format(o=cue.orientation))
        else:
            parts.append("转弯完成")
        if cue.step_distance_m > 0:
            parts.append("，继续走{d}米".format(d=cue.step_distance_m))
            confirm_steps = steps_for(cue.step_distance_m, step_length_m)
            if confirm_steps is not None:
                parts.append("，约{n}步".format(n=confirm_steps))
        # Segment-entry preview: the first mapped attention point before the
        # next turn, when it is beyond the EARLY window (inside it, the
        # early/prepare/act ladder is about to speak anyway).
        # 进入路段时的预告：下一个转弯前第一个需留意的地图要素；若已进入预告
        # 窗口（120 米内），阶梯提示马上会播报，无需重复。
        if (cue.hazard_ahead_maneuver is not None
                and cue.hazard_ahead_m > PedestrianGuidanceEngine.EARLY_METERS):
            noun = HAZARD_NOUN_ZH.get(cue.hazard_ahead_maneuver, "需要留意的路段")
            parts.append("。前方约{d}米有{n}，请提前留意".format(
                d=int(round(cue.hazard_ahead_m / 10.0) * 10), n=noun))
    elif cue.stage == STAGE_ARRIVAL:
        arrival_m = max(cue.remaining_route_m, cue.distance_m)
        parts.append("目的地在前方约{d}米".format(d=arrival_m))
        arrival_steps = steps_for(arrival_m, step_length_m)
        if arrival_steps is not None:
            parts.append("，约{n}步".format(n=arrival_steps))
    elif cue.stage == STAGE_OFF_ROUTE:
        parts.append("已偏离路线约{d}米。请停下并转身返回路线".format(d=cue.off_route_m))

    states_direction = cue.stage in (STAGE_EARLY, STAGE_PREPARE, STAGE_ACT)
    if states_direction and cue.road_name and cue.stage != STAGE_ACT:
        parts.append("，进入{r}".format(r=cue.road_name))

    # Clock position is the most precise thing we can offer a blind walker, so
    # it survives even concise mode at the moment of action.
    # 钟点方向是能给盲人步行者的最精确信息，简洁模式下执行时刻仍保留。
    if states_direction and cue.clock_position is not None and cue.side != "NONE":
        if detail != DETAIL_CONCISE or cue.stage == STAGE_ACT:
            parts.append("，在{c}点钟方向".format(c=cue.clock_position))
    if (detail == DETAIL_DETAILED and states_direction
            and cue.turn_angle_deg is not None and cue.side != "NONE"):
        parts.append("，约{a}度".format(a=cue.turn_angle_deg))
    if (detail != DETAIL_CONCISE and cue.landmark
            and cue.stage in (STAGE_PREPARE, STAGE_ACT)):
        parts.append("，靠近{l}".format(l=cue.landmark))
    if detail == DETAIL_DETAILED and cue.stage == STAGE_CONFIRM and cue.orientation:
        parts.append("，朝向{o}".format(o=cue.orientation))

    # Hazards are always spoken, at every detail level. / 危险提示任何模式都播报。
    if cue.maneuver == Maneuver.CROSSWALK and cue.stage == STAGE_ACT:
        parts.append("。请在路缘处停下。过街前请自行确认实际路口和交通状况")
    elif cue.is_hazard and cue.stage in (STAGE_PREPARE, STAGE_ACT):
        parts.append("。继续前请确认周围环境")

    if (detail == DETAIL_DETAILED and cue.stage == STAGE_CONFIRM
            and cue.remaining_route_m > 0):
        parts.append("。剩余{d}米，约{m}分钟".format(
            d=cue.remaining_route_m, m=cue.remaining_route_min))
    return "".join(parts) + "。"


# --------------------------------------------------------------------------
# AMap Web Service client (v3 REST).  All coordinates on this boundary are
# GCJ-02 — AMap's own frame — both in requests and responses.  The fused GNSS
# position is converted with wgs84_to_gcj02() before touching this client.
# Quirk to know: AMap v3 returns [] instead of "" for absent string fields.
# 高德 Web 服务客户端（v3 REST）。此边界上的坐标一律为 GCJ-02（高德坐标系），
# 请求与响应皆然；融合后的 GNSS 位置须先经 wgs84_to_gcj02() 转换。
# 注意怪癖：v3 接口的空字符串字段会返回 []。
# --------------------------------------------------------------------------

class AmapError(Exception):
    """Raised on network failure or an AMap error status.  Messages never
    contain the URL, so the key cannot leak into logs.
    网络失败或高德返回错误状态时抛出。错误信息不含 URL，key 不会泄漏到日志。"""


def _amap_str(value) -> str:
    return value if isinstance(value, str) else ""


def _amap_float(value, default: float = 0.0) -> float:
    try:
        return float(value)
    except (TypeError, ValueError):
        return default


def _amap_int(value, default: int = 0) -> int:
    return int(_amap_float(value, float(default)))


def parse_polyline(text: str) -> List[Tuple[float, float]]:
    """AMap polylines are "lng,lat;lng,lat;..." — note longitude FIRST.
    Returned as (lat, lon) tuples, still GCJ-02.
    高德折线格式为 "经度,纬度;……"（经度在前）。返回 (纬度, 经度) 元组，仍为 GCJ-02。"""
    points: List[Tuple[float, float]] = []
    for chunk in text.split(";"):
        pieces = chunk.split(",")
        if len(pieces) != 2:
            continue
        try:
            lon, lat = float(pieces[0]), float(pieces[1])
        except ValueError:
            continue
        points.append((lat, lon))
    return points


@dataclass
class Place:
    """One POI candidate, GCJ-02. / 一个候选地点（GCJ-02 坐标）。"""

    name: str
    address: str
    lat: float
    lon: float
    distance_m: Optional[float] = None


@dataclass
class RouteStep:
    """One walking step.  ``maneuver`` is the action at the END of the step.
    一段步行路段；maneuver 是该段末端要执行的动作。"""

    index: int
    maneuver: str
    road_name: str
    next_road_name: str
    distance_m: float
    duration_s: float
    path: List[Tuple[float, float]]     # GCJ-02
    walk_type: int
    orientation: str
    needs_confirmation: bool

    @property
    def maneuver_point(self) -> Tuple[float, float]:
        return self.path[-1] if self.path else (0.0, 0.0)


@dataclass
class WalkingRoute:
    destination_name: str
    total_distance_m: float
    total_duration_s: float
    steps: List[RouteStep]
    crossing_count: int

    @property
    def destination(self) -> Tuple[float, float]:
        for step in reversed(self.steps):
            if step.path:
                return step.path[-1]
        return 0.0, 0.0


def parse_walking_route(data: Dict, destination_name: str) -> WalkingRoute:
    """Build the route model from a v3 direction/walking response.
    从 v3 步行路径规划响应构建路线模型。"""
    paths = (data.get("route") or {}).get("paths") or []
    if not paths:
        raise AmapError("AMap returned no walking path / 高德未返回步行路径")
    raw_path = paths[0]
    raw_steps = raw_path.get("steps") or []
    if not raw_steps:
        raise AmapError("AMap returned an empty step list / 高德返回的路段列表为空")

    walk_types = [_amap_int(s.get("walk_type"), 0) for s in raw_steps]
    roads = [_amap_str(s.get("road")) for s in raw_steps]
    steps: List[RouteStep] = []
    for i, raw in enumerate(raw_steps):
        is_last = i == len(raw_steps) - 1
        action = _amap_str(raw.get("action"))
        assistant = _amap_str(raw.get("assistant_action"))
        if "到达" in assistant:
            maneuver = Maneuver.ARRIVED
        else:
            maneuver = maneuver_from_amap(
                action, None if is_last else walk_types[i + 1], is_last)
        steps.append(RouteStep(
            index=i,
            maneuver=maneuver,
            road_name=roads[i],
            next_road_name="" if is_last else roads[i + 1],
            distance_m=_amap_float(raw.get("distance")),
            duration_s=_amap_float(raw.get("duration")),
            path=parse_polyline(_amap_str(raw.get("polyline"))),
            walk_type=walk_types[i],
            orientation=_amap_str(raw.get("orientation")),
            needs_confirmation=False,  # set below / 见下方
        ))
    for step in steps:
        step.needs_confirmation = step.maneuver in HAZARD_MANEUVERS
    return WalkingRoute(
        destination_name=destination_name,
        total_distance_m=_amap_float(raw_path.get("distance")),
        total_duration_s=_amap_float(raw_path.get("duration")),
        steps=steps,
        crossing_count=sum(1 for wt in walk_types if wt == 1),
    )


class AmapWebClient:
    """Thin blocking client for the five endpoints this product needs.
    Personal-tier keys are rate limited — callers keep request volume sparse.
    覆盖本产品所需五个接口的轻量阻塞客户端。个人 key 有限流，调用方需保持低频。"""

    BASE_URL = "https://restapi.amap.com"
    is_fake = False

    def __init__(self, key: str, timeout_s: float = AMAP_TIMEOUT_S) -> None:
        self._key = key
        self._timeout_s = timeout_s

    def _get(self, path: str, params: Dict[str, str], retries: int = 1) -> Dict:
        query = dict(params)
        query["key"] = self._key
        query["output"] = "json"
        url = self.BASE_URL + path + "?" + urllib.parse.urlencode(query)
        try:
            with urllib.request.urlopen(url, timeout=self._timeout_s) as response:
                data = json.loads(response.read().decode("utf-8"))
        except (urllib.error.URLError, OSError, ValueError):
            if retries > 0:
                return self._get(path, params, retries - 1)
            raise AmapError("network error calling %s / 请求 %s 失败（网络）" % (path, path))
        if data.get("status") != "1":
            raise AmapError("AMap %s error: %s (code %s)" % (
                path, _amap_str(data.get("info")) or "?", _amap_str(data.get("infocode")) or "?"))
        return data

    def search_place(self, keywords: str, city_adcode: Optional[str] = None,
                     limit: int = 5) -> List[Place]:
        """POI keyword search, optionally limited to one city.
        关键词地点搜索，可限定城市（传入 adcode）。"""
        params = {"keywords": keywords, "offset": str(limit), "page": "1",
                  "extensions": "base"}
        if city_adcode:
            params["city"] = city_adcode
            params["citylimit"] = "true"
        data = self._get("/v3/place/text", params)
        places: List[Place] = []
        for poi in data.get("pois") or []:
            location = _amap_str(poi.get("location"))
            pieces = location.split(",")
            if len(pieces) != 2:
                continue
            places.append(Place(
                name=_amap_str(poi.get("name")),
                address=_amap_str(poi.get("address")),
                lat=_amap_float(pieces[1]), lon=_amap_float(pieces[0]),
            ))
        return places

    def walking_route(self, origin_lat: float, origin_lon: float,
                      dest_lat: float, dest_lon: float,
                      destination_name: str) -> WalkingRoute:
        """Plan a walking route between two GCJ-02 points.
        规划两点间的步行路线（GCJ-02）。"""
        data = self._get("/v3/direction/walking", {
            "origin": "%.6f,%.6f" % (origin_lon, origin_lat),
            "destination": "%.6f,%.6f" % (dest_lon, dest_lat),
        })
        return parse_walking_route(data, destination_name)

    def regeo(self, lat: float, lon: float, radius_m: int = 100) -> Dict[str, str]:
        """Reverse geocode: address, adcode and the nearest mapped POI.
        逆地理编码：地址、行政区划代码与最近的地图 POI。"""
        data = self._get("/v3/geocode/regeo", {
            "location": "%.6f,%.6f" % (lon, lat),
            "radius": str(radius_m),
            "extensions": "all",
        })
        regeocode = data.get("regeocode") or {}
        component = regeocode.get("addressComponent") or {}
        nearest_name, nearest_distance = "", None
        for poi in regeocode.get("pois") or []:
            distance = _amap_float(poi.get("distance"), float("inf"))
            if nearest_distance is None or distance < nearest_distance:
                nearest_name = _amap_str(poi.get("name"))
                nearest_distance = distance
        return {
            "address": _amap_str(regeocode.get("formatted_address")),
            "adcode": _amap_str(component.get("adcode")),
            "city": _amap_str(component.get("city")) or _amap_str(component.get("province")),
            "nearest_poi": nearest_name,
            "nearest_poi_distance_m": "" if nearest_distance is None else "%.0f" % nearest_distance,
        }

    def around(self, lat: float, lon: float, keywords: str,
               radius_m: int = NEARBY_RADIUS_M, limit: int = 5) -> List[Place]:
        """Nearby search sorted by distance. / 周边搜索，按距离排序。"""
        data = self._get("/v3/place/around", {
            "location": "%.6f,%.6f" % (lon, lat),
            "keywords": keywords,
            "radius": str(radius_m),
            "sortrule": "distance",
            "offset": str(limit),
            "page": "1",
        })
        places: List[Place] = []
        for poi in data.get("pois") or []:
            location = _amap_str(poi.get("location"))
            pieces = location.split(",")
            if len(pieces) != 2:
                continue
            places.append(Place(
                name=_amap_str(poi.get("name")),
                address=_amap_str(poi.get("address")),
                lat=_amap_float(pieces[1]), lon=_amap_float(pieces[0]),
                distance_m=_amap_float(poi.get("distance"), -1.0),
            ))
        return places

    def weather(self, adcode: str) -> Dict[str, str]:
        """Live weather for one district. / 指定区县的实时天气。"""
        data = self._get("/v3/weather/weatherInfo", {"city": adcode, "extensions": "base"})
        lives = data.get("lives") or []
        if not lives:
            raise AmapError("AMap returned no live weather / 高德未返回实时天气")
        live = lives[0]
        return {key: _amap_str(live.get(key))
                for key in ("city", "weather", "temperature",
                            "winddirection", "windpower", "humidity")}


# --------------------------------------------------------------------------
# Simulated AMap client: deterministic Changsha fixtures for --demo, --selftest
# and keyless development.  Every reply reaching the user is labelled 模拟.
# The canned walking route always starts at the requested origin and runs the
# same 302 m shape (east, left turn, crosswalk, arrival) regardless of the
# requested destination — it is a simulation, not a shortest path.
# 模拟高德客户端：供 --demo、--selftest 及无 key 开发使用的确定性长沙数据。
# 所有到达用户的回复都标注"模拟"。内置步行路线始终从请求起点出发、走固定的
# 302 米形状（向东、左转、人行横道、到达），与请求的终点无关——这是模拟，
# 不是最短路径。
# --------------------------------------------------------------------------

class FakeAmapClient:
    is_fake = True

    ANCHOR = (28.18550, 112.94540)  # GCJ-02, near Yuelu district / 岳麓区附近

    def _canned_route_json(self, origin_lat: float, origin_lon: float) -> Dict:
        """A v3-shaped walking response (including the [] empty-field quirk) so
        the real parser is exercised even in simulation.
        构造 v3 格式的步行响应（含 [] 空字段怪癖），模拟模式也走真实解析器。"""
        p0 = (origin_lat, origin_lon)
        p1 = offset_position(p0[0], p0[1], 90.0, 150.0)
        p2 = offset_position(p1[0], p1[1], 0.0, 80.0)
        p3 = offset_position(p2[0], p2[1], 0.0, 12.0)
        p4 = offset_position(p3[0], p3[1], 0.0, 60.0)

        def poly(a: Tuple[float, float], b: Tuple[float, float]) -> str:
            return "%.6f,%.6f;%.6f,%.6f" % (a[1], a[0], b[1], b[0])

        return {"status": "1", "route": {"paths": [{
            "distance": "302", "duration": "236",
            "steps": [
                {"instruction": "向东步行150米左转", "orientation": "东",
                 "road": "麓山南路", "distance": "150", "duration": "115",
                 "polyline": poly(p0, p1), "action": "左转",
                 "assistant_action": [], "walk_type": "0"},
                {"instruction": "向北步行80米", "orientation": "北",
                 "road": "新民路", "distance": "80", "duration": "62",
                 "polyline": poly(p1, p2), "action": [],
                 "assistant_action": [], "walk_type": "0"},
                {"instruction": "通过人行横道", "orientation": "北",
                 "road": [], "distance": "12", "duration": "12",
                 "polyline": poly(p2, p3), "action": [],
                 "assistant_action": [], "walk_type": "1"},
                {"instruction": "向北步行60米到达目的地", "orientation": "北",
                 "road": "登山路", "distance": "60", "duration": "47",
                 "polyline": poly(p3, p4), "action": [],
                 "assistant_action": "到达目的地", "walk_type": "0"},
            ],
        }]}}

    def search_place(self, keywords: str, city_adcode: Optional[str] = None,
                     limit: int = 5) -> List[Place]:
        base_lat, base_lon = self.ANCHOR
        first = offset_position(base_lat, base_lon, 60.0, 300.0)
        second = offset_position(base_lat, base_lon, 0.0, 650.0)
        return [
            Place(name="%s南门" % keywords, address="麓山南路（模拟地址）",
                  lat=first[0], lon=first[1]),
            Place(name="%s东门" % keywords, address="新民路（模拟地址）",
                  lat=second[0], lon=second[1]),
        ][:limit]

    def walking_route(self, origin_lat: float, origin_lon: float,
                      dest_lat: float, dest_lon: float,
                      destination_name: str) -> WalkingRoute:
        return parse_walking_route(
            self._canned_route_json(origin_lat, origin_lon), destination_name)

    def regeo(self, lat: float, lon: float, radius_m: int = 100) -> Dict[str, str]:
        return {"address": "湖南省长沙市岳麓区麓山南路（模拟）",
                "adcode": "430104", "city": "长沙市",
                "nearest_poi": "岳麓书院", "nearest_poi_distance_m": "45"}

    def around(self, lat: float, lon: float, keywords: str,
               radius_m: int = NEARBY_RADIUS_M, limit: int = 5) -> List[Place]:
        first = offset_position(lat, lon, 45.0, 120.0)
        second = offset_position(lat, lon, 0.0, 300.0)
        return [
            Place(name="%s一号（模拟）" % keywords, address="模拟地址",
                  lat=first[0], lon=first[1], distance_m=120.0),
            Place(name="%s二号（模拟）" % keywords, address="模拟地址",
                  lat=second[0], lon=second[1], distance_m=300.0),
        ][:limit]

    def weather(self, adcode: str) -> Dict[str, str]:
        return {"city": "岳麓区", "weather": "多云", "temperature": "28",
                "winddirection": "东南", "windpower": "3", "humidity": "60"}


# --------------------------------------------------------------------------
# Background AMap worker.  update_gps()/update_imu() run on the sensor thread
# and must never block on HTTP, so landmark lookups and reroutes go through a
# single daemon thread; results are picked up on later guidance passes.  With
# the fake client everything runs synchronously (deterministic tests).
# 后台高德工作线程。update_gps()/update_imu() 跑在传感器线程上，绝不能被
# HTTP 阻塞，因此地标查询与重新规划走单个守护线程，结果在之后的引导判定中
# 取用。模拟客户端下全部同步执行（保证测试确定性）。
# --------------------------------------------------------------------------

class _AmapWorker:
    def __init__(self, client) -> None:
        self._client = client
        self._lock = threading.Lock()
        self._landmarks: Dict[int, str] = {}
        self._requested_landmarks: set = set()
        self._reroute_result: Optional[WalkingRoute] = None
        self._reroute_failed = False
        self._reroute_pending = False
        self._jobs: Optional["queue.Queue"] = None
        if not client.is_fake:
            self._jobs = queue.Queue()
            thread = threading.Thread(target=self._run, daemon=True,
                                      name="amap-worker")
            thread.start()

    def reset_for_route(self) -> None:
        with self._lock:
            self._landmarks.clear()
            self._requested_landmarks.clear()
            self._reroute_result = None
            self._reroute_failed = False
            self._reroute_pending = False

    # -- Landmarks / 地标 ----------------------------------------------------

    def request_landmark(self, step_index: int, lat: float, lon: float) -> None:
        with self._lock:
            if step_index in self._requested_landmarks:
                return
            self._requested_landmarks.add(step_index)
        if self._jobs is None:
            self._do_landmark(step_index, lat, lon)
        else:
            self._jobs.put(("landmark", step_index, lat, lon))

    def landmark_for(self, step_index: int) -> str:
        with self._lock:
            return self._landmarks.get(step_index, "")

    def _do_landmark(self, step_index: int, lat: float, lon: float) -> None:
        name = ""
        try:
            info = self._client.regeo(lat, lon, radius_m=int(LANDMARK_MAX_DISTANCE_M))
            distance = _amap_float(info.get("nearest_poi_distance_m"), float("inf"))
            if info.get("nearest_poi") and distance <= LANDMARK_MAX_DISTANCE_M:
                name = info["nearest_poi"]
        except AmapError:
            pass  # landmarks are optional decoration / 地标是可选修饰，失败即放弃
        with self._lock:
            self._landmarks[step_index] = name

    # -- Reroute / 重新规划 ---------------------------------------------------

    def request_reroute(self, origin_lat: float, origin_lon: float,
                        dest_lat: float, dest_lon: float, name: str) -> None:
        with self._lock:
            if self._reroute_pending:
                return
            self._reroute_pending = True
            self._reroute_failed = False
        if self._jobs is None:
            self._do_reroute(origin_lat, origin_lon, dest_lat, dest_lon, name)
        else:
            self._jobs.put(("reroute", origin_lat, origin_lon, dest_lat, dest_lon, name))

    def take_reroute(self) -> Tuple[Optional[WalkingRoute], bool]:
        """(new route or None, failed_flag) — each reported once.
        （新路线或 None，失败标志）——每个结果只报告一次。"""
        with self._lock:
            result, failed = self._reroute_result, self._reroute_failed
            self._reroute_result, self._reroute_failed = None, False
            if result is not None or failed:
                self._reroute_pending = False
            return result, failed

    def _do_reroute(self, origin_lat: float, origin_lon: float,
                    dest_lat: float, dest_lon: float, name: str) -> None:
        try:
            route = self._client.walking_route(origin_lat, origin_lon,
                                               dest_lat, dest_lon, name)
            with self._lock:
                self._reroute_result = route
        except AmapError:
            with self._lock:
                self._reroute_failed = True

    def _run(self) -> None:
        while True:
            job = self._jobs.get()
            try:
                if job[0] == "landmark":
                    self._do_landmark(job[1], job[2], job[3])
                elif job[0] == "reroute":
                    self._do_reroute(job[1], job[2], job[3], job[4], job[5])
            except Exception:  # noqa: BLE001 — the worker must never die / 工作线程不可退出
                pass
            time.sleep(AMAP_WORKER_SPACING_S)


# --------------------------------------------------------------------------
# Route tracker: matches the fused position onto the route polyline, advances
# through steps, and produces GuidanceSnapshots for the engine.  This replaces
# what the AMap Android SDK's navigation callbacks did for the phone app.
# 路线跟踪器：把融合位置匹配到路线折线上、推进路段，并为引导引擎生成
# GuidanceSnapshot。它承担了手机端由高德导航 SDK 回调完成的工作。
# --------------------------------------------------------------------------

def final_bearing_of_path(path: List[Tuple[float, float]]) -> Optional[float]:
    """Bearing of the last non-degenerate segment. / 折线末段的方位角。"""
    for i in range(len(path) - 1, 0, -1):
        if path[i] != path[i - 1]:
            return bearing_deg(path[i - 1][0], path[i - 1][1],
                               path[i][0], path[i][1])
    return None


class RouteTracker:
    ADVANCE_REMAINING_M = 3.0     # end-of-step snap distance / 判定走完一段的剩余距离
    ADVANCE_MARGIN_M = 3.0        # next step must match this much better / 下一段需明显更贴合
    ADVANCE_MIN_ALONG_M = 5.0     # ...and this far along / 且已沿下一段走出此距离

    def __init__(self, route: WalkingRoute) -> None:
        self.route = route
        self.step_index = 0
        self.finished = False
        self._geom_lengths = [path_length_m(step.path) for step in route.steps]

    @property
    def current_step(self) -> RouteStep:
        return self.route.steps[self.step_index]

    def remaining_route_m(self, remaining_in_step: float) -> float:
        return remaining_in_step + sum(self._geom_lengths[self.step_index + 1:])

    HAZARD_PREVIEW_HORIZON_M = 800.0

    def hazard_ahead(self, step_index: int,
                     remaining_in_step: float) -> Optional[Tuple[str, int]]:
        """First mapped attention point (crossing, stairs, bridge...) between
        here and the next turn, within the preview horizon.  The scan stops at
        turns because the stretch after a turn gets its own preview at its
        CONFIRM cue.  Unmarked side roads are NOT in the map data — detecting
        those stays the job of the cane and the backpack's local sensing.
        从当前位置到下一个转弯之间第一个需留意的地图要素（过街、楼梯、天桥……），
        限定在预告视距内。扫描到转弯即停止——转弯后的新路段会在其"确认"提示中
        获得自己的预告。未标注的支路口不在地图数据里——探测它们仍是盲杖和背包
        本地感知的职责。"""
        distance = max(0.0, remaining_in_step)
        for i in range(step_index, len(self.route.steps)):
            if distance > self.HAZARD_PREVIEW_HORIZON_M:
                return None
            maneuver = self.route.steps[i].maneuver
            if maneuver in HAZARD_MANEUVERS:
                return maneuver, int(round(distance))
            if (turn_side(maneuver) != "NONE"
                    or maneuver in (Maneuver.U_TURN, Maneuver.ARRIVED)):
                return None
            if i + 1 < len(self.route.steps):
                distance += self._geom_lengths[i + 1]
        return None

    def on_position(self, lat: float, lon: float,
                    heading_deg_value: Optional[float]) -> GuidanceSnapshot:
        """Consume one GCJ-02 position, advance steps, emit a snapshot.
        输入一个 GCJ-02 位置，推进路段并生成快照。"""
        steps = self.route.steps
        drift, along = project_point_to_path(steps[self.step_index].path, lat, lon)
        remaining = max(0.0, self._geom_lengths[self.step_index] - along)

        # Step advancement: finish the segment, or be clearly better matched to
        # the next one (handles cutting a corner past the maneuver point).
        # 路段推进：走完当前段，或明显更贴合下一段（处理转弯抹角越过转折点）。
        for _ in range(len(steps)):
            if self.step_index >= len(steps) - 1:
                break
            advanced = False
            if remaining <= self.ADVANCE_REMAINING_M:
                advanced = True
            else:
                next_drift, next_along = project_point_to_path(
                    steps[self.step_index + 1].path, lat, lon)
                if (next_drift + self.ADVANCE_MARGIN_M < drift
                        and next_along > self.ADVANCE_MIN_ALONG_M):
                    advanced = True
            if not advanced:
                break
            self.step_index += 1
            drift, along = project_point_to_path(steps[self.step_index].path, lat, lon)
            remaining = max(0.0, self._geom_lengths[self.step_index] - along)

        if (self.step_index == len(steps) - 1
                and remaining <= ROUTE_FINISH_M):
            self.finished = True

        step = steps[self.step_index]
        next_step = (steps[self.step_index + 1]
                     if self.step_index + 1 < len(steps) else None)

        relative_bearing: Optional[int] = None
        turn_angle: Optional[int] = None
        if next_step is not None:
            next_bearing = initial_bearing_of_path(next_step.path)
            if next_bearing is not None:
                if heading_deg_value is not None:
                    relative_bearing = int(round(
                        angle_diff_deg(heading_deg_value, next_bearing)))
                current_end = final_bearing_of_path(step.path)
                if current_end is not None:
                    turn_angle = abs(int(round(
                        angle_diff_deg(current_end, next_bearing))))

        remaining_route = self.remaining_route_m(remaining)
        total = max(1.0, self.route.total_distance_m)
        remaining_s = int(self.route.total_duration_s * remaining_route / total)
        drift_int = int(round(drift))
        hazard = self.hazard_ahead(self.step_index, remaining)
        return GuidanceSnapshot(
            step_index=self.step_index,
            maneuver=step.maneuver,
            distance_to_maneuver_m=int(round(remaining)),
            next_road_name=step.next_road_name,
            current_road_name=step.road_name,
            step_distance_m=int(round(step.distance_m or self._geom_lengths[self.step_index])),
            orientation=step.orientation,
            relative_bearing_deg=relative_bearing,
            turn_angle_deg=turn_angle,
            landmark="",  # attached by the caller from the worker cache / 由调用方从地标缓存填充
            needs_confirmation=step.needs_confirmation,
            remaining_route_m=int(round(remaining_route)),
            remaining_route_s=remaining_s,
            off_route_m=(drift_int if drift_int >= PedestrianGuidanceEngine.OFF_ROUTE_MINIMUM_METERS
                         else None),
            hazard_ahead_maneuver=None if hazard is None else hazard[0],
            hazard_ahead_m=0 if hazard is None else hazard[1],
        )


# --------------------------------------------------------------------------
# Text command interface.  Deterministic keyword matching on purpose: the
# cloud must never be required for the assistant to answer, and behaviour
# must be testable.  Replies mirror the input language (English / Chinese).
# 文本指令接口：刻意采用确定性的关键词匹配——回答绝不依赖云端，行为可测试。
# 回复语言跟随输入语言（英文 / 中文）。
# --------------------------------------------------------------------------

def _is_chinese(text: str) -> bool:
    return any("一" <= ch <= "鿿" for ch in text)


# Product voice commands are Chinese (hardware-team decision, 27 July 2026).
# The English keywords below are kept ONLY as an engineering/debug convenience;
# every reply is Chinese.
# 产品语音指令为中文（硬件组 2026-07-27 决定）。下方英文关键词仅供工程调试；
# 所有回复均为中文。
_DEST_PATTERNS_EN = ("take me to ", "navigate to ", "guide me to ", "go to ", "bring me to ")
_DEST_PATTERNS_ZH = ("带我去", "帶我去", "我要去", "导航到", "導航到", "去")
# Question words that must never be treated as a place name ("我要去哪里").
# 不能被当作地名的疑问词（如"我要去哪里"）。
_NON_DESTINATIONS = {"哪", "哪里", "哪儿", "哪裡", "那里", "哪个", "什么地方"}

_CONFIRM_WORDS = ("确认", "确定", "好的", "是的", "就这个", "选这个", "confirm", "yes")
_CONFIRM_EXACT = ("好", "是", "嗯", "ok", "okay", "行")
_NEXT_WORDS = ("下一个", "下一條", "下一条", "换一个", "換一個", "next")
_START_WORDS = ("开始", "出发", "出發", "走吧", "start")
_START_EXACT = ("go", "走")

# Spoken nearby-essentials categories -> AMap search keyword.  One category per
# request, never "everything nearby" (project rule: no scene dumping).
# 口语类别 -> 高德搜索关键词。每次只查一类，绝不播报"附近所有地点"。
_NEARBY_CATEGORIES: Tuple[Tuple[str, str], ...] = (
    ("厕所", "公共厕所"), ("洗手间", "公共厕所"), ("卫生间", "公共厕所"), ("toilet", "公共厕所"),
    ("公交", "公交站"), ("bus", "公交站"),
    ("地铁", "地铁站"), ("metro", "地铁站"), ("subway", "地铁站"),
    ("药店", "药店"), ("药房", "药店"), ("pharmacy", "药店"),
    ("医院", "医院"), ("hospital", "医院"),
    ("超市", "超市"), ("supermarket", "超市"),
)

_STATE_ZH = {
    "IDLE": "空闲",
    "CHOOSING": "等待选择目的地",
    "READY": "等待出发",
    "NAVIGATING": "导航中",
    "PAUSED": "已暂停",
    "ARRIVED": "已到达",
}


class SolePrecisionCore:
    """Facade the hardware team integrates against. / 硬件组集成时对接的门面类。

    Dialog states / 对话状态:
        IDLE -> CHOOSING (candidate offered / 播报候选地点)
             -> READY    (route planned, waiting for 开始 / 路线已规划，等待出发)
             -> NAVIGATING <-> PAUSED
             -> ARRIVED -> IDLE
    """

    def __init__(self, amap_client=None,
                 step_length_m: float = STEP_LENGTH_M) -> None:
        self.tracker = PositionTracker(step_length_m)
        self.amap = amap_client
        self.worker = _AmapWorker(amap_client) if amap_client is not None else None
        self.state = "IDLE"
        self.detail = DETAIL_STANDARD
        self.destination_name = ""
        self.engine = PedestrianGuidanceEngine()
        self.route: Optional[WalkingRoute] = None
        self.route_tracker: Optional[RouteTracker] = None
        self._destination_gcj: Optional[Tuple[float, float]] = None
        self._candidates: List[Place] = []
        self._candidate_index = 0
        self._announcements: Deque[Tuple[str, str]] = deque()  # (priority, text)
        self._last_reply = ""
        self._last_cue_sentence = ""
        self._last_remaining_m: Optional[int] = None
        self._last_remaining_s: Optional[int] = None
        self._last_guidance_ts = float("-inf")
        self._last_reroute_ts = float("-inf")
        self._regeo_cache: Optional[Tuple[float, float, float, Dict[str, str]]] = None
        self._arrival_announced = False

    # -- Sensor inputs / 传感器输入 -------------------------------------------

    def update_gps(self, fix: GpsSample) -> None:
        self.tracker.update_gps(fix)
        self._on_position_changed(force=True)

    def update_imu(self, sample: ImuSample) -> None:
        self.tracker.update_imu(sample)
        self._on_position_changed(force=False)

    def current_position(self) -> Optional[PositionEstimate]:
        return self.tracker.current_position()

    # -- Announcements (unprompted speech) / 主动播报 --------------------------

    def pop_announcements(self) -> List[Dict[str, str]]:
        """Queued guidance sentences, oldest first, each with a priority
        (critical / high / normal).  Critical should interrupt running TTS.
        排队的引导语句（先进先出），每条带优先级；critical 建议打断当前语音。"""
        drained = [{"priority": priority, "text": text}
                   for priority, text in self._announcements]
        self._announcements.clear()
        return drained

    def pop_pending_announcement(self) -> str:
        """Back-compat plain-text form: all queued sentences joined.
        兼容旧接口：将排队语句合并为一个字符串返回。"""
        return "".join(item["text"] for item in self.pop_announcements())

    def _announce(self, text: str, priority: str = "normal") -> None:
        if not text:
            return
        # Bound the queue: drop the oldest NORMAL entries first, never critical.
        # 限制队列长度：先丢最旧的 normal 条目，绝不丢 critical。
        while len(self._announcements) >= 12:
            for i, (existing_priority, _) in enumerate(self._announcements):
                if existing_priority == "normal":
                    del self._announcements[i]
                    break
            else:
                self._announcements.popleft()
        self._announcements.append((priority, text))

    # -- Guidance loop / 引导循环 ----------------------------------------------

    def _on_position_changed(self, force: bool) -> None:
        if self.state != "NAVIGATING":
            return
        if self.route_tracker is None:
            self._check_arrival_without_route()
            return
        position = self.current_position()
        if position is None:
            return
        if not force and position.timestamp - self._last_guidance_ts < GUIDANCE_MIN_INTERVAL_S:
            return
        self._last_guidance_ts = position.timestamp
        self._adopt_reroute_result()
        if self.route_tracker is None:  # reroute adoption can fail / 采纳失败时防御
            return

        snapshot = self.route_tracker.on_position(
            position.gcj_latitude, position.gcj_longitude, position.heading_deg)
        self._last_remaining_m = snapshot.remaining_route_m
        self._last_remaining_s = snapshot.remaining_route_s

        if self.route_tracker.finished:
            self._finish_route()
            return

        self._prefetch_landmarks(snapshot.step_index)
        if self.worker is not None:
            snapshot.landmark = self.worker.landmark_for(snapshot.step_index)

        cue = self.engine.on_snapshot(snapshot)
        if cue is None:
            return
        # Step counts follow the same configurable step length as the fusion,
        # so tuning it per user adjusts both consistently.
        # 步数与融合共用同一个可配置步长，按用户调整时两者保持一致。
        sentence = cue_message(cue, self.detail, self.tracker.step_length_m)
        self._last_cue_sentence = sentence
        self._announce(sentence, cue.priority)
        if (cue.stage == STAGE_OFF_ROUTE and cue.off_route_m >= REROUTE_DRIFT_M):
            self._maybe_request_reroute(position)

    def _prefetch_landmarks(self, step_index: int) -> None:
        if self.worker is None or self.route is None:
            return
        for index in (step_index, step_index + 1):
            if index < len(self.route.steps):
                point = self.route.steps[index].maneuver_point
                self.worker.request_landmark(index, point[0], point[1])

    def _maybe_request_reroute(self, position: PositionEstimate) -> None:
        if (self.worker is None or self.route is None
                or position.timestamp - self._last_reroute_ts < REROUTE_COOLDOWN_S):
            return
        self._last_reroute_ts = position.timestamp
        dest_lat, dest_lon = self.route.destination
        self.worker.request_reroute(position.gcj_latitude, position.gcj_longitude,
                                    dest_lat, dest_lon, self.route.destination_name)
        self._announce("正在重新规划路线。", "high")

    def _adopt_reroute_result(self) -> None:
        if self.worker is None:
            return
        new_route, failed = self.worker.take_reroute()
        if new_route is not None and new_route.steps:
            self.route = new_route
            self.route_tracker = RouteTracker(new_route)
            self.engine.reset()
            self.worker.reset_for_route()
            self._prefetch_landmarks(0)
            self._announce("已重新规划路线：全程约%d米，步行约%d分钟。"
                           % (int(new_route.total_distance_m),
                              max(1, int((new_route.total_duration_s + 59) // 60))),
                           "high")
        elif failed:
            self._announce("路线重新规划失败。请停下并原路返回路线。", "high")

    def _finish_route(self) -> None:
        name = self.destination_name or "目的地"
        self._announce("已到达%s附近。请使用盲杖等辅助工具确认具体入口。导航结束。" % name,
                       "critical")
        self.state = "ARRIVED"
        self.route = None
        self.route_tracker = None
        self._last_cue_sentence = ""

    # -- Destination entry points / 目的地入口 ---------------------------------

    def set_destination(self, name: str, latitude: Optional[float] = None,
                        longitude: Optional[float] = None) -> None:
        """Programmatic destination (JSON ``set_destination``).  Coordinates are
        GCJ-02 — they come from AMap search results.  If the AMap client and a
        position are available a walking route is planned immediately (blocking,
        typically well under a second) and guidance starts; otherwise the core
        falls back to straight-line arrival detection only.
        程序化设置目的地（JSON set_destination）。坐标为 GCJ-02（来自高德搜索
        结果）。若已配置高德客户端且有定位，会立即规划步行路线（阻塞调用，
        通常远小于一秒）并开始引导；否则退化为仅按直线距离判断到达。"""
        self.destination_name = name.strip()
        self._arrival_announced = False
        self._destination_gcj = None
        if latitude is not None and longitude is not None:
            self._destination_gcj = (float(latitude), float(longitude))
        self.route = None
        self.route_tracker = None
        self.state = "NAVIGATING"
        if self.amap is None or self._destination_gcj is None:
            return
        position = self.current_position()
        if position is None:
            return
        try:
            route = self.amap.walking_route(
                position.gcj_latitude, position.gcj_longitude,
                self._destination_gcj[0], self._destination_gcj[1],
                self.destination_name)
        except AmapError:
            self._announce("路线规划失败，只能按直线距离提示到达。", "high")
            return
        self.route = route
        self._begin_guidance(announce_summary=True)

    # -- Text interface / 文本指令接口 -----------------------------------------

    def process_text(self, text: str) -> str:
        """One spoken command in, one Chinese sentence out.  Any guidance cues
        queued since the last call are prepended so pure-text integrations
        still hear them (JSON integrations drain them via ``announcements``).
        输入一句口语指令，返回一句中文回复。上次调用以来排队的引导提示会
        拼在回复前，纯文本集成也不会漏听（JSON 集成用 announcements 字段）。"""
        raw = text.strip()
        lower = raw.lower().rstrip("?？!！。.")
        if not lower:
            return self._remember("请说出指令。说“帮助”可以听到可用指令。")
        prefix = self.pop_pending_announcement()
        reply = self._dispatch(raw, lower)
        self._remember(reply)
        return prefix + reply

    # -- Internals / 内部实现 ---------------------------------------------------

    def _remember(self, reply: str) -> str:
        self._last_reply = reply
        return reply

    @staticmethod
    def _contains(raw: str, lower: str, keys: Tuple[str, ...]) -> bool:
        return any((key in lower) if key.isascii() else (key in raw) for key in keys)

    @staticmethod
    def _is_exactly(raw: str, lower: str, keys: Tuple[str, ...]) -> bool:
        bare = raw.strip("?？!！。.，, ")
        return lower in keys or bare in keys

    def _dispatch(self, raw: str, lower: str) -> str:
        if self._contains(raw, lower, ("帮助", "幫助", "你能做什么", "help")):
            return ("您可以说：带我去某地；确认；下一个；开始；我在哪里；还有多远；"
                    "附近的厕所、公交站、地铁站、药店、医院或超市；天气；"
                    "暂停；继续；停止；重复；状态；简洁模式、标准模式或详细模式。")

        detail_reply = self._handle_detail_mode(raw)
        if detail_reply:
            return detail_reply

        if self._contains(raw, lower, ("停止", "取消", "结束导航", "結束導航",
                                       "stop", "cancel")):
            return self._handle_stop()

        # A new destination phrase always wins, in any state.
        # 新的目的地指令在任何状态下都优先。
        destination = self._extract_destination(raw, lower)
        if destination:
            return self._start_search(destination)

        if self.state == "CHOOSING":
            if (self._contains(raw, lower, _CONFIRM_WORDS)
                    or self._is_exactly(raw, lower, _CONFIRM_EXACT)):
                return self._plan_route_for_current_candidate()
            if self._contains(raw, lower, _NEXT_WORDS):
                return self._next_candidate()

        if self.state == "READY":
            if (self._contains(raw, lower, _START_WORDS)
                    or self._is_exactly(raw, lower, _START_EXACT)
                    or self._contains(raw, lower, _CONFIRM_WORDS)
                    or self._is_exactly(raw, lower, _CONFIRM_EXACT)):
                return self._start_navigation()
            if self._contains(raw, lower, _NEXT_WORDS):
                return "当前只规划了一条路线。说“开始”出发，或说“取消”重新选择目的地。"

        # 开始 outside READY must not fall through to "I did not understand"
        # (observed in a live run: a repeated 开始 mid-navigation).
        # READY 之外说"开始"不能落入"没有听懂"（实测中出现过导航中重复说"开始"）。
        if (self._contains(raw, lower, _START_WORDS)
                or self._is_exactly(raw, lower, _START_EXACT)):
            if self.state == "NAVIGATING":
                return "导航已在进行中。说“暂停”可以暂停，说“停止”结束导航。"
            if self.state == "PAUSED":
                self.state = "NAVIGATING"
                return "导航已恢复。"

        if self._contains(raw, lower, ("去哪", "目的地", "要去哪",
                                       "where do i need to go", "where am i going",
                                       "destination")):
            if self.state in ("READY", "NAVIGATING", "PAUSED") and self.destination_name:
                return "您的目的地是%s。" % self.destination_name
            return "还没有设置目的地。请说：带我去，然后说出地点名称。"

        if self._contains(raw, lower, ("我在哪", "当前位置", "我的位置",
                                       "where am i", "current location")):
            return self._where_am_i()

        if self._contains(raw, lower, ("还有多远", "还要多久", "剩余距离",
                                       "how far", "how long")):
            return self._how_far()

        nearby_reply = self._handle_nearby(raw, lower)
        if nearby_reply:
            return nearby_reply

        if self._contains(raw, lower, ("天气", "天氣", "weather")):
            return self._weather()

        if self._contains(raw, lower, ("暂停", "暫停", "pause")):
            if self.state == "NAVIGATING":
                self.state = "PAUSED"
                return "导航已暂停。准备好后请说“继续”。"
            return "当前没有正在进行的导航。"

        if self._contains(raw, lower, ("继续", "繼續", "continue", "resume")):
            if self.state == "PAUSED":
                self.state = "NAVIGATING"
                return "导航已恢复。"
            return "导航未处于暂停状态。"

        if self._contains(raw, lower, ("重复", "重複", "再说一遍", "repeat")):
            if self.state in ("NAVIGATING", "PAUSED") and self._last_cue_sentence:
                return self._last_cue_sentence
            if self._last_reply:
                return self._last_reply
            return "还没有可以重复的内容。"

        if self._contains(raw, lower, ("状态", "狀態", "status")):
            return self._status()

        if self.state == "CHOOSING":
            return "请说“确认”选择这个地点，说“下一个”听下一个结果，或说“取消”。"
        if self.state == "READY":
            return "路线已准备好。说“开始”出发，或说“取消”。"
        return "抱歉，我没有听懂。说“帮助”可以听到我能做什么。"

    def _handle_detail_mode(self, raw: str) -> str:
        if "模式" not in raw and "播报" not in raw and "播報" not in raw:
            return ""
        if "简洁" in raw or "簡潔" in raw:
            self.detail = DETAIL_CONCISE
            return "已切换到简洁播报：只保留准备、执行和危险提示。"
        if "详细" in raw or "詳細" in raw:
            self.detail = DETAIL_DETAILED
            return "已切换到详细播报：包含进度、转弯角度和剩余路程。"
        if "标准" in raw or "標準" in raw:
            self.detail = DETAIL_STANDARD
            return "已切换到标准播报。"
        return ""

    def _handle_stop(self) -> str:
        previous_state = self.state
        self.state = "IDLE"
        self.destination_name = ""
        self._destination_gcj = None
        self.route = None
        self.route_tracker = None
        self._candidates = []
        self._candidate_index = 0
        self._last_cue_sentence = ""
        self._last_remaining_m = None
        self._last_remaining_s = None
        if previous_state in ("CHOOSING", "READY"):
            return "已取消。"
        if previous_state in ("NAVIGATING", "PAUSED"):
            return "导航已停止。"
        return "当前没有正在进行的导航。"

    @staticmethod
    def _extract_destination(raw: str, lower: str) -> str:
        for pattern in _DEST_PATTERNS_EN:
            idx = lower.find(pattern)
            if idx >= 0:
                name = raw[idx + len(pattern):].strip().strip("?？!！。.,")
                if name and name not in _NON_DESTINATIONS:
                    return name
        # Chinese: longest patterns first so 带我去 wins over bare 去.
        # 中文按模式长度倒序匹配，让"带我去"优先于单字"去"。
        for pattern in sorted(_DEST_PATTERNS_ZH, key=len, reverse=True):
            idx = raw.find(pattern)
            if idx >= 0:
                name = raw[idx + len(pattern):].strip().strip("?？!！。.,")
                # Bare 去 needs at least two following characters to avoid
                # swallowing phrases like 去哪 (handled as a question).
                # 单字"去"后至少需要两个字，避免吞掉"去哪"这类疑问句。
                if (name and name not in _NON_DESTINATIONS
                        and (pattern != "去" or len(name) >= 2)):
                    return name
        return ""

    # -- Search and candidates / 搜索与候选 ------------------------------------

    def _sim(self, text: str) -> str:
        """Simulation marker — mock data must always announce itself.
        模拟标记——模拟数据必须自我声明，绝不冒充真实数据。"""
        if self.amap is not None and self.amap.is_fake:
            return "（模拟）" + text
        return text

    def _start_search(self, name: str) -> str:
        if self.amap is None:
            return ("地图搜索不可用：未配置高德 Web 服务 key。"
                    "请设置环境变量 AMAP_WEB_KEY，或在脚本旁放置 amap_key.txt 文件。")
        was_active = self.state in ("NAVIGATING", "PAUSED")
        self.route = None
        self.route_tracker = None
        self._last_cue_sentence = ""
        self._last_remaining_m = None
        self._last_remaining_s = None
        position = self.current_position()
        try:
            places = self.amap.search_place(name, self._current_adcode(position) or None)
        except AmapError:
            self.state = "IDLE"
            return self._sim("搜索失败，网络或地图服务暂时不可用。请稍后再试。")
        if not places:
            self.state = "IDLE"
            return self._sim("没有找到%s。请换个说法再试。" % name)
        if position is not None:
            for place in places:
                place.distance_m = haversine_m(position.gcj_latitude, position.gcj_longitude,
                                               place.lat, place.lon)
        self._candidates = places[:5]
        self._candidate_index = 0
        self.state = "CHOOSING"
        prefix = "已停止当前导航。" if was_active else ""
        return prefix + self._describe_candidate(first=True)

    def _describe_candidate(self, first: bool = False) -> str:
        place = self._candidates[self._candidate_index]
        total = len(self._candidates)
        parts: List[str] = []
        if first:
            parts.append("为您找到%d个结果。" % total)
        parts.append("第%d个：%s" % (self._candidate_index + 1, place.name))
        if place.address:
            parts.append("，%s" % place.address)
        if place.distance_m is not None and place.distance_m >= 0:
            parts.append("，距离约%d米" % int(round(place.distance_m)))
            direction = self._direction_phrase(place.lat, place.lon)
            if direction:
                parts.append(direction)
        parts.append("。确认请说“确认”，换下一个请说“下一个”。" if first
                     else "。说“确认”或“下一个”。")
        return self._sim("".join(parts))

    def _next_candidate(self) -> str:
        if not self._candidates:
            return "没有可选择的结果。请先说：带我去某地。"
        self._candidate_index += 1
        wrapped = self._candidate_index >= len(self._candidates)
        if wrapped:
            self._candidate_index = 0
        prefix = "已经是最后一个结果，回到第一个。" if wrapped else ""
        return prefix + self._describe_candidate()

    def _direction_phrase(self, lat: float, lon: float) -> str:
        position = self.current_position()
        if position is None:
            return ""
        target_bearing = bearing_deg(position.gcj_latitude, position.gcj_longitude, lat, lon)
        if position.heading_deg is not None:
            relative = int(round(angle_diff_deg(position.heading_deg, target_bearing)))
            return "，在%d点钟方向" % clock_position_for(relative)
        return "，在%s方向" % bearing_to_compass_zh(target_bearing)

    # -- Route planning and navigation start / 路线规划与出发 -------------------

    def _plan_route_for_current_candidate(self) -> str:
        if not self._candidates:
            return "没有可确认的地点。请先说：带我去某地。"
        position = self.current_position()
        if position is None:
            return "还没有定位信号，无法规划路线。请到开阔处稍候，再说“确认”。"
        place = self._candidates[self._candidate_index]
        try:
            route = self.amap.walking_route(
                position.gcj_latitude, position.gcj_longitude,
                place.lat, place.lon, place.name)
        except AmapError:
            return self._sim("路线规划失败，网络或地图服务暂时不可用。请稍后再说“确认”重试。")
        self.route = route
        self.route_tracker = None
        self.destination_name = place.name
        self._destination_gcj = (place.lat, place.lon)
        self.state = "READY"
        return self._sim(self._route_summary_sentence(route) + "说“开始”出发。")

    @staticmethod
    def _route_summary_sentence(route: WalkingRoute) -> str:
        minutes = max(1, int((route.total_duration_s + 59) // 60))
        sentence = "路线已规划：全程约%d米，步行约%d分钟，共%d段。" % (
            int(route.total_distance_m), minutes, len(route.steps))
        if route.crossing_count:
            sentence += "沿途包含%d处人行横道。" % route.crossing_count
        return sentence

    def _start_navigation(self) -> str:
        if self.route is None:
            return "还没有规划好的路线。请先说：带我去某地。"
        intro = self._begin_guidance()
        return self._sim(intro)

    def _begin_guidance(self) -> str:
        """Arms the tracker/engine for the planned route and returns the spoken
        departure sentence.  Callers already hold a non-None ``self.route``.
        为已规划路线启动跟踪器与引导引擎，返回出发播报句。调用方需保证
        self.route 非空。"""
        route = self.route
        self.route_tracker = RouteTracker(route)
        self.engine.reset()
        # The departure sentence already states the first leg, so the first
        # PROGRESS cue must wait until real distance has been walked.
        # 出发播报已说明第一段，首条进度提示需等真正走出一段距离后再出现。
        self.engine.prime_progress(int(round(route.steps[0].distance_m)))
        if self.worker is not None:
            self.worker.reset_for_route()
        self._prefetch_landmarks(0)
        self.state = "NAVIGATING"
        self.destination_name = route.destination_name
        if self._destination_gcj is None:
            self._destination_gcj = route.destination
        self._arrival_announced = False
        self._last_cue_sentence = ""
        self._last_remaining_m = int(route.total_distance_m)
        self._last_remaining_s = int(route.total_duration_s)
        self._last_guidance_ts = float("-inf")
        self._last_reroute_ts = float("-inf")
        first = route.steps[0]
        parts = ["导航开始。"]
        if first.orientation:
            parts.append("向%s方向" % first.orientation)
        if first.road_name:
            parts.append("沿%s" % first.road_name)
        parts.append("直行约%d米。" % int(round(first.distance_m)))
        # Departure hazard preview, same rule as segment-entry confirms.
        # 出发时的路段预告，与转弯确认时的规则一致。
        hazard = self.route_tracker.hazard_ahead(0, path_length_m(first.path))
        if hazard is not None and hazard[1] > PedestrianGuidanceEngine.EARLY_METERS:
            parts.append("前方约%d米有%s，请提前留意。"
                         % (int(round(hazard[1] / 10.0) * 10),
                            HAZARD_NOUN_ZH.get(hazard[0], "需要留意的路段")))
        parts.append("请以盲杖和周围环境为准，地图信息不能代替安全确认。")
        return "".join(parts)

    # -- Information commands / 信息类指令 --------------------------------------

    def _regeo_cached(self, position: PositionEstimate) -> Optional[Dict[str, str]]:
        """Reverse geocode with a small cache so repeated questions and adcode
        lookups do not burn the key's daily quota.
        带缓存的逆地理编码，避免重复提问烧掉 key 的每日配额。"""
        if self.amap is None:
            return None
        if self._regeo_cache is not None:
            cached_lat, cached_lon, cached_ts, cached_info = self._regeo_cache
            if (haversine_m(cached_lat, cached_lon,
                            position.gcj_latitude, position.gcj_longitude) < 250.0
                    and abs(position.timestamp - cached_ts) < 600.0):
                return cached_info
        try:
            info = self.amap.regeo(position.gcj_latitude, position.gcj_longitude)
        except AmapError:
            return None
        self._regeo_cache = (position.gcj_latitude, position.gcj_longitude,
                             position.timestamp, info)
        return info

    def _current_adcode(self, position: Optional[PositionEstimate]) -> str:
        if position is None:
            return ""
        info = self._regeo_cached(position)
        return "" if info is None else info.get("adcode", "")

    def _where_am_i(self) -> str:
        position = self.current_position()
        if position is None:
            return "还没有获取到GPS位置。"
        info = self._regeo_cached(position) if self.amap is not None else None
        if info is not None and info.get("address"):
            parts = ["您在%s附近" % info["address"]]
            if info.get("nearest_poi"):
                distance = info.get("nearest_poi_distance_m", "")
                parts.append("，最近的地标是%s" % info["nearest_poi"])
                if distance:
                    parts.append("，约%s米" % distance)
            if position.horizontal_accuracy_m is not None:
                parts.append("。定位精度约%d米" % int(round(position.horizontal_accuracy_m)))
            parts.append("。这只是地图信息，不能代替对周围环境的安全确认。")
            return self._sim("".join(parts))
        heading = ("" if position.heading_deg is None
                   else "，朝向%.0f度" % position.heading_deg)
        return ("您位于纬度%.5f，经度%.5f（地图坐标%.5f，%.5f）%s。定位来源：%s。"
                "这只是地图信息，不能代替对周围环境的安全确认。"
                % (position.latitude, position.longitude,
                   position.gcj_latitude, position.gcj_longitude,
                   heading, position.source))

    def _how_far(self) -> str:
        if self.state == "READY" and self.route is not None:
            return self._sim(
                "到%s全程约%d米，步行约%d分钟。说“开始”出发。"
                % (self.destination_name, int(self.route.total_distance_m),
                   max(1, int((self.route.total_duration_s + 59) // 60))))
        if self.state in ("NAVIGATING", "PAUSED"):
            if self._last_remaining_m is not None and self.route is not None:
                minutes = max(1, int(((self._last_remaining_s or 0) + 59) // 60))
                return "到%s还有约%d米，步行大约%d分钟。" % (
                    self.destination_name, self._last_remaining_m, minutes)
            position = self.current_position()
            if position is not None and self._destination_gcj is not None:
                distance = haversine_m(position.gcj_latitude, position.gcj_longitude,
                                       self._destination_gcj[0], self._destination_gcj[1])
                minutes = max(1, int(round(distance / 1.2 / 60.0)))
                return "到%s的直线距离约%d米，步行大约%d分钟。" % (
                    self.destination_name, int(distance), minutes)
            return "暂时无法计算距离——需要GPS定位。"
        return "还没有设置目的地。请说：带我去，然后说出地点名称。"

    def _handle_nearby(self, raw: str, lower: str) -> str:
        """Focused one-category nearby search; never announces everything.
        单类别周边搜索；绝不播报周围的一切。"""
        if not self._contains(raw, lower, ("附近", "最近", "nearby", "nearest")):
            return ""
        keyword = ""
        for spoken, search_key in _NEARBY_CATEGORIES:
            if (spoken in lower) if spoken.isascii() else (spoken in raw):
                keyword = search_key
                break
        if not keyword:
            return "请说出要找的类别：附近的厕所、公交站、地铁站、药店、医院或超市。"
        if self.amap is None:
            return "周边搜索不可用：未配置高德 Web 服务 key。"
        position = self.current_position()
        if position is None:
            return "还没有定位信号，无法搜索周边。"
        try:
            places = self.amap.around(position.gcj_latitude, position.gcj_longitude,
                                      keyword, NEARBY_RADIUS_M, 5)
        except AmapError:
            return self._sim("周边搜索失败，网络或地图服务暂时不可用。")
        if not places:
            return self._sim("一公里内没有找到%s。" % keyword)
        nearest = places[0]
        parts = ["最近的%s：%s" % (keyword, nearest.name)]
        if nearest.distance_m is not None and nearest.distance_m >= 0:
            parts.append("，约%d米" % int(round(nearest.distance_m)))
        parts.append(self._direction_phrase(nearest.lat, nearest.lon))
        if len(places) > 1:
            second = places[1]
            parts.append("。另外还有%s" % second.name)
            if second.distance_m is not None and second.distance_m >= 0:
                parts.append("，约%d米" % int(round(second.distance_m)))
        parts.append("。位置来自地图数据，请以实际环境为准。")
        return self._sim("".join(parts))

    def _weather(self) -> str:
        if self.amap is None:
            return "天气查询不可用：未配置高德 Web 服务 key。"
        position = self.current_position()
        adcode = self._current_adcode(position)
        if not adcode:
            return "还没有定位信号，无法查询当地天气。"
        try:
            info = self.amap.weather(adcode)
        except AmapError:
            return self._sim("暂时无法获取天气信息。")
        sentence = "%s天气：%s，气温%s度" % (
            info.get("city") or "当地", info.get("weather") or "未知",
            info.get("temperature") or "未知")
        if info.get("winddirection") and info.get("windpower"):
            sentence += "，%s风%s级" % (info["winddirection"], info["windpower"])
        if info.get("humidity"):
            sentence += "，湿度%s%%" % info["humidity"]
        return self._sim(sentence + "。")

    def _status(self) -> str:
        parts = ["当前状态：%s。" % _STATE_ZH.get(self.state, self.state)]
        position = self.current_position()
        if position is None:
            parts.append("尚无GPS定位。")
        else:
            parts.append("GPS数据%.0f秒前，来源%s。" % (position.gps_age_s, position.source))
        if self.state in ("NAVIGATING", "PAUSED") and self._last_remaining_m is not None:
            parts.append("路线剩余约%d米。" % self._last_remaining_m)
        if self.amap is None:
            parts.append("地图服务：未配置。")
        elif self.amap.is_fake:
            parts.append("地图服务：模拟数据。")
        else:
            parts.append("地图服务：高德在线。")
        return "".join(parts)

    # -- Legacy straight-line arrival (no route available) ---------------------
    # -- 无路线时的直线到达判定（降级模式） -------------------------------------

    def _check_arrival_without_route(self) -> None:
        if (self.state != "NAVIGATING" or self._arrival_announced
                or self._destination_gcj is None):
            return
        position = self.current_position()
        if position is None:
            return
        distance = haversine_m(position.gcj_latitude, position.gcj_longitude,
                               self._destination_gcj[0], self._destination_gcj[1])
        if distance <= ARRIVAL_RADIUS_M:
            self.state = "ARRIVED"
            self._arrival_announced = True
            self._announce("您已到达%s附近约%d米内。请使用盲杖等辅助工具确认具体入口。"
                           % (self.destination_name or "目的地", int(distance)),
                           "critical")


# --------------------------------------------------------------------------
# CLI modes / 命令行模式
# --------------------------------------------------------------------------

def _handle_json_line(core: SolePrecisionCore, line: str) -> Optional[str]:
    reply_text: Optional[str] = None
    try:
        message = json.loads(line)
        kind = message.get("type")
        if kind == "gps":
            core.update_gps(GpsSample.from_dict(message))
        elif kind == "imu":
            core.update_imu(ImuSample.from_dict(message))
            return None  # high-rate stream: stay silent / 高频数据流，保持安静
        elif kind == "text":
            # The reply already carries any queued cues as its prefix.
            # 回复已自带排队提示作为前缀。
            reply_text = core.process_text(str(message.get("text", "")))
        elif kind == "set_destination":
            core.set_destination(message["name"],
                                 message.get("latitude"), message.get("longitude"))
        elif kind in ("get_position", "poll"):
            pass
        else:
            return json.dumps({"error": "unknown type: %s" % kind})
    except (ValueError, KeyError, TypeError) as error:
        return json.dumps({"error": str(error)})

    position = core.current_position()
    response: Dict = {"position": None if position is None else position.to_dict()}
    if reply_text is not None:
        response["reply"] = reply_text
    announcements = core.pop_announcements()
    if announcements:
        response["announcements"] = announcements
        # Back-compat plain string. / 兼容旧字段的纯文本形式。
        response["announcement"] = "".join(item["text"] for item in announcements)
    return json.dumps(response, ensure_ascii=False)


def run_interactive(core: SolePrecisionCore) -> None:
    if core.amap is None:
        map_status = "未配置 key，搜索不可用 / no key, search disabled"
    elif core.amap.is_fake:
        map_status = "模拟数据 / SIMULATED data"
    else:
        map_status = "高德在线 / AMap online"
    print("Sole Precision Pi core. 中文指令或 JSON 传感器行；Ctrl-D 退出。 "
          "Chinese commands or JSON sensor lines; Ctrl-D exits. "
          "地图服务 map service: %s" % map_status)
    for line in sys.stdin:
        line = line.strip()
        if not line:
            continue
        if line.startswith("{"):
            reply = _handle_json_line(core, line)
            if reply is not None:
                print(reply)
        else:
            print(core.process_text(line))
        sys.stdout.flush()


def run_demo() -> None:
    """Full simulated product run: Chinese dialog, then a guided walk along the
    canned route with staged cues, then a fusion-accuracy walk.
    完整模拟运行：中文对话 -> 沿内置路线的引导行走（分级提示）-> 融合精度演示。"""
    core = SolePrecisionCore(FakeAmapClient())

    # A first fix so search/planning have an origin (WGS-84, Changsha).
    # 先给一次定位，搜索与规划才有起点（WGS-84，长沙）。
    start_wgs = (28.2282, 112.9388)
    core.update_gps(GpsSample(timestamp=0.0, latitude=start_wgs[0],
                              longitude=start_wgs[1], accuracy_m=5.0))

    print("== 对话 Conversation (simulated AMap) ==")
    for text in ("帮助", "带我去岳麓山", "下一个", "确认", "还有多远", "开始"):
        print("  >> %s" % text)
        print("  << %s" % core.process_text(text))

    route = core.route
    assert route is not None and core.state == "NAVIGATING"
    path = [point for step in route.steps for point in step.path]
    total = path_length_m(path)

    # Guided walk at 5 m/s: GPS 1 Hz (converted back to WGS-84 like a real
    # receiver would emit), IMU 20 Hz with step spikes and the segment heading.
    # 以 5 m/s 引导行走：GPS 1 Hz（反算回 WGS-84，模拟真实接收机输出），
    # IMU 20 Hz（计步峰值 + 分段航向）。
    print("== 引导行走 Guided walk (%.0f m, staged cues) ==" % total)
    speed, imu_hz = 5, 20
    step_period, last_step = 0.55, -1.0
    ticks = int((total / speed + 15) * imu_hz)
    for tick in range(ticks):
        t = 1.0 + tick / imu_hz
        walked = min(total, speed * (tick / imu_hz))
        gcj_lat, gcj_lon, seg_bearing = point_along_path(path, walked)
        if t - last_step >= step_period:
            accel = (0.4, 0.2, GRAVITY_MPS2 + 2.4)
            last_step = t
        else:
            accel = (0.05, -0.03, GRAVITY_MPS2 + 0.1)
        core.update_imu(ImuSample(timestamp=t, accel=accel, heading_deg=seg_bearing))
        if tick % imu_hz == 0:
            wgs_lat, wgs_lon = gcj02_to_wgs84(gcj_lat, gcj_lon)
            core.update_gps(GpsSample(timestamp=t, latitude=wgs_lat, longitude=wgs_lon,
                                      accuracy_m=5.0, speed_mps=speed,
                                      bearing_deg=seg_bearing))
        for item in core.pop_announcements():
            print("  [%s] t=%3.0fs  %s" % (item["priority"].upper()[:4], t, item["text"]))
        if core.state == "ARRIVED":
            break
    print("  final state: %s" % core.state)

    # Fusion accuracy demo: 120 m due west, GPS wobble +/-2 m, no route.
    # 融合精度演示：向正西 120 米，GPS ±2 米抖动，不带路线。
    print("== 融合精度 Fusion walk (120 m west, GPS 1 Hz + IMU 20 Hz) ==")
    tracker = PositionTracker()
    start_lat, start_lon = start_wgs
    heading = 270.0
    duration = 100
    step_period, last_step = 0.55, -1.0
    for tick in range(duration * imu_hz + 1):
        t = tick / imu_hz
        true_lat, true_lon = offset_position(start_lat, start_lon, heading,
                                             min(120.0, speed * t))
        if t - last_step >= step_period:
            accel = (0.4, 0.2, GRAVITY_MPS2 + 2.4)
            last_step = t
        else:
            accel = (0.05, -0.03, GRAVITY_MPS2 + 0.1)
        tracker.update_imu(ImuSample(timestamp=t, accel=accel, heading_deg=heading))
        if tick % imu_hz == 0:
            wobble = 2.0 * math.sin(t * 0.7)
            noisy_lat, noisy_lon = offset_position(true_lat, true_lon,
                                                   (heading + 90.0) % 360.0, wobble)
            tracker.update_gps(GpsSample(timestamp=t, latitude=noisy_lat,
                                         longitude=noisy_lon, accuracy_m=6.0,
                                         speed_mps=speed, bearing_deg=heading))
        if tick % (20 * imu_hz) == 0:
            position = tracker.current_position()
            assert position is not None
            error = haversine_m(position.latitude, position.longitude, true_lat, true_lon)
            print("  t=%3.0fs  wgs=(%.6f, %.6f)  v=%.2f m/s  src=%s  err=%.1f m"
                  % (t, position.latitude, position.longitude,
                     position.speed_mps, position.source, error))


def run_selftest() -> int:
    failures: List[str] = []

    def check(name: str, condition: bool) -> None:
        print("  %s %s" % ("PASS" if condition else "FAIL", name))
        if not condition:
            failures.append(name)

    # -- Geometry / 几何 -------------------------------------------------------
    d = haversine_m(28.2282, 112.9388, 28.2282, 112.9488)
    check("haversine ~981 m for 0.01 deg lon in Changsha", 950 <= d <= 1010)
    glat, glon = wgs84_to_gcj02(28.2282, 112.9388)
    shift = haversine_m(28.2282, 112.9388, glat, glon)
    check("gcj02 shift 100-1000 m inside China", 100 <= shift <= 1000)
    check("gcj02 no-op outside China",
          wgs84_to_gcj02(47.4979, 19.0402) == (47.4979, 19.0402))
    back = gcj02_to_wgs84(glat, glon)
    check("gcj02 inverse round-trips under 0.5 m",
          haversine_m(28.2282, 112.9388, back[0], back[1]) < 0.5)

    a = (28.2282, 112.9388)
    b = offset_position(a[0], a[1], 90.0, 100.0)
    c = offset_position(b[0], b[1], 0.0, 100.0)
    path = [a, b, c]
    mid = offset_position(a[0], a[1], 90.0, 50.0)
    off = offset_position(mid[0], mid[1], 0.0, 5.0)
    drift, along = project_point_to_path(path, off[0], off[1])
    check("path projection drift ~5 m", 4.0 <= drift <= 6.0)
    check("path projection along ~50 m", 45.0 <= along <= 55.0)
    p_lat, p_lon, p_bearing = point_along_path(path, 150.0)
    check("point_along_path lands on second leg heading north",
          abs(p_bearing) < 1.0 or abs(p_bearing - 360.0) < 1.0)
    check("path length ~200 m", 195.0 <= path_length_m(path) <= 205.0)

    # -- Guidance engine / 引导引擎 --------------------------------------------
    check("clock 0 -> 12", clock_position_for(0) == 12)
    check("clock 90 -> 3", clock_position_for(90) == 3)
    check("clock -90 -> 9", clock_position_for(-90) == 9)

    def snap(step: int, dist: int, step_dist: int = 200,
             maneuver: str = Maneuver.RIGHT, remaining: int = 500,
             off_m: Optional[int] = None) -> GuidanceSnapshot:
        return GuidanceSnapshot(
            step_index=step, maneuver=maneuver, distance_to_maneuver_m=dist,
            next_road_name="麓山路", current_road_name="新民路",
            step_distance_m=step_dist, orientation="东",
            relative_bearing_deg=90, turn_angle_deg=90,
            needs_confirmation=maneuver in HAZARD_MANEUVERS,
            remaining_route_m=remaining, remaining_route_s=300, off_route_m=off_m)

    engine = PedestrianGuidanceEngine()
    stages = [engine.on_snapshot(s) for s in (
        snap(0, 200), snap(0, 195), snap(0, 110), snap(0, 25),
        snap(0, 6), snap(0, 6), snap(1, 150, step_dist=80))]
    check("stage sequence progress/early/prepare/act/confirm",
          [x.stage if x else None for x in stages]
          == [STAGE_PROGRESS, None, STAGE_EARLY, STAGE_PREPARE,
              STAGE_ACT, None, STAGE_CONFIRM])
    arrival_cue = engine.on_snapshot(snap(1, 140, remaining=20))
    again = engine.on_snapshot(snap(1, 139, remaining=18))
    check("arrival fires once at <=25 m of route end",
          arrival_cue is not None and arrival_cue.stage == STAGE_ARRIVAL
          and (again is None or again.stage != STAGE_ARRIVAL))

    engine2 = PedestrianGuidanceEngine()
    o1 = engine2.on_snapshot(snap(0, 100, off_m=9))
    o2 = engine2.on_snapshot(snap(0, 100, off_m=12))
    o3 = engine2.on_snapshot(snap(0, 100, off_m=22))
    o4 = engine2.on_snapshot(snap(0, 100, off_m=None))
    check("off-route fires at 9 m, re-fires only after 10 m change",
          o1 is not None and o1.stage == STAGE_OFF_ROUTE and o2 is None
          and o3 is not None and o3.stage == STAGE_OFF_ROUTE
          and (o4 is None or o4.stage != STAGE_OFF_ROUTE))
    engine3 = PedestrianGuidanceEngine()
    hazard = engine3.on_snapshot(snap(0, 12, maneuver=Maneuver.CROSSWALK))
    check("hazard act threshold is 12 m",
          hazard is not None and hazard.stage == STAGE_ACT and hazard.priority == "critical")
    engine4 = PedestrianGuidanceEngine()
    early_cue = engine4.on_snapshot(snap(0, 110))
    check("progress suppressed right after a maneuver cue",
          early_cue is not None and early_cue.stage == STAGE_EARLY
          and engine4.on_snapshot(snap(0, 108)) is None)
    engine5 = PedestrianGuidanceEngine()
    engine5.prime_progress(700)
    quiet = engine5.on_snapshot(snap(0, 660))
    still_quiet = engine5.on_snapshot(snap(0, 520))
    later = engine5.on_snapshot(snap(0, 440))
    check("progress waits a full bucket (~250 m) after the last cue",
          quiet is None and still_quiet is None
          and later is not None and later.stage == STAGE_PROGRESS)
    engine6 = PedestrianGuidanceEngine()
    engine6.on_snapshot(snap(5, 300))
    confirm_cue = engine6.on_snapshot(snap(6, 400, step_dist=400))
    check("confirm arms the walked-guard against instant progress",
          confirm_cue is not None and confirm_cue.stage == STAGE_CONFIRM
          and engine6.on_snapshot(snap(6, 390)) is None)
    check("progress spacing: none below 150 m, 250 m mid, 500 m far",
          PedestrianGuidanceEngine.progress_bucket_size(140) is None
          and PedestrianGuidanceEngine.progress_bucket_size(600) == 250
          and PedestrianGuidanceEngine.progress_bucket_size(1500) == 500)

    # -- Cue speech / 提示语音 -------------------------------------------------
    cross = cue_message(GuidanceCue(stage=STAGE_ACT, maneuver=Maneuver.CROSSWALK,
                                    distance_m=5, needs_confirmation=True))
    check("crossing act says stop at kerb + verify",
          "人行横道" in cross and "路缘" in cross and "确认" in cross)
    right_act = GuidanceCue(stage=STAGE_ACT, maneuver=Maneuver.RIGHT, distance_m=5,
                            clock_position=3, side="RIGHT")
    check("clock survives concise mode at act",
          "3点钟" in cue_message(right_act, DETAIL_CONCISE))
    early_right = GuidanceCue(stage=STAGE_EARLY, maneuver=Maneuver.RIGHT,
                              distance_m=100, clock_position=3, side="RIGHT",
                              road_name="麓山路")
    check("concise early drops clock, standard keeps it",
          "点钟" not in cue_message(early_right, DETAIL_CONCISE)
          and "点钟" in cue_message(early_right, DETAIL_STANDARD))
    confirm = GuidanceCue(stage=STAGE_CONFIRM, maneuver=Maneuver.STRAIGHT,
                          distance_m=0, current_road_name="新民路",
                          step_distance_m=80, orientation="北",
                          remaining_route_m=150, remaining_route_min=2)
    detailed = cue_message(confirm, DETAIL_DETAILED)
    check("confirm names the new road; detailed adds remaining route",
          "新民路" in detailed and "剩余150米" in detailed)
    check("step counts: 30 m -> ~45 steps, rounded to 5",
          steps_for(30.0) == 45 and steps_for(12.0) == 17)
    check("step counts absent when too far or too close",
          steps_for(50.0) is None and steps_for(2.0) is None)
    prepare_right = GuidanceCue(stage=STAGE_PREPARE, maneuver=Maneuver.RIGHT,
                                distance_m=28, clock_position=3, side="RIGHT")
    check("prepare cue speaks an approximate step count",
          "约40步" in cue_message(prepare_right))
    arrival_close = GuidanceCue(stage=STAGE_ARRIVAL, maneuver=Maneuver.ARRIVED,
                                distance_m=24, remaining_route_m=24)
    check("arrival cue speaks steps when close",
          "步" in cue_message(arrival_close))
    check("longer stride lowers the count",
          "约35步" in cue_message(prepare_right, DETAIL_STANDARD, step_length_m=0.8))

    # -- AMap parsing / 高德解析 ------------------------------------------------
    fake = FakeAmapClient()
    route = fake.walking_route(28.2282, 112.9388, 0.0, 0.0, "岳麓山")
    check("fake route parses 4 steps, 302 m, 1 crossing",
          len(route.steps) == 4 and int(route.total_distance_m) == 302
          and route.crossing_count == 1)
    check("step maneuvers: left turn, crosswalk next, arrival last",
          route.steps[0].maneuver == Maneuver.LEFT
          and route.steps[1].maneuver == Maneuver.CROSSWALK
          and route.steps[3].maneuver == Maneuver.ARRIVED)
    check("[] empty-field quirk parses to empty road name",
          route.steps[2].road_name == "")
    check("action text mapping",
          maneuver_from_amap("向左前方行走", None, False) == Maneuver.SLIGHT_LEFT
          and maneuver_from_amap("直行", 1, False) == Maneuver.CROSSWALK)

    # -- Route tracker / 路线跟踪 ------------------------------------------------
    gcj_path = [point for step in route.steps for point in step.path]
    rt = RouteTracker(route)
    near_start = point_along_path(gcj_path, 10.0)
    s1 = rt.on_position(near_start[0], near_start[1], 90.0)
    check("tracker: 10 m in -> step 0, ~140 m to maneuver",
          s1.step_index == 0 and 130 <= s1.distance_to_maneuver_m <= 148)
    past_turn = point_along_path(gcj_path, 165.0)
    s2 = rt.on_position(past_turn[0], past_turn[1], 0.0)
    check("tracker advances to step 1 after the turn", s2.step_index == 1)
    base = point_along_path(gcj_path, 60.0)
    rt2 = RouteTracker(route)
    off_point = offset_position(base[0], base[1], 0.0, 15.0)
    s3 = rt2.on_position(off_point[0], off_point[1], 90.0)
    check("tracker reports ~15 m drift as off-route",
          s3.off_route_m is not None and 12 <= s3.off_route_m <= 18)
    check("hazard preview stops at turns, finds crossings beyond them",
          rt2.hazard_ahead(0, 150.0) is None
          and rt2.hazard_ahead(1, 80.0) == (Maneuver.CROSSWALK, 80))
    preview_cue = GuidanceCue(stage=STAGE_CONFIRM, maneuver=Maneuver.STRAIGHT,
                              distance_m=0, current_road_name="银盆南路",
                              step_distance_m=422,
                              hazard_ahead_maneuver=Maneuver.CROSSWALK,
                              hazard_ahead_m=422)
    check("confirm speaks a hazard preview beyond the early window",
          "前方约420米有人行横道" in cue_message(preview_cue))
    near_preview = GuidanceCue(stage=STAGE_CONFIRM, maneuver=Maneuver.STRAIGHT,
                               distance_m=0, current_road_name="新民路",
                               step_distance_m=80,
                               hazard_ahead_maneuver=Maneuver.CROSSWALK,
                               hazard_ahead_m=80)
    check("no preview inside the early window (ladder covers it)",
          "请提前留意" not in cue_message(near_preview))
    approach = GuidanceCue(stage=STAGE_PREPARE, maneuver=Maneuver.ARRIVED,
                           distance_m=28)
    check("arrival approach says 即将到达, not 已到达",
          "即将到达目的地" in cue_message(approach))

    # -- Dialog end-to-end (simulated AMap) / 对话全流程（模拟高德） -------------
    core = SolePrecisionCore(FakeAmapClient())
    core.update_gps(GpsSample(timestamp=0.5, latitude=28.2282, longitude=112.9388,
                              accuracy_m=5.0))
    reply = core.process_text("带我去岳麓山")
    check("search offers first candidate, labelled 模拟",
          "第1个" in reply and "模拟" in reply and core.state == "CHOOSING")
    reply = core.process_text("下一个")
    check("next candidate offered", "第2个" in reply)
    reply = core.process_text("确认")
    check("confirm plans the route",
          "路线已规划" in reply and "人行横道" in reply and core.state == "READY")
    reply = core.process_text("还有多远")
    check("how-far in ready state speaks the total", "全程" in reply)
    reply = core.process_text("开始")
    check("start begins guidance with safety line",
          "导航开始" in reply and "盲杖" in reply and core.state == "NAVIGATING")

    walk_route = core.route
    assert walk_route is not None
    walk_path = [point for step in walk_route.steps for point in step.path]
    walk_total = path_length_m(walk_path)
    collected: List[Dict[str, str]] = []
    t = 1.0
    for second in range(int(walk_total / 5) + 12):
        t += 1.0
        walked = min(walk_total, 5 * second)
        w_lat, w_lon, w_bearing = point_along_path(walk_path, walked)
        wgs_lat, wgs_lon = gcj02_to_wgs84(w_lat, w_lon)
        core.update_gps(GpsSample(timestamp=t, latitude=wgs_lat, longitude=wgs_lon,
                                  accuracy_m=5.0, speed_mps=5, bearing_deg=w_bearing))
        collected.extend(core.pop_announcements())
        if core.state == "ARRIVED":
            break
    spoken = "".join(item["text"] for item in collected)
    check("guided walk reaches ARRIVED", core.state == "ARRIVED")
    check("cues include prepare and act", "准备：" in spoken and "现在：" in spoken)
    check("crossing cue includes kerb warning", "路缘" in spoken)
    check("confirm cue names the new road", "新民路" in spoken)
    check("landmark spoken near maneuver", "靠近岳麓书院" in spoken)
    check("critical priority used", any(item["priority"] == "critical" for item in collected))
    check("final arrival sentence delivered", "导航结束" in spoken)

    # Off-route -> reroute cycle on a fresh run. / 偏航 -> 重新规划闭环。
    core2 = SolePrecisionCore(FakeAmapClient())
    core2.update_gps(GpsSample(timestamp=0.5, latitude=28.2282, longitude=112.9388,
                               accuracy_m=5.0))
    core2.process_text("带我去岳麓山")
    core2.process_text("确认")
    core2.process_text("开始")
    path2 = [point for step in core2.route.steps for point in step.path]
    stray_base = point_along_path(path2, 50.0)
    stray = offset_position(stray_base[0], stray_base[1], 0.0, 25.0)
    stray_wgs = gcj02_to_wgs84(stray[0], stray[1])
    core2.update_gps(GpsSample(timestamp=2.0, latitude=stray_wgs[0],
                               longitude=stray_wgs[1], accuracy_m=5.0,
                               speed_mps=5, bearing_deg=90.0))
    first_pass = "".join(item["text"] for item in core2.pop_announcements())
    core2.update_gps(GpsSample(timestamp=3.5, latitude=stray_wgs[0],
                               longitude=stray_wgs[1], accuracy_m=5.0,
                               speed_mps=5, bearing_deg=90.0))
    second_pass = "".join(item["text"] for item in core2.pop_announcements())
    check("off-route announced and reroute requested",
          "偏离路线" in first_pass and "正在重新规划" in first_pass)
    check("reroute adopted with new summary", "已重新规划路线" in second_pass)

    # -- Dialog odds and ends / 对话杂项 ----------------------------------------
    core3 = SolePrecisionCore(FakeAmapClient())
    core3.update_gps(GpsSample(timestamp=0.5, latitude=28.2282, longitude=112.9388,
                               accuracy_m=5.0))
    check("where-am-i uses reverse geocode + safety line",
          "岳麓" in core3.process_text("我在哪里")
          and "安全确认" in core3._last_reply)
    check("nearby search is single-category and labelled",
          "公共厕所" in core3.process_text("附近的厕所"))
    check("weather spoken from adcode", "多云" in core3.process_text("天气"))
    check("detail switch acknowledged", "简洁播报" in core3.process_text("简洁模式"))
    check("question is not swallowed as a destination",
          "还没有设置目的地" in core3.process_text("我要去哪里"))
    check("help lists the command set", "带我去" in core3.process_text("帮助"))
    check("stop cancels cleanly",
          "没有正在进行" in core3.process_text("停止"))
    core3.process_text("带我去岳麓山")
    core3.process_text("确认")
    core3.process_text("开始")
    check("repeated 开始 mid-navigation is understood",
          "导航已在进行中" in core3.process_text("开始"))
    core3.process_text("暂停")
    check("开始 while paused resumes",
          "导航已恢复" in core3.process_text("开始"))

    # No-AMap fallback: straight-line arrival only. / 无高德时的直线到达降级。
    core4 = SolePrecisionCore()
    check("keyless search explains the missing key",
          "AMAP_WEB_KEY" in core4.process_text("带我去岳麓山"))
    dest_gcj = wgs84_to_gcj02(28.2282, 112.9388)
    near_dest = offset_position(dest_gcj[0], dest_gcj[1], 45.0, 10.0)
    core4.set_destination("测试点", near_dest[0], near_dest[1])
    core4.update_gps(GpsSample(timestamp=1.0, latitude=28.2282, longitude=112.9388,
                               accuracy_m=5.0))
    check("straight-line arrival announced inside 20 m",
          core4.state == "ARRIVED" and "已到达" in core4.pop_pending_announcement())

    # -- Fusion (unchanged behaviour) / 融合（行为不变） -------------------------
    tracker = PositionTracker()
    tracker.update_gps(GpsSample(timestamp=0.0, latitude=28.2282, longitude=112.9388,
                                 accuracy_m=5.0, speed_mps=0.0))
    for tick in range(1, 201):  # 10 s of IMU at 20 Hz, stepping north / 向北计步 10 秒
        step_t = tick / 20.0
        spike = (tick % 11 == 0)
        accel = (0.3, 0.1, GRAVITY_MPS2 + (2.5 if spike else 0.1))
        tracker.update_imu(ImuSample(timestamp=step_t, accel=accel, heading_deg=0.0))
    position = tracker.current_position()
    check("dead reckoning moved north",
          position is not None and position.latitude > 28.2282
          and abs(position.longitude - 112.9388) < 1e-4)
    check("dead reckoning flagged as gps+imu / DR",
          position is not None and position.source in ("gps+imu", "imu-dead-reckoning"))
    tracker.update_gps(GpsSample(timestamp=10.5, latitude=28.22835, longitude=112.9388,
                                 accuracy_m=5.0, speed_mps=1.2, bearing_deg=0.0))
    snapped = tracker.current_position()
    check("good fix re-anchors the estimate",
          snapped is not None and abs(snapped.latitude - 28.22835) < 1e-6)

    print("%d checks failed" % len(failures) if failures else "all checks passed")
    return 1 if failures else 0


def _run_walk_simulator(core: SolePrecisionCore,
                        start_wgs: Tuple[float, float]) -> None:
    """Engineering aid for laptops without a GNSS receiver: seeds one starting
    fix, then — whenever navigation is active — feeds synthetic GPS along the
    planned route at 5 m/s and prints cues as they are produced.  This is
    simulated MOVEMENT over REAL map data (when a key is configured); it mirrors
    the Android app's "simulate movement when navigating" developer option.
    工程测试辅助（电脑上没有 GNSS 接收机时用）：先注入一个起始定位；导航激活后
    以 5 m/s 沿已规划路线注入模拟 GPS，并实时打印提示。这是"真实地图数据 +
    模拟移动"（配 key 时），对应安卓端"导航时模拟移动"开发者选项。"""
    core.update_gps(GpsSample(timestamp=time.monotonic(),
                              latitude=start_wgs[0], longitude=start_wgs[1],
                              accuracy_m=5.0))
    print("[模拟行走 SIM-WALK] 起始定位 %.4f, %.4f（WGS-84）已注入。"
          "开始导航后将以 5 m/s 沿路线自动行走。" % start_wgs)
    sys.stdout.flush()
    walked = 0.0
    active_route_id: Optional[int] = None
    path: List[Tuple[float, float]] = []
    total = 0.0
    while True:
        time.sleep(1.0)
        route = core.route
        if (core.state == "NAVIGATING" and route is not None
                and core.route_tracker is not None):
            if id(route) != active_route_id:
                # New or replanned route: it starts at the current position,
                # so walking restarts from zero along its geometry.
                # 新路线（或重新规划）从当前位置出发，沿其几何从零走起。
                active_route_id = id(route)
                path = [point for step in route.steps for point in step.path]
                total = path_length_m(path)
                walked = 0.0
            walked = min(total, walked + 5)
            gcj_lat, gcj_lon, seg_bearing = point_along_path(path, walked)
            wgs_lat, wgs_lon = gcj02_to_wgs84(gcj_lat, gcj_lon)
            core.update_gps(GpsSample(timestamp=time.monotonic(),
                                      latitude=wgs_lat, longitude=wgs_lon,
                                      accuracy_m=5.0, speed_mps=5,
                                      bearing_deg=seg_bearing))
        for item in core.pop_announcements():
            print("[%s] %s" % (item["priority"], item["text"]))
            sys.stdout.flush()


def _resolve_start_position(argv: List[str]) -> Tuple[float, float]:
    """--at <lat,lon> (WGS-84) for the simulator; defaults to central Changsha.
    模拟行走的起点 --at 纬度,经度（WGS-84）；默认长沙市中心附近。"""
    if "--at" in argv:
        index = argv.index("--at")
        if index + 1 < len(argv):
            pieces = argv[index + 1].split(",")
            if len(pieces) == 2:
                try:
                    lat, lon = float(pieces[0]), float(pieces[1])
                    if is_valid_coordinate(lat, lon):
                        return lat, lon
                except ValueError:
                    pass
            print("--at 格式应为 纬度,经度（WGS-84），已使用默认起点。")
    return 28.2282, 112.9388


def _resolve_amap_key(argv: List[str]) -> str:
    """--key beats AMAP_WEB_KEY beats amap_key.txt beside the script.  The key
    is never printed or logged.
    优先级：--key > 环境变量 AMAP_WEB_KEY > 脚本旁的 amap_key.txt。key 绝不打印。"""
    if "--key" in argv:
        index = argv.index("--key")
        if index + 1 < len(argv):
            return argv[index + 1].strip()
    env_key = os.environ.get("AMAP_WEB_KEY", "").strip()
    if env_key:
        return env_key
    key_path = os.path.join(os.path.dirname(os.path.abspath(__file__)), "amap_key.txt")
    try:
        with open(key_path, "r", encoding="utf-8") as handle:
            return handle.read().strip()
    except OSError:
        return ""


if __name__ == "__main__":
    if "--selftest" in sys.argv:
        sys.exit(run_selftest())
    elif "--demo" in sys.argv:
        run_demo()
    else:
        if "--fake-amap" in sys.argv:
            amap_client = FakeAmapClient()
        else:
            resolved_key = _resolve_amap_key(sys.argv)
            amap_client = AmapWebClient(resolved_key) if resolved_key else None
        main_core = SolePrecisionCore(amap_client)
        if "--simulate-walk" in sys.argv:
            threading.Thread(target=_run_walk_simulator,
                             args=(main_core, _resolve_start_position(sys.argv)),
                             daemon=True, name="walk-simulator").start()
        run_interactive(main_core)
