package com.vaultpay.userauthmgt.user.response;

public record UserAuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        UserResponse user
) {
}
