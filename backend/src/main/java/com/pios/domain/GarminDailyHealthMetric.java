package com.pios.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

@Entity
@Table(name = "garmin_daily_health_metrics",
       uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "metric_date"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class GarminDailyHealthMetric {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "metric_date", nullable = false)
    private LocalDate metricDate;

    @Column(name = "resting_heart_rate")
    private Integer restingHeartRate;

    @Column(name = "hrv_avg", precision = 10, scale = 2)
    private BigDecimal hrvAvg;

    @Column(name = "stress_avg", precision = 10, scale = 2)
    private BigDecimal stressAvg;

    @Column(name = "body_battery_min")
    private Integer bodyBatteryMin;

    @Column(name = "body_battery_max")
    private Integer bodyBatteryMax;

    @Column(name = "steps")
    private Integer steps;

    @Column(name = "calories_total")
    private Integer caloriesTotal;

    @Column(name = "weight_kg", precision = 5, scale = 2)
    private BigDecimal weightKg;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_payload", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> rawPayload;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
