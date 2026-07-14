package com.vaultpay.wallet.response;

import com.vaultpay.wallet.Currency;

import java.math.BigDecimal;

public record WalletBalanceResponse(
        Currency currency,
        BigDecimal availableBalance,
        BigDecimal heldBalance,
        BigDecimal totalBalance
) {
}

