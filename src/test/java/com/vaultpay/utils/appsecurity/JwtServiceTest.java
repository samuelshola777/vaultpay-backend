package com.vaultpay.utils.appsecurity;

import com.vaultpay.userauthmgt.user.User;
import com.vaultpay.userauthmgt.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private JwtService jwtService;
    private User user;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(
                "dGVzdC1zZWNyZXQtdGhhdC1pcy1hdC1sZWFzdC0zMi1ieXRlcw==", 60_000, 120_000
        );
        user = User.builder().email("samuel@example.com").role(UserRole.USER).tokenVersion(2).build();
        user.setId(UUID.randomUUID());
    }

    @Test
    void createsDistinctAccessAndRefreshTokens() {
        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = jwtService.generateRefreshToken(user);

        assertThat(jwtService.isValid(accessToken, user, "access")).isTrue();
        assertThat(jwtService.isValid(refreshToken, user, "refresh")).isTrue();
        assertThat(jwtService.isValid(accessToken, user, "refresh")).isFalse();
    }

    @Test
    void tokenVersionRevokesPreviouslyIssuedTokens() {
        String token = jwtService.generateAccessToken(user);
        user.setTokenVersion(3);

        assertThat(jwtService.isValid(token, user, "access")).isFalse();
    }
}
