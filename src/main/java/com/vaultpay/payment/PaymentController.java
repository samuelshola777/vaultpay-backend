package com.vaultpay.payment;

import com.vaultpay.payment.request.FundWalletRequest;
import com.vaultpay.payment.response.PaymentInitializationResponse;
import com.vaultpay.payment.response.PaymentVerificationResponse;
import com.vaultpay.userauthmgt.user.User;
import com.vaultpay.utils.ApiResponse;
import com.vaultpay.utils.exception.InvalidInputException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final PaystackClient paystackClient;
    private final PaystackWebhookService webhookService;

    @PostMapping("/private/fund")
    public ResponseEntity<ApiResponse<PaymentInitializationResponse>> initializeFunding(
            @AuthenticationPrincipal User user, @Valid @RequestBody FundWalletRequest request
    ) {
        return ResponseEntity.ok(new ApiResponse<>(
                true, "Payment initialized successfully", paymentService.initializeFunding(user, request)
        ));
    }

    @PostMapping("/private/verify/{reference}")
    public ResponseEntity<ApiResponse<PaymentVerificationResponse>> verifyFunding(
            @AuthenticationPrincipal User user, @PathVariable String reference
    ) {
        return ResponseEntity.ok(new ApiResponse<>(
                true, "Payment verification completed", paymentService.verifyFunding(user, reference)
        ));
    }

    @PostMapping("/public/paystack/webhook")
    public ResponseEntity<Void> paystackWebhook(
            @RequestHeader(value = "x-paystack-signature", required = false) String signature,
            @RequestBody String payload
    ) {
        if (!paystackClient.isValidWebhook(payload, signature)) {
            throw new InvalidInputException("Invalid webhook signature");
        }
        webhookService.process(payload);
        return ResponseEntity.ok().build();
    }
}
