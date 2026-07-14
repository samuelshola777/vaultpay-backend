package com.vaultpay.notification.response;

import com.vaultpay.notification.NotificationType;

import java.time.LocalDateTime;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        NotificationType type,
        String title,
        String message,
        boolean read,
        LocalDateTime createdAt
) {
}

