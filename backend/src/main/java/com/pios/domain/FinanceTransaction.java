package com.pios.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

@Entity
@Table(name = "finance_transactions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class FinanceTransaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cycle_id")
    private FinanceCycle cycle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "linked_template_version_id")
    private RecurringBillTemplateVersion linkedTemplateVersion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id")
    private FinanceAccount account;

    @Column(name = "transaction_at", nullable = false)
    private Instant transactionAt;

    @Column(name = "transaction_date", nullable = false)
    private LocalDate transactionDate;

    private String asset;
    private String category;
    private String subcategory;
    private String description;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "flow_type", nullable = false)
    private String flowType;

    @Column(columnDefinition = "text")
    private String memo;

    @Column(nullable = false)
    private String currency;

    @Column(name = "source_fingerprint", nullable = false)
    private String sourceFingerprint;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "source_row", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> sourceRow;

    @Column(name = "cashflow_included", nullable = false)
    private boolean cashflowIncluded;

    @Column(name = "spending_included", nullable = false)
    private boolean spendingIncluded;

    @Column(name = "payment_method")
    private String paymentMethod;

    @Column(name = "time_adjusted", nullable = false)
    private boolean timeAdjusted;

    @Column(name = "time_adjusted_at")
    private Instant timeAdjustedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
