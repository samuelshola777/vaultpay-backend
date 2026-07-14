package com.vaultpay.notification.response;

import java.time.Instant;

public record KeepAliveResponse(
        String status,
        String message,
        Instant timestamp
) {
}