package com.vaultpay.utils;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/system")
public class SystemController {

    @GetMapping({"/public/health", "/health"})
    public ResponseEntity<ApiResponse<Map<String, Object>>> health() {
        return ResponseEntity.ok(new ApiResponse<>(
                true,
                "VaultPay is running",
                Map.of("status", "UP", "timestamp", Instant.now())
        ));
    }
}
