package com.vaultpay.transaction.response;

import com.vaultpay.transaction.TransactionStatus;
import com.vaultpay.transaction.TransactionType;
import com.vaultpay.wallet.Currency;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record TransactionResponse(
        UUID id,
        String reference,
        TransactionType type,
        TransactionStatus status,
        Currency currency,
        BigDecimal amount,
        BigDecimal fee,
        String sourceWalletNumber,
        String destinationWalletNumber,
        String narration,
        LocalDateTime createdAt
) {
}

