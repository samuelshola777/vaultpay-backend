package com.vaultpay.payment.response;

import com.vaultpay.payment.PaymentAttemptStatus;
import com.vaultpay.wallet.Currency;

import java.math.BigDecimal;

public record PaymentVerificationResponse(
        String reference,
        BigDecimal amount,
        Currency currency,
        PaymentAttemptStatus status
) {
}
