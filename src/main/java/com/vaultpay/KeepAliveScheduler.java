package com.vaultpay;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Component
public class KeepAliveScheduler {

    private static final Logger log = LoggerFactory.getLogger(KeepAliveScheduler.class);
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Value("${app.paystack.callback-url}")
    private String backendBaseUrl;

    private static final String KEEP_ALIVE_URL =
            "https://vaultpay-backend-hwoq.onrender.com/api/v1/system/keep-alive";

    private final HttpClient httpClient;
    private LocalDateTime lastCallTime;
    private LocalDateTime nextCallTime;
    private int successCount = 0;
    private int failureCount = 0;

    public KeepAliveScheduler() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        this.lastCallTime = null;
        this.nextCallTime = LocalDateTime.now().plusSeconds(60);
    }

    @Scheduled(fixedDelay = 60_000)
    public void keepServerAwake() {
        LocalDateTime currentTime = LocalDateTime.now();
        this.lastCallTime = currentTime;
        this.nextCallTime = currentTime.plusSeconds(60);

        log.info("========================================");
        log.info("KEEP-ALIVE SCHEDULER TRIGGERED");
        log.info("Current Time: {}", currentTime.format(formatter));
        log.info("Last Call: {}", lastCallTime.format(formatter));
        log.info("Next Call: {}", nextCallTime.format(formatter));
        log.info("Success Count: {}, Failure Count: {}", successCount, failureCount);
        log.info("========================================");

        try {
            long startTime = System.currentTimeMillis();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(KEEP_ALIVE_URL))
                    .timeout(Duration.ofSeconds(60))
                    .header("User-Agent", "VaultPay-KeepAlive")
                    .GET()
                    .build();

            log.info("Sending keep-alive request to: {}", KEEP_ALIVE_URL);

            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

            long endTime = System.currentTimeMillis();
            long responseTime = endTime - startTime;

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                successCount++;
                log.info("✅ Keep-alive SUCCESS");
                log.info("   Status Code: {}", response.statusCode());
                log.info("   Response Time: {}ms", responseTime);
                log.info("   Response Body Length: {} characters",
                        response.body() != null ? response.body().length() : 0);
            } else {
                failureCount++;
                log.warn("⚠️ Keep-alive returned non-success status");
                log.warn("   Status Code: {}", response.statusCode());
                log.warn("   Response Time: {}ms", responseTime);
            }

        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            failureCount++;
            log.error("❌ Keep-alive request was interrupted", exception);

        } catch (Exception exception) {
            failureCount++;
            log.error("❌ Keep-alive request failed", exception);
        }

        log.info("========================================\n");
    }

    public void getStatus() {
        log.info("Keep-Alive Scheduler Status:");
        log.info("  Last Call: {}", lastCallTime != null ? lastCallTime.format(formatter) : "Never");
        log.info("  Next Call: {}", nextCallTime != null ? nextCallTime.format(formatter) : "Not scheduled");
        log.info("  Success Count: {}", successCount);
        log.info("  Failure Count: {}", failureCount);
        log.info("  Total Attempts: {}", successCount + failureCount);
    }
}