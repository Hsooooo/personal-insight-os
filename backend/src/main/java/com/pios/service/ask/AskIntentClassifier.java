package com.pios.service.ask;

import java.util.List;
import java.util.Map;

public class AskIntentClassifier {

    private static final Map<AskIntent, List<String>> KEYWORDS = Map.of(
            AskIntent.WORKOUT_SUMMARY, List.of(
                    "이번주 운동", "이번 주 운동", "운동 정리", "훈련 일지",
                    "weekly summary", "workout summary", "수행한 운동",
                    "이번주 훈련", "이번 주 훈련", "training log"
            ),
            AskIntent.PERFORMANCE, List.of(
                    "러닝", "페이스", "기록", "성과", "레이스", "marathon",
                    "5k", "10k", "하프", "풀", "마라톤", "속도", "타임"
            ),
            AskIntent.TRAINING, List.of(
                    "훈련", "운동", "강도", "볼륨", "적절", "과훈련", "언더트레이닝",
                    "training", "workout", "load", "volume"
            ),
            AskIntent.SLEEP, List.of(
                    "수면", "잠", "딥슬립", "렘슬립", "수면점수", "수면 시간",
                    "sleep", "deep sleep", "rem sleep"
            ),
            AskIntent.CONDITION, List.of(
                    "컨디션", "피로", "회복", "hrv", "스트레스", "안정시 심박",
                    "바디 배터리", "몸상태", "condition", "fatigue", "recovery",
                    "스트레스", "stress"
            )
    );

    public static AskIntent classify(String question) {
        String q = question.toLowerCase();
        for (AskIntent intent : List.of(
                AskIntent.WORKOUT_SUMMARY,
                AskIntent.PERFORMANCE,
                AskIntent.TRAINING,
                AskIntent.SLEEP,
                AskIntent.CONDITION
        )) {
            for (String keyword : KEYWORDS.get(intent)) {
                if (q.contains(keyword.toLowerCase())) {
                    return intent;
                }
            }
        }
        return AskIntent.GENERAL;
    }
}
