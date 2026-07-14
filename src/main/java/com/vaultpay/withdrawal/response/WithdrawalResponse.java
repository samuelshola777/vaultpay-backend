package com.vaultpay.withdrawal.response;

import com.vaultpay.transaction.TransactionStatus;
import com.vaultpay.wallet.Currency;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record WithdrawalResponse(
        UUID id,
        String reference,
        TransactionStatus status,
        BigDecimal amount,
        BigDecimal fee,
        Currency currency,
        String accountName,
        String accountNumber,
        String bankName,
        LocalDateTime createdAt
) {
}

