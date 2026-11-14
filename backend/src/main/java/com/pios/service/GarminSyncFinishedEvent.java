package com.pios.service;

/**
 * Garmin 동기화 종료 이벤트 (성공/부분/실패 모두 발행).
 *
 * @param scheduled 스케줄러가 시작한 동기화인지 (이상 신호 감지 체이닝에 사용)
 */
public record GarminSyncFinishedEvent(Long userId, Long syncLogId, String status, boolean scheduled) {
}
