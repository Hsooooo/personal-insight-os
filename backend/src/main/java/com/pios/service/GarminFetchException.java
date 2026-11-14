package com.pios.service;

/**
 * Garmin 스크립트 실행 실패. kind로 인증/레이트리밋(치명) 여부를 구분한다.
 */
public class GarminFetchException extends RuntimeException {

    public enum Kind {
        AUTH, RATE_LIMIT, TIMEOUT, SERVER, NETWORK, CONFIG, SCRIPT_ERROR, OTHER;

        public static Kind fromScriptKind(String kind) {
            if (kind == null) return OTHER;
            return switch (kind) {
                case "auth" -> AUTH;
                case "rate_limit" -> RATE_LIMIT;
                case "server" -> SERVER;
                case "network" -> NETWORK;
                case "config" -> CONFIG;
                default -> OTHER;
            };
        }

        /** 재시도해도 의미 없는 오류: 동기화 전체를 FAILED로 처리하고 남은 청크를 중단한다 */
        public boolean isFatal() {
            return this == AUTH || this == RATE_LIMIT || this == CONFIG;
        }
    }

    private final Kind kind;

    public GarminFetchException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public GarminFetchException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    public Kind getKind() {
        return kind;
    }
}
