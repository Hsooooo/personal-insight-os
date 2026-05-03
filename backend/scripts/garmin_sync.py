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

def fetch_activities(client, start_date, end_date):
    """Garmin 활동 목록 조회"""
    results = []
    try:
        # get_activities_by_date는 start, end 순서로 받음
        activities = client.get_activities_by_date(
            start_date.strftime("%Y-%m-%d"),
            end_date.strftime("%Y-%m-%d")
        )
        for a in activities:
            results.append({
                "garmin_activity_id": str(a.get("activityId", "")),
                "activity_type": a.get("activityType", {}).get("typeKey", ""),
                "activity_name": a.get("activityName", ""),
                "start_time": a.get("startTimeLocal", ""),
                "duration_seconds": a.get("duration", 0),
                "distance_meters": a.get("distance", 0),
                "average_pace_seconds": a.get("averageSpeed", 0),
                "average_heart_rate": a.get("averageHR", 0),
                "max_heart_rate": a.get("maxHR", 0),
                "calories": a.get("calories", 0),
                "elevation_gain_meters": a.get("elevationGain", 0),
                "raw_payload": a
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

    result = {"activities": [], "health": [], "sleep": []}

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

        print(json.dumps(result, ensure_ascii=False, default=str))
    except Exception as e:
        traceback.print_exc()
        print(json.dumps({"error": str(e)}), file=sys.stderr)
        sys.exit(1)

if __name__ == "__main__":
    main()
