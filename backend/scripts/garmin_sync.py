#!/usr/bin/env python3
"""
Garmin Connect 데이터 동기화 스크립트
Java 백엔드에서 ProcessBuilder로 호출

Usage:
    python garmin_sync.py <email> <password> <from_date> <to_date> <data_type>

data_type: activities | health | sleep | all

Output: JSON array to stdout
Errors: JSON {"error": "..."} to stderr, exit code != 0
"""

import json
import sys
import traceback
from datetime import datetime, timedelta, timezone

def parse_date(s):
    return datetime.strptime(s, "%Y-%m-%d").date()

def _parse_laps(client, activity_id):
    """활동의 랩(splits) 데이터를 조회. 랩이 없거나 오류 발생 시 빈 리스트 반환."""
    try:
        splits = client.get_activity_splits(activity_id)
        lap_dtos = splits.get("lapDTOs", [])
        laps = []
        for lap in lap_dtos:
            duration = lap.get("duration")
            distance = lap.get("distance")
            avg_speed = lap.get("averageSpeed")
            # Garmin API의 averageSpeed는 m/s 단위. pace(sec/km) = 1000 / speed (0이면 None)
            avg_pace = None
            if avg_speed and avg_speed > 0:
                avg_pace = round(1000.0 / avg_speed, 4)
            laps.append({
                "lap_index": lap.get("lapIndex"),
                "start_time": lap.get("startTimeLocal") or lap.get("startTimeGMT"),
                "duration_seconds": duration,
                "distance_meters": distance,
                "average_pace_seconds": avg_pace,
                "average_heart_rate": lap.get("averageHR"),
                "max_heart_rate": lap.get("maxHR"),
                "raw_payload": lap
            })
        return laps
    except Exception:
        return []


def fetch_activities(client, start_date, end_date):
    """Garmin 활동 목록 조회 (랩 데이터 포함)"""
    results = []
    try:
        # get_activities_by_date는 start, end 순서로 받음
        activities = client.get_activities_by_date(
            start_date.strftime("%Y-%m-%d"),
            end_date.strftime("%Y-%m-%d")
        )
        for a in activities:
            activity_id = a.get("activityId")
            laps = _parse_laps(client, activity_id) if activity_id else []
            results.append({
                "garmin_activity_id": str(activity_id or ""),
                "activity_type": a.get("activityType", {}).get("typeKey", ""),
                "activity_name": a.get("activityName", ""),
                "start_time": a.get("startTimeLocal", ""),
                "duration_seconds": a.get("duration", 0),
                "distance_meters": a.get("distance", 0),
                "average_pace_seconds": round(1000.0 / a.get("averageSpeed"), 4) if a.get("averageSpeed") and a.get("averageSpeed") > 0 else None,
                "average_heart_rate": a.get("averageHR", 0),
                "max_heart_rate": a.get("maxHR", 0),
                "calories": a.get("calories", 0),
                "elevation_gain_meters": a.get("elevationGain", 0),
                "raw_payload": a,
                "laps": laps
            })
    except Exception as e:
        raise RuntimeError(f"activities fetch failed: {e}")
    return results

def fetch_health(client, date):
    """일일 건강 지표 조회"""
    try:
        stats = client.get_stats(date.strftime("%Y-%m-%d"))
        return {
            "metric_date": date.strftime("%Y-%m-%d"),
            "resting_heart_rate": stats.get("restingHeartRate", 0),
            "hrv_avg": stats.get("hrvAverage", 0),
            "stress_avg": stats.get("averageStressLevel", 0),
            "body_battery_min": stats.get("bodyBatteryDuringSleep", {}).get("min", 0) if isinstance(stats.get("bodyBatteryDuringSleep"), dict) else 0,
            "body_battery_max": stats.get("bodyBatteryDuringSleep", {}).get("max", 0) if isinstance(stats.get("bodyBatteryDuringSleep"), dict) else 0,
            "steps": stats.get("totalSteps", 0),
            "calories_total": stats.get("totalKilocalories", 0),
            "raw_payload": stats
        }
    except Exception as e:
        # 일부 날짜는 데이터가 없을 수 있음
        return None

def _ms_to_iso(timestamp_ms):
    """밀리초 Unix timestamp를 ISO 문자열로 변환"""
    if not timestamp_ms:
        return ""
    return datetime.fromtimestamp(timestamp_ms / 1000, tz=timezone.utc).isoformat()


def fetch_sleep(client, date):
    """수면 데이터 조회"""
    try:
        sleep = client.get_sleep_data(date.strftime("%Y-%m-%d"))
        sleep_summary = sleep.get("dailySleepDTO", {})
        if not sleep_summary:
            return None
        return {
            "sleep_date": date.strftime("%Y-%m-%d"),
            "start_time": _ms_to_iso(sleep_summary.get("sleepStartTimestampGMT")),
            "end_time": _ms_to_iso(sleep_summary.get("sleepEndTimestampGMT")),
            "total_sleep_seconds": sleep_summary.get("sleepTimeSeconds", 0),
            "deep_sleep_seconds": sleep_summary.get("deepSleepSeconds", 0),
            "light_sleep_seconds": sleep_summary.get("lightSleepSeconds", 0),
            "rem_sleep_seconds": sleep_summary.get("remSleepSeconds", 0),
            "awake_seconds": sleep_summary.get("awakeSleepSeconds", 0),
            "sleep_score": sleep_summary.get("sleepScores", {}).get("overall", {}).get("value", 0),
            "raw_payload": sleep
        }
    except Exception as e:
        return None

def fetch_body_composition(client, start_date, end_date):
    """체중/신체조성 데이터 조회 (기간 단위)"""
    try:
        data = client.get_body_composition(
            start_date.strftime("%Y-%m-%d"),
            end_date.strftime("%Y-%m-%d")
        )
        weight_list = data.get("dateWeightList", [])
        # 같은 날 여러 건이면 마지막 측정값 사용
        date_map = {}
        for entry in weight_list:
            calendar_date = entry.get("calendarDate")
            weight = entry.get("weight")
            if calendar_date and weight is not None:
                date_map[calendar_date] = {
                    "metric_date": calendar_date,
                    "weight_kg": weight,
                    "bmi": entry.get("bmi"),
                    "body_fat": entry.get("bodyFat"),
                    "raw_payload": entry
                }
        return list(date_map.values())
    except Exception as e:
        return []

def main():
    if len(sys.argv) < 6:
        print(json.dumps({"error": "Usage: garmin_sync.py <email> <password> <from_date> <to_date> <data_type>"}), file=sys.stderr)
        sys.exit(1)

    email = sys.argv[1]
    password = sys.argv[2]
    from_date = parse_date(sys.argv[3])
    to_date = parse_date(sys.argv[4])
    data_type = sys.argv[5]

    try:
        from garminconnect import Garmin
    except ImportError:
        print(json.dumps({"error": "garminconnect library not installed"}), file=sys.stderr)
        sys.exit(1)

    try:
        client = Garmin(email, password)
        client.login()
    except Exception as e:
        print(json.dumps({"error": f"login failed: {e}"}), file=sys.stderr)
        sys.exit(1)

    result = {"activities": [], "health": [], "sleep": [], "weights": []}

    try:
        if data_type in ("activities", "all"):
            result["activities"] = fetch_activities(client, from_date, to_date)

        if data_type in ("health", "sleep", "all"):
            current = from_date
            while current <= to_date:
                if data_type in ("health", "all"):
                    h = fetch_health(client, current)
                    if h:
                        result["health"].append(h)
                if data_type in ("sleep", "all"):
                    s = fetch_sleep(client, current)
                    if s:
                        result["sleep"].append(s)
                current += timedelta(days=1)

        if data_type in ("health", "all"):
            result["weights"] = fetch_body_composition(client, from_date, to_date)

        print(json.dumps(result, ensure_ascii=False, default=str))
    except Exception as e:
        traceback.print_exc()
        print(json.dumps({"error": str(e)}), file=sys.stderr)
        sys.exit(1)

if __name__ == "__main__":
    main()
