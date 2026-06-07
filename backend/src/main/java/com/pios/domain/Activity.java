package com.pios.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Table(name = "activities")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Activity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_connection_id")
    private ProviderConnection providerConnection;

    @Column(name = "external_activity_id")
    private String externalActivityId;

    @Column(name = "source_type", nullable = false, length = 20)
    @Builder.Default
    private String sourceType = "GARMIN";

    @Column(name = "activity_type")
    private String activityType;

    @Column(name = "activity_name")
    private String activityName;

    @Column(name = "start_time")
    private LocalDateTime startTime;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "distance_meters", precision = 12, scale = 2)
    private BigDecimal distanceMeters;

    @Column(name = "average_pace_seconds", precision = 10, scale = 2)
    private BigDecimal averagePaceSeconds;

    @Column(name = "average_heart_rate")
    private Integer averageHeartRate;

    @Column(name = "max_heart_rate")
    private Integer maxHeartRate;

    @Column(name = "calories")
    private Integer calories;

    @Column(name = "elevation_gain_meters", precision = 10, scale = 2)
    private BigDecimal elevationGainMeters;

    @Column(name = "user_tag")
    private String userTag;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_payload", columnDefinition = "jsonb")
    private Map<String, Object> rawPayload;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "weight_training_detail", columnDefinition = "jsonb")
    private Map<String, Object> weightTrainingDetail;

    @Column(name = "weather_temperature", precision = 4, scale = 1)
    private BigDecimal weatherTemperature;

    @Column(name = "weather_humidity")
    private Integer weatherHumidity;

    @Column(name = "weather_wind_speed", precision = 4, scale = 1)
    private BigDecimal weatherWindSpeed;

    @Column(name = "weather_condition", length = 50)
    private String weatherCondition;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "weather_raw", columnDefinition = "jsonb")
    private Map<String, Object> weatherRaw;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
