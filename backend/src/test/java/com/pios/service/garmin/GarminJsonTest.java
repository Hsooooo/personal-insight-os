package com.pios.service.garmin;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GarminJsonTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final GarminJson json = new GarminJson(mapper);

    @Test
    void payloadMapKeepsArrayPayloads() throws Exception {
        // get_body_battery()/get_training_readiness()는 배열을 반환한다 — 예전에는 {}로 저장되어 원문이 사라졌다
        var node = mapper.readTree("[{\"date\":\"2026-11-14\",\"charged\":20,\"drained\":20}]");

        Map<String, Object> payload = json.toPayloadMap(node);

        assertThat(payload).containsKey("items");
        assertThat((List<?>) payload.get("items")).hasSize(1);
    }

    @Test
    void payloadMapKeepsObjectsAsIs() throws Exception {
        var node = mapper.readTree("{\"hrvSummary\":{\"lastNightAvg\":69}}");

        assertThat(json.toPayloadMap(node)).containsOnlyKeys("hrvSummary");
    }
}
