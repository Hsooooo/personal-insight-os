package com.pios.service.garmin;

import com.pios.domain.Activity;
import com.pios.repository.ActivityRepository;
import com.pios.service.WeatherService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 활동 날씨 보강. 외부 API 호출은 DB 트랜잭션 밖에서 하고, 결과만 짧은 트랜잭션으로 반영한다.
 * 이미 날씨가 있는 활동은 후보에서 제외된다({@link GarminActivityPersister}).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ActivityWeatherEnricher {

    private final WeatherService weatherService;
    private final ActivityRepository activityRepo;
    private final TransactionTemplate transactionTemplate;

    /** @return 날씨가 저장된 활동 수 */
    public int enrich(List<GarminActivityPersister.WeatherCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) return 0;

        Map<Long, WeatherService.WeatherData> fetched = new HashMap<>();
        for (GarminActivityPersister.WeatherCandidate c : candidates) {
            try {
                WeatherService.WeatherData weather =
                        weatherService.fetchWeather(c.latitude(), c.longitude(), c.startTime());
                if (weather != null) {
                    fetched.put(c.activityId(), weather);
                }
            } catch (Exception e) {
                log.warn("Weather fetch failed for activity {}: {}", c.activityId(), e.getMessage());
            }
        }
        if (fetched.isEmpty()) return 0;

        try {
            Integer updated = transactionTemplate.execute(status -> {
                List<Activity> activities = activityRepo.findAllById(fetched.keySet());
                for (Activity a : activities) {
                    WeatherService.WeatherData w = fetched.get(a.getId());
                    a.setWeatherTemperature(w.getTemperature());
                    a.setWeatherHumidity(w.getHumidity());
                    a.setWeatherWindSpeed(w.getWindSpeed());
                    a.setWeatherCondition(w.getCondition());
                    a.setWeatherRaw(w.getRaw());
                }
                activityRepo.saveAll(activities);
                return activities.size();
            });
            return updated != null ? updated : 0;
        } catch (Exception e) {
            log.warn("Saving weather for {} activities failed: {}", fetched.size(), e.getMessage());
            return 0;
        }
    }
}
