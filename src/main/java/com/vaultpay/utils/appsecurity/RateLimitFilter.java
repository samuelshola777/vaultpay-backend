package com.vaultpay.utils.appsecurity;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    @Value("${app.security.rate-limit-requests:20}")
    private int maximumRequests;

    @Value("${app.security.rate-limit-window-seconds:60}")
    private long windowSeconds;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/v1/users/public/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        long now = Instant.now().getEpochSecond();
        String key = clientAddress(request) + "|" + request.getRequestURI();
        Window current = windows.compute(key, (ignored, value) -> {
            if (value == null || now - value.startedAt >= windowSeconds) {
                return new Window(now, 1);
            }
            return new Window(value.startedAt, value.count + 1);
        });
        if (current.count > maximumRequests) {
            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"success\":false,\"message\":\"Too many requests. Try again later\",\"data\":null}");
            return;
        }
        if (windows.size() > 10_000) {
            windows.entrySet().removeIf(entry -> now - entry.getValue().startedAt >= windowSeconds);
        }
        filterChain.doFilter(request, response);
    }

    private String clientAddress(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return forwarded == null || forwarded.isBlank()
                ? request.getRemoteAddr()
                : forwarded.split(",", 2)[0].trim();
    }

    private record Window(long startedAt, int count) {
    }
}
