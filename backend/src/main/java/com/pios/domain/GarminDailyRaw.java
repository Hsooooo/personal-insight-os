package com.pios.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

/**
 * Garmin 일일 부가 엔드포인트 원문 저장 (HRV, Body Battery, Stress 등).
 * 필드 손실 없이 jsonb로 전량 보존하고, (user, 날짜, 데이터 타입) 단위로 upsert한다.
 */
@Entity
@Table(name = "garmin_daily_raw",
       uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "metric_date", "data_type"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class GarminDailyRaw {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "metric_date", nullable = false)
    private LocalDate metricDate;

    @Column(name = "data_type", nullable = false, length = 30)
    private String dataType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> payload;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
