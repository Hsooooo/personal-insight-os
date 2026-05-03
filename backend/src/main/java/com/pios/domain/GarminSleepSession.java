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

@Entity
@Table(name = "garmin_sleep_sessions",
       uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "sleep_date"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class GarminSleepSession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "sleep_date", nullable = false)
    private LocalDate sleepDate;

    @Column(name = "start_time")
    private Instant startTime;

    @Column(name = "end_time")
    private Instant endTime;

    @Column(name = "total_sleep_seconds")
    private Integer totalSleepSeconds;

    @Column(name = "deep_sleep_seconds")
    private Integer deepSleepSeconds;

    @Column(name = "light_sleep_seconds")
    private Integer lightSleepSeconds;

    @Column(name = "rem_sleep_seconds")
    private Integer remSleepSeconds;

    @Column(name = "awake_seconds")
    private Integer awakeSeconds;

    @Column(name = "sleep_score")
    private Integer sleepScore;

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
