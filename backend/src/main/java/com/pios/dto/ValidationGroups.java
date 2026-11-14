package com.pios.dto;

/** Bean Validation 그룹. 생성 요청에만 필수인 필드에 사용한다 (PATCH는 부분 업데이트). */
public final class ValidationGroups {
    private ValidationGroups() {}

    public interface Create {}
}
