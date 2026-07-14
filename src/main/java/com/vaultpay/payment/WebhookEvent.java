package com.vaultpay.payment;

import com.vaultpay.utils.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "webhook_events", indexes = {
        @Index(name = "webhook_events_key_idx", columnList = "event_key", unique = true),
        @Index(name = "webhook_events_status_idx", columnList = "status, created_at")
})
public class WebhookEvent extends BaseEntity {

    @Column(name = "event_key", nullable = false, unique = true, length = 64)
    private String eventKey;

    @Column(nullable = false, length = 40)
    private String provider;

    @Column(nullable = false, length = 80)
    private String eventType;

    @Column(length = 100)
    private String reference;

    @Column(nullable = false, length = 64)
    private String payloadHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WebhookEventStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column(length = 500)
    private String failureReason;

    private LocalDateTime processedAt;
}
