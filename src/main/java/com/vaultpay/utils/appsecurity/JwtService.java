package com.vaultpay.utils.appsecurity;

import com.vaultpay.userauthmgt.user.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

@Service
public class JwtService {

    private final SecretKey signingKey;
    private final long expirationMs;
    private final long refreshExpirationMs;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-ms}") long expirationMs,
            @Value("${app.jwt.refresh-expiration-ms:604800000}") long refreshExpirationMs
    ) {
        this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        this.expirationMs = expirationMs;
        this.refreshExpirationMs = refreshExpirationMs;
    }

    public String generateAccessToken(User user) {
        return generateToken(user, "access", expirationMs);
    }

    public String generateRefreshToken(User user) {
        return generateToken(user, "refresh", refreshExpirationMs);
    }

    private String generateToken(User user, String tokenType, long validityMs) {
        Instant now = Instant.now();
        return Jwts.builder()
                .claims(Map.of(
                        "userId", user.getId().toString(),
                        "role", user.getRole().name(),
                        "type", tokenType,
                        "version", user.getTokenVersion()
                ))
                .subject(user.getEmail())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(validityMs)))
                .signWith(signingKey)
                .compact();
    }

    public String extractSubject(String token) {
        return parseClaims(token).getSubject();
    }

    public boolean isValid(String token, User user, String expectedType) {
        Claims claims = parseClaims(token);
        Number version = claims.get("version", Number.class);
        return user.getEmail().equalsIgnoreCase(claims.getSubject())
                && expectedType.equals(claims.get("type", String.class))
                && version != null && version.intValue() == user.getTokenVersion()
                && claims.getExpiration().after(new Date());
    }

    public long getExpirationSeconds() {
        return expirationMs / 1000;
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
