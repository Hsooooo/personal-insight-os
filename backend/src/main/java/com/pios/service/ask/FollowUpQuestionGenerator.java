package com.pios.service.ask;

import com.pios.dto.AskEvidence;
import lombok.experimental.UtilityClass;

import java.util.ArrayList;
import java.util.List;

@UtilityClass
public class FollowUpQuestionGenerator {

    public static List<String> generate(AskIntent intent, List<AskEvidence> evidences) {
        List<String> questions = new ArrayList<>();
        switch (intent) {
            case CONDITION -> {
                questions.add("수면과 HRV 변화를 날짜별로 비교해줘");
                questions.add("스트레스가 높았던 날의 활동 패턴은?");
            }
            case SLEEP -> {
                questions.add("수면 점수가 낮았던 날의 공통점은?");
                questions.add("딥슬립과 REM 비율을 알려줘");
            }
            case TRAINING -> {
                questions.add("훈련 강도가 높은 날의 회복 지표는?");
                questions.add("이번 주와 지난 주 훈련량을 비교해줘");
            }
            case PERFORMANCE -> {
                questions.add("최근 러닝 기록 중 가장 좋았던 날은?");
                questions.add("페이스와 심박수의 관계는?");
            }
            case WORKOUT_SUMMARY -> {
                questions.add("특정 울등의 상세 분석이나 다음 주 훈련 계획이 필요하신가요?");
                questions.add("이번 주 가장 강도 높은 훈련은?");
            }
            default -> {
                questions.add("최근 컨디션 변화의 주요 원인은?");
                questions.add("어떤 지표를 더 자세히 분석해드릴까요?");
            }
        }
        return questions.subList(0, Math.min(2, questions.size()));
    }
}
