package com.vaultpay.admin.response;

import com.vaultpay.userauthmgt.user.UserRole;
import com.vaultpay.userauthmgt.user.UserStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record AdminUserResponse(
        UUID id,
        String firstName,
        String lastName,
        String email,
        String phoneNumber,
        UserRole role,
        UserStatus status,
        boolean emailVerified,
        LocalDateTime createdAt
) {
}

