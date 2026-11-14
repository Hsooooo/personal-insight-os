#!/usr/bin/env python3
"""
Garmin Connect 데이터 동기화 스크립트
Java 백엔드(GarminPythonClient)에서 ProcessBuilder로 호출

Usage:
    GARMIN_EMAIL=... GARMIN_PASSWORD=... [GARMIN_TOKENSTORE=<dir>] \
        python garmin_sync.py <from_date> <to_date> <data_type>

자격증명은 프로세스 인자(ps로 노출됨) 대신 환경변수로 받는다.
GARMIN_TOKENSTORE가 있으면 저장된 세션 토큰을 재사용하고, 없거나 만료 시에만 로그인 후 토큰을 저장한다.

data_type: activities | health | sleep | all

Output (stdout, exit 0): JSON object
    {
      "activities": [...], "health": [...], "sleep": [...], "weights": [...], "extras": [...],
      "errors": [{"endpoint": "...", "date": "YYYY-MM-DD"|null, "kind": "...", "message": "..."}],
      "fatal_error": null | {"kind": "auth"|"rate_limit"|..., "message": "...", "endpoint": "..."}
    }
    - 값이 없는 지표는 0이 아니라 null로 내보낸다 (기준선 평균 오염 방지).
    - errors: 엔드포인트별 실패(데이터 없음은 오류가 아님). Java는 errors가 있으면 PARTIAL로 기록한다.
    - fatal_error: 인증 실패/재시도 후에도 지속되는 rate limit 등 계속 진행할 수 없는 오류.
      이때까지 수집한 데이터는 함께 출력되며, Java는 동기화를 FAILED로 기록하고 이후 청크를 중단한다.

Errors before any data (usage, import, login): JSON {"error": "...", "kind": "..."} to stderr, exit code 1
kind: auth | rate_limit | server | network | not_found | config | other
"""

import json
import os
import random
import sys
import time
import traceback
from datetime import datetime, timedelta, timezone

MAX_ATTEMPTS = 3
BACKOFF_BASE_SECONDS = 2.0
DAILY_CALL_INTERVAL_SECONDS = 0.5


class FatalSyncError(Exception):
    """계속 진행할 수 없는 오류 (인증 실패, 지속되는 rate limit)."""

    def __init__(self, kind, message, endpoint=None):
        super().__init__(message)
        self.kind = kind
        self.endpoint = endpoint


def parse_date(s):
    return datetime.strptime(s, "%Y-%m-%d").date()


def _status_code(exc):
    """garminconnect / garth / requests 예외에서 HTTP 상태 코드를 최대한 추출."""
    for obj in (exc, getattr(exc, "error", None), getattr(exc, "__cause__", None), getattr(exc, "__context__", None)):
        if obj is None:
            continue
        resp = getattr(obj, "response", None)
        code = getattr(resp, "status_code", None)
        if isinstance(code, int):
            return code
        code = getattr(obj, "status_code", None)
        if isinstance(code, int):
            return code
    text = str(exc)
    for code in (401, 403, 404, 429, 500, 502, 503, 504):
        if str(code) in text:
            return code
    return None


def classify_error(exc):
    """예외를 kind 문자열로 분류."""
    name = type(exc).__name__
    if "TooManyRequests" in name:
        return "rate_limit"
    if "Authentication" in name:
        return "auth"
    code = _status_code(exc)
    if code == 429:
        return "rate_limit"
    if code in (401, 403):
        return "auth"
    if code == 404:
        return "not_found"
    if code is not None and 500 <= code < 600:
        return "server"
    if "Connection" in name or "Timeout" in name or "timed out" in str(exc).lower():
        return "network"
    return "other"


def _short(msg, limit=300):
    msg = str(msg).replace("\n", " ")
    return msg if len(msg) <= limit else msg[:limit] + "..."


class Collector:
    def __init__(self):
        self.errors = []

    def add_error(self, endpoint, date, kind, message):
        self.errors.append({
            "endpoint": endpoint,
            "date": date,
            "kind": kind,
            "message": _short(message),
        })

    def call(self, endpoint, fn, date=None):
        """
        재시도(429/5xx/네트워크, 지수 백오프) 포함 호출.
        - 성공: 결과 반환
        - 데이터 없음(404): None 반환, 오류로 기록하지 않음
        - 인증 실패 / 재시도 후에도 429: FatalSyncError
        - 기타 실패: errors에 기록 후 None
        """
        last_exc = None
        kind = "other"
        for attempt in range(1, MAX_ATTEMPTS + 1):
            try:
                return fn()
            except Exception as e:  # noqa: BLE001 - 엔드포인트별로 분류해 기록
                last_exc = e
                kind = classify_error(e)
                if kind == "not_found":
                    return None
                if kind == "auth":
                    raise FatalSyncError("auth", f"{endpoint}: {_short(e)}", endpoint)
                if kind in ("rate_limit", "server", "network") and attempt < MAX_ATTEMPTS:
                    delay = BACKOFF_BASE_SECONDS * (2 ** (attempt - 1)) + random.uniform(0, 0.5)
                    if kind == "rate_limit":
                        delay *= 2
                    time.sleep(delay)
                    continue
                break
        if kind == "rate_limit":
            raise FatalSyncError("rate_limit", f"{endpoint}: {_short(last_exc)}", endpoint)
        self.add_error(endpoint, date, kind, last_exc)
        return None


def _num(value):
    """숫자만 통과 (그 외는 None)."""
    if isinstance(value, bool):
        return None
    if isinstance(value, (int, float)):
        return value
    return None


def _positive(value):
    """생리학적으로 0이 불가능한 지표(안정시 심박, HRV, 수면 점수/시간 등): 0 이하는 None."""
    v = _num(value)
    return v if v is not None and v > 0 else None


def _dict(value):
    return value if isinstance(value, dict) else {}


def _parse_laps(collector, client, activity_id):
    """활동의 랩(splits) 데이터. 랩이 없으면 빈 리스트 (실패는 errors에 기록)."""
    splits = collector.call("activity_splits", lambda: client.get_activity_splits(activity_id))
    laps = []
    for lap in _dict(splits).get("lapDTOs") or []:
        avg_speed = _num(lap.get("averageSpeed"))
        # Garmin API의 averageSpeed는 m/s 단위. pace(sec/km) = 1000 / speed
        avg_pace = round(1000.0 / avg_speed, 4) if avg_speed and avg_speed > 0 else None
        laps.append({
            "lap_index": lap.get("lapIndex"),
            "start_time": lap.get("startTimeLocal") or lap.get("startTimeGMT"),
            "duration_seconds": _num(lap.get("duration")),
            "distance_meters": _num(lap.get("distance")),
            "average_pace_seconds": avg_pace,
            "average_heart_rate": _positive(lap.get("averageHR")),
            "max_heart_rate": _positive(lap.get("maxHR")),
            "raw_payload": lap,
        })
    return laps


def fetch_activities(collector, client, start_date, end_date):
    """Garmin 활동 목록 조회 (랩 데이터 포함)"""
    activities = collector.call(
        "activities",
        lambda: client.get_activities_by_date(start_date.strftime("%Y-%m-%d"), end_date.strftime("%Y-%m-%d")),
    ) or []
    results = []
    for a in activities:
        activity_id = a.get("activityId")
        laps = _parse_laps(collector, client, activity_id) if activity_id else []
        avg_speed = _num(a.get("averageSpeed"))
        results.append({
            "garmin_activity_id": str(activity_id or ""),
            "activity_type": _dict(a.get("activityType")).get("typeKey"),
            "activity_name": a.get("activityName"),
            "start_time": a.get("startTimeLocal"),
            "duration_seconds": _num(a.get("duration")),
            "distance_meters": _num(a.get("distance")),
            "average_pace_seconds": round(1000.0 / avg_speed, 4) if avg_speed and avg_speed > 0 else None,
            "average_heart_rate": _positive(a.get("averageHR")),
            "max_heart_rate": _positive(a.get("maxHR")),
            "calories": _num(a.get("calories")),
            "elevation_gain_meters": _num(a.get("elevationGain")),
            "raw_payload": a,
            "laps": laps,
        })
    return results


def fetch_health(collector, client, date):
    """일일 건강 지표 (SpO2/호흡/층수/활동칼로리/바디배터리 상세 포함). 데이터 없으면 None."""
    date_str = date.strftime("%Y-%m-%d")
    stats = collector.call("stats", lambda: client.get_stats(date_str), date_str)
    if not stats or not isinstance(stats, dict):
        return None
    bb_sleep = _dict(stats.get("bodyBatteryDuringSleep"))
    return {
        "metric_date": date_str,
        "resting_heart_rate": _positive(stats.get("restingHeartRate")),
        "hrv_avg": _positive(stats.get("hrvAverage")),
        "stress_avg": _positive(stats.get("averageStressLevel")),
        "body_battery_min": _num(bb_sleep.get("min")) if bb_sleep else _num(stats.get("bodyBatteryLowestValue")),
        "body_battery_max": _num(bb_sleep.get("max")) if bb_sleep else _num(stats.get("bodyBatteryHighestValue")),
        "steps": _num(stats.get("totalSteps")),
        "calories_total": _num(stats.get("totalKilocalories")),
        "average_spo2": _positive(stats.get("averageSpo2")),
        "lowest_spo2": _positive(stats.get("lowestSpo2")),
        "avg_waking_respiration": _positive(stats.get("avgWakingRespirationValue")),
        "floors_ascended": _num(stats.get("floorsAscended")),
        "floors_descended": _num(stats.get("floorsDescended")),
        "moderate_intensity_minutes": _num(stats.get("moderateIntensityMinutes")),
        "vigorous_intensity_minutes": _num(stats.get("vigorousIntensityMinutes")),
        "active_kilocalories": _num(stats.get("activeKilocalories")),
        "bmr_kilocalories": _num(stats.get("bmrKilocalories")),
        "body_battery_at_wake": _num(stats.get("bodyBatteryAtWakeTime")),
        "body_battery_charged": _num(stats.get("bodyBatteryChargedValue")),
        "body_battery_drained": _num(stats.get("bodyBatteryDrainedValue")),
        "total_distance_meters": _num(stats.get("totalDistanceMeters")),
        "raw_payload": stats,
    }


def _ms_to_iso(timestamp_ms):
    """밀리초 Unix timestamp를 ISO 문자열로 변환 (없으면 None)"""
    if not _num(timestamp_ms):
        return None
    return datetime.fromtimestamp(timestamp_ms / 1000, tz=timezone.utc).isoformat()


def fetch_sleep(collector, client, date):
    """수면 데이터 (낮잠/수면 필요량/HRV 상태 포함). 데이터 없으면 None."""
    date_str = date.strftime("%Y-%m-%d")
    sleep = collector.call("sleep", lambda: client.get_sleep_data(date_str), date_str)
    summary = _dict(_dict(sleep).get("dailySleepDTO"))
    total = _positive(summary.get("sleepTimeSeconds"))
    if not summary or (total is None and not summary.get("sleepStartTimestampGMT")):
        return None
    sleep_need = _dict(summary.get("sleepNeed"))
    overall = _dict(_dict(summary.get("sleepScores")).get("overall"))
    return {
        "sleep_date": date_str,
        "start_time": _ms_to_iso(summary.get("sleepStartTimestampGMT")),
        "end_time": _ms_to_iso(summary.get("sleepEndTimestampGMT")),
        "total_sleep_seconds": total,
        "deep_sleep_seconds": _num(summary.get("deepSleepSeconds")),
        "light_sleep_seconds": _num(summary.get("lightSleepSeconds")),
        "rem_sleep_seconds": _num(summary.get("remSleepSeconds")),
        "awake_seconds": _num(summary.get("awakeSleepSeconds")),
        "sleep_score": _positive(overall.get("value")),
        "nap_seconds": _num(summary.get("napTimeSeconds")),
        "avg_sleep_stress": _num(summary.get("avgSleepStress")),
        "sleep_need_minutes": _num(sleep_need.get("actual")),
        "hrv_status": sleep.get("hrvStatus") if isinstance(sleep, dict) else None,
        "raw_payload": sleep,
    }


DAILY_EXTRA_FETCHERS = [
    ("HRV", lambda c, d: c.get_hrv_data(d)),
    ("BODY_BATTERY", lambda c, d: c.get_body_battery(d, d)),
    ("STRESS", lambda c, d: c.get_stress_data(d)),
    ("HEART_RATE", lambda c, d: c.get_heart_rates(d)),
    ("STEPS", lambda c, d: c.get_steps_data(d)),
    ("RESPIRATION", lambda c, d: c.get_respiration_data(d)),
    ("SPO2", lambda c, d: c.get_spo2_data(d)),
    ("TRAINING_READINESS", lambda c, d: c.get_training_readiness(d)),
    ("TRAINING_STATUS", lambda c, d: c.get_training_status(d)),
]


def fetch_daily_extras(collector, client, date):
    """일일 부가 엔드포인트 전량 수집 (원문 jsonb 저장용).

    엔드포인트별 실패는 errors에 기록하고 계속 진행. 호출 사이 0.5초 간격으로 rate limit을 완화한다.
    """
    date_str = date.strftime("%Y-%m-%d")
    results = []
    for data_type, fetch in DAILY_EXTRA_FETCHERS:
        payload = collector.call(data_type.lower(), lambda f=fetch: f(client, date_str), date_str)
        if payload:
            results.append({"data_type": data_type, "metric_date": date_str, "payload": payload})
        time.sleep(DAILY_CALL_INTERVAL_SECONDS)
    return results


def hrv_from_extras(extras):
    """stats에 HRV가 없을 때 HRV 엔드포인트의 lastNightAvg로 보완."""
    for e in extras:
        if e.get("data_type") == "HRV":
            summary = _dict(_dict(e.get("payload")).get("hrvSummary"))
            return _positive(summary.get("lastNightAvg"))
    return None


def fetch_body_composition(collector, client, start_date, end_date):
    """체중/신체조성 데이터 조회 (기간 단위)"""
    data = collector.call(
        "body_composition",
        lambda: client.get_body_composition(start_date.strftime("%Y-%m-%d"), end_date.strftime("%Y-%m-%d")),
    )
    date_map = {}
    for entry in _dict(data).get("dateWeightList") or []:
        calendar_date = entry.get("calendarDate")
        weight = _positive(entry.get("weight"))
        if calendar_date and weight is not None:
            # Garmin이 grams 단위로 반환하는 경우 보정 (500 이상이면 grams)
            weight_kg = round(weight / 1000, 2) if weight > 500 else weight
            # 같은 날 여러 건이면 마지막 측정값 사용
            date_map[calendar_date] = {
                "metric_date": calendar_date,
                "weight_kg": weight_kg,
                "bmi": _positive(entry.get("bmi")),
                "body_fat": _positive(entry.get("bodyFat")),
                "raw_payload": entry,
            }
    return list(date_map.values())


def fail(message, kind):
    print(json.dumps({"error": message, "kind": kind}), file=sys.stderr)
    sys.exit(1)


def main():
    if len(sys.argv) < 4:
        fail("Usage: garmin_sync.py <from_date> <to_date> <data_type>", "config")

    email = os.environ.get("GARMIN_EMAIL", "")
    password = os.environ.get("GARMIN_PASSWORD", "")
    tokenstore = os.environ.get("GARMIN_TOKENSTORE") or None
    try:
        from_date = parse_date(sys.argv[1])
        to_date = parse_date(sys.argv[2])
    except ValueError as e:
        fail(f"invalid date: {e}", "config")
    data_type = sys.argv[3]

    try:
        from garminconnect import Garmin
    except ImportError:
        fail("garminconnect library not installed", "config")

    try:
        client = Garmin(email, password)
        # tokenstore가 있으면 토큰 로드를 먼저 시도하고, 실패 시 자격증명 로그인 후 토큰을 저장한다
        client.login(tokenstore)
    except Exception as e:  # noqa: BLE001
        kind = classify_error(e)
        if kind == "other":
            kind = "auth"
        fail(f"login failed: {_short(e)}", kind)

    collector = Collector()
    result = {"activities": [], "health": [], "sleep": [], "weights": [], "extras": [],
              "errors": collector.errors, "fatal_error": None}

    try:
        if data_type in ("activities", "all"):
            result["activities"] = fetch_activities(collector, client, from_date, to_date)

        if data_type in ("health", "sleep", "all"):
            current = from_date
            while current <= to_date:
                extras = fetch_daily_extras(collector, client, current)
                result["extras"].extend(extras)
                if data_type in ("health", "all"):
                    h = fetch_health(collector, client, current)
                    if h:
                        if h.get("hrv_avg") is None:
                            h["hrv_avg"] = hrv_from_extras(extras)
                        result["health"].append(h)
                if data_type in ("sleep", "all"):
                    s = fetch_sleep(collector, client, current)
                    if s:
                        result["sleep"].append(s)
                current += timedelta(days=1)

        if data_type in ("health", "all"):
            result["weights"] = fetch_body_composition(collector, client, from_date, to_date)
    except FatalSyncError as e:
        result["fatal_error"] = {"kind": e.kind, "message": _short(str(e)), "endpoint": e.endpoint}
    except Exception as e:  # noqa: BLE001 - 예기치 못한 오류도 수집분과 함께 보고
        traceback.print_exc(file=sys.stderr)
        result["fatal_error"] = {"kind": classify_error(e), "message": _short(e), "endpoint": None}

    print(json.dumps(result, ensure_ascii=False, default=str))


if __name__ == "__main__":
    main()
