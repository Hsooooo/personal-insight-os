package com.pios.common;

/**
 * 요청한 리소스가 없거나 현재 사용자 소유가 아닐 때 사용한다 (404).
 * 소유권 불일치도 404로 응답해 다른 사용자의 리소스 존재 여부를 노출하지 않는다.
 */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}
