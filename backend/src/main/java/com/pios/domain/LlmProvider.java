package com.pios.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "llm_providers",
       uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "provider_name"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LlmProvider {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "provider_name", nullable = false)
    private String providerName;

    @Column(name = "api_key_encrypted")
    private String apiKeyEncrypted;

    @Column(name = "default_chat_model")
    private String defaultChatModel;

    @Column(name = "embedding_model")
    private String embeddingModel;

    @Column(nullable = false)
    private Boolean enabled;

    @Column(name = "monthly_budget_limit", precision = 12, scale = 2)
    private BigDecimal monthlyBudgetLimit;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
