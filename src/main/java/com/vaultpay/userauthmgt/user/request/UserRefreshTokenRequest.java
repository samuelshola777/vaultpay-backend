package com.vaultpay.userauthmgt.user.request;

import jakarta.validation.constraints.NotBlank;

public record UserRefreshTokenRequest(@NotBlank String refreshToken) {
}
