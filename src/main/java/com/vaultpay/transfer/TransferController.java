package com.vaultpay.transfer;

import com.vaultpay.transaction.response.TransactionResponse;
import com.vaultpay.transfer.request.WalletTransferRequest;
import com.vaultpay.userauthmgt.user.User;
import com.vaultpay.utils.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/transfers")
@RequiredArgsConstructor
public class TransferController {

    private final TransferService transferService;

    @PostMapping("/private/wallet")
    public ResponseEntity<ApiResponse<TransactionResponse>> transfer(
            @AuthenticationPrincipal User user,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody WalletTransferRequest request
    ) {
        return ResponseEntity.ok(new ApiResponse<>(
                true, "Transfer completed successfully", transferService.transfer(user, idempotencyKey, request)
        ));
    }
}

