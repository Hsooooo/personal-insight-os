package com.pios.service.garmin;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pios.common.AppTimeZones;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * garmin_sync.py 출력(JSON) 파싱 헬퍼. 누락/null 값은 null로 유지한다 (0으로 채우지 않음).
 */
@Component
@RequiredArgsConstructor
public class GarminJson {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final ObjectMapper objectMapper;

    public String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) return null;
        String text = value.asText();
        return text.isEmpty() ? null : text;
    }

    public Integer integer(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || !value.isNumber()) return null;
        return (int) Math.round(value.asDouble());
    }

    /** 생리학적으로 0이 불가능한 지표용: 0 이하는 null */
    public Integer positiveInteger(JsonNode node, String field) {
        Integer v = integer(node, field);
        return v != null && v > 0 ? v : null;
    }

    public BigDecimal decimal(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || !value.isNumber()) return null;
        return BigDecimal.valueOf(value.asDouble());
    }

    public BigDecimal positiveDecimal(JsonNode node, String field) {
        BigDecimal v = decimal(node, field);
        return v != null && v.signum() > 0 ? v : null;
    }

    public Map<String, Object> toMap(JsonNode node) {
        if (node == null || node.isNull() || !node.isObject()) return new HashMap<>();
        try {
            return objectMapper.convertValue(node, MAP_TYPE);
        } catch (IllegalArgumentException e) {
            return new HashMap<>();
        }
    }

    public LocalDate date(String text) {
        if (text == null || text.isEmpty()) return null;
        try {
            return LocalDate.parse(text);
        } catch (Exception e) {
            return null;
        }
    }

    public LocalDateTime localDateTime(String text) {
        if (text == null || text.isEmpty()) return null;
        try {
            String normalized = text.replace(" ", "T");
            if (normalized.endsWith(".0")) {
                normalized = normalized.substring(0, normalized.length() - 2);
            }
            return LocalDateTime.parse(normalized, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (Exception e) {
            return null;
        }
    }

    /** 오프셋 포함 ISO(UTC) 또는 로컬 시각(KST로 간주) 문자열을 Instant로 */
    public Instant instant(String text) {
        if (text == null || text.isEmpty()) return null;
        try {
            return OffsetDateTime.parse(text).toInstant();
        } catch (Exception ignored) {
            // fall through
        }
        try {
            return Instant.parse(text);
        } catch (Exception ignored) {
            // fall through
        }
        LocalDateTime local = localDateTime(text);
        return local != null ? local.atZone(AppTimeZones.KST).toInstant() : null;
    }
}
