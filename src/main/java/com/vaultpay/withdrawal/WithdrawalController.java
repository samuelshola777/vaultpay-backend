package com.vaultpay.withdrawal;

import com.vaultpay.userauthmgt.user.User;
import com.vaultpay.utils.ApiResponse;
import com.vaultpay.withdrawal.request.WithdrawalRequest;
import com.vaultpay.withdrawal.response.WithdrawalResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/withdrawals")
@RequiredArgsConstructor
public class WithdrawalController {

    private final WithdrawalService withdrawalService;

    @PostMapping("/private")
    public ResponseEntity<ApiResponse<WithdrawalResponse>> withdraw(
            @AuthenticationPrincipal User user,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody WithdrawalRequest request
    ) {
        return ResponseEntity.accepted().body(new ApiResponse<>(
                true, "Withdrawal is being processed", withdrawalService.withdraw(user, idempotencyKey, request)
        ));
    }
}
