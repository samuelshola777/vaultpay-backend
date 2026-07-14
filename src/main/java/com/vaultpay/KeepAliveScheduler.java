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

@Component
public class KeepAliveScheduler {

    private static final Logger log =
            LoggerFactory.getLogger(KeepAliveScheduler.class);
    @Value("${app.paystack.callback-url}")
    private String backendBaseUrl;

    private static final String KEEP_ALIVE_URL =
            "https://vaultpay-backend-hwoq.onrender.com/api/v1/system/keep-alive";

    private final HttpClient httpClient;

    public KeepAliveScheduler() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    @Scheduled(
            initialDelay = 60_000,
            fixedDelay = 600_000
    )
    public void keepServerAwake() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(KEEP_ALIVE_URL))
                    .timeout(Duration.ofSeconds(60))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.info("Keep-alive successful: {}", response.statusCode());
            } else {
                log.warn(
                        "Keep-alive returned status: {}",
                        response.statusCode()
                );
            }

        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.error("Keep-alive request was interrupted", exception);

        } catch (Exception exception) {
            log.error("Keep-alive request failed", exception);
        }
    }
}