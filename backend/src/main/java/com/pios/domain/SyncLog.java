package com.pios.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "sync_logs")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SyncLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "provider_type", nullable = false)
    private String providerType;

    @Column(name = "sync_type", nullable = false)
    private String syncType;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "date_from")
    private LocalDate dateFrom;

    @Column(name = "date_to")
    private LocalDate dateTo;

    @Column(name = "activities_count")
    private Integer activitiesCount = 0;

    @Column(name = "health_metrics_count")
    private Integer healthMetricsCount = 0;

    @Column(name = "sleep_count")
    private Integer sleepCount = 0;

    @Column(name = "weights_count")
    private Integer weightsCount = 0;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
