package com.vaultpay.activitylog;

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

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "activity_logs", indexes = @Index(name = "activity_logs_user_idx", columnList = "user_id, created_at"))
public class ActivityLog extends BaseEntity {

    @Column(name = "user_id")
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ActivityAction action;

    @Column(nullable = false, length = 50)
    private String module;

    @Column(nullable = false, length = 500)
    private String description;

    @Column(length = 80)
    private String ipAddress;
}

