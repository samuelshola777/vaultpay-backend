package com.vaultpay.wallet.response;

import com.vaultpay.wallet.WalletStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record WalletResponse(
        UUID id,
        String walletNumber,
        WalletStatus status,
        boolean pinSet,
        List<WalletBalanceResponse> balances,
        LocalDateTime createdAt
) {
}

