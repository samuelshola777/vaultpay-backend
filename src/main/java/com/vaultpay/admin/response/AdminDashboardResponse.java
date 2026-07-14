package com.vaultpay.admin.response;

public record AdminDashboardResponse(
        long totalUsers,
        long activeUsers,
        long totalWallets,
        long frozenWallets,
        long totalTransactions,
        long completedTransactions,
        long pendingTransactions
) {
}

