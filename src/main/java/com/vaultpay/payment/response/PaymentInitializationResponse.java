package com.vaultpay.payment.response;

import java.math.BigDecimal;

public record PaymentInitializationResponse(
        String reference,
        BigDecimal amount,
        String currency,
        String authorizationUrl,
        String accessCode
) {
}

