package com.pios.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class WeatherService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public WeatherData fetchWeather(double latitude, double longitude, LocalDateTime startTime) {
        try {
            LocalDateTime hour = startTime.withMinute(0).withSecond(0).withNano(0);
            String date = hour.toLocalDate().toString();
            int hourOfDay = hour.getHour();

            String url = UriComponentsBuilder.fromHttpUrl("https://api.open-meteo.com/v1/forecast")
                    .queryParam("latitude", latitude)
                    .queryParam("longitude", longitude)
                    .queryParam("start_date", date)
                    .queryParam("end_date", date)
                    .queryParam("hourly", "temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m")
                    .queryParam("timezone", "auto")
                    .toUriString();

            JsonNode response = restTemplate.getForObject(url, JsonNode.class);
            if (response == null || response.has("error")) {
                log.warn("Open-Meteo API returned error or null for lat={}, lon={}, date={}", latitude, longitude, date);
                return null;
            }

            JsonNode hourly = response.get("hourly");
            if (hourly == null) {
                return null;
            }

            JsonNode times = hourly.get("time");
            int index = -1;
            for (int i = 0; i < times.size(); i++) {
                String t = times.get(i).asText();
                if (t.endsWith(String.format("T%02d:00", hourOfDay))) {
                    index = i;
                    break;
                }
            }

            if (index < 0) {
                return null;
            }

            BigDecimal temperature = getBigDecimal(hourly.get("temperature_2m"), index);
            Integer humidity = getInt(hourly.get("relative_humidity_2m"), index);
            BigDecimal windSpeed = getBigDecimal(hourly.get("wind_speed_10m"), index);
            Integer weatherCode = getInt(hourly.get("weather_code"), index);

            Map<String, Object> raw = new HashMap<>();
            raw.put("temperature_2m", temperature);
            raw.put("relative_humidity_2m", humidity);
            raw.put("wind_speed_10m", windSpeed);
            raw.put("weather_code", weatherCode);
            raw.put("hour", hourOfDay);

            return WeatherData.builder()
                    .temperature(temperature)
                    .humidity(humidity)
                    .windSpeed(windSpeed)
                    .condition(translateWeatherCode(weatherCode))
                    .raw(raw)
                    .build();
        } catch (Exception e) {
            log.warn("Failed to fetch weather for lat={}, lon={}, time={}: {}", latitude, longitude, startTime, e.getMessage());
            return null;
        }
    }

    private BigDecimal getBigDecimal(JsonNode array, int index) {
        if (array == null || !array.has(index)) return null;
        JsonNode node = array.get(index);
        return node.isNull() ? null : BigDecimal.valueOf(node.asDouble());
    }

    private Integer getInt(JsonNode array, int index) {
        if (array == null || !array.has(index)) return null;
        JsonNode node = array.get(index);
        return node.isNull() ? null : node.asInt();
    }

    private String translateWeatherCode(Integer code) {
        if (code == null) return "Unknown";
        return switch (code) {
            case 0 -> "Clear sky";
            case 1 -> "Mainly clear";
            case 2 -> "Partly cloudy";
            case 3 -> "Overcast";
            case 45, 48 -> "Fog";
            case 51, 53, 55 -> "Drizzle";
            case 56, 57 -> "Freezing drizzle";
            case 61, 63, 65 -> "Rain";
            case 66, 67 -> "Freezing rain";
            case 71, 73, 75, 77 -> "Snow";
            case 80, 81, 82 -> "Rain showers";
            case 85, 86 -> "Snow showers";
            case 95, 96, 99 -> "Thunderstorm";
            default -> "Unknown";
        };
    }

    @lombok.Builder
    @lombok.Data
    public static class WeatherData {
        private BigDecimal temperature;
        private Integer humidity;
        private BigDecimal windSpeed;
        private String condition;
        private Map<String, Object> raw;
    }
}
