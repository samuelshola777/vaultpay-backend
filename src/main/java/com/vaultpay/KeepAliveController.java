package com.vaultpay;


import com.vaultpay.notification.response.KeepAliveResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/v1/system")
public class KeepAliveController {

    @GetMapping("/keep-alive")
    public ResponseEntity<KeepAliveResponse> keepAlive() {
        KeepAliveResponse response = new KeepAliveResponse(
                "UP",
                "Vault  backend is running",
                Instant.now()
        );

        return ResponseEntity.ok(response);
    }
}