package com.pios.service.ask;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AskIntentClassifierTest {

    @Test
    void conditionIntent() {
        assertThat(AskIntentClassifier.classify("최근 컨디션이 왜 떨어졌어")).isEqualTo(AskIntent.CONDITION);
        assertThat(AskIntentClassifier.classify("HRV가 낮은 이유가 뭐야")).isEqualTo(AskIntent.CONDITION);
        assertThat(AskIntentClassifier.classify("스트레스 많은 날 패턴")).isEqualTo(AskIntent.CONDITION);
    }

    @Test
    void sleepIntent() {
        assertThat(AskIntentClassifier.classify("수면이 부족하면 어때")).isEqualTo(AskIntent.SLEEP);
        assertThat(AskIntentClassifier.classify("딥슬립이 줄었어")).isEqualTo(AskIntent.SLEEP);
    }

    @Test
    void trainingIntent() {
        assertThat(AskIntentClassifier.classify("훈련 강도가 적절했어")).isEqualTo(AskIntent.TRAINING);
        assertThat(AskIntentClassifier.classify("운동 강도가 적절했어")).isEqualTo(AskIntent.TRAINING);
    }

    @Test
    void performanceIntent() {
        assertThat(AskIntentClassifier.classify("러닝 기록이 좋았던 날")).isEqualTo(AskIntent.PERFORMANCE);
        assertThat(AskIntentClassifier.classify("10K 페이스 분석")).isEqualTo(AskIntent.PERFORMANCE);
    }

    @Test
    void workoutSummaryIntent() {
        assertThat(AskIntentClassifier.classify("이번 주 운동 정리해줘")).isEqualTo(AskIntent.WORKOUT_SUMMARY);
        assertThat(AskIntentClassifier.classify("training log")).isEqualTo(AskIntent.WORKOUT_SUMMARY);
    }

    @Test
    void generalIntent() {
        assertThat(AskIntentClassifier.classify("오늘 날씨 어때")).isEqualTo(AskIntent.GENERAL);
    }
}
