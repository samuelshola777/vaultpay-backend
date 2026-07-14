package com.vaultpay.transaction;

import com.vaultpay.transaction.response.TransactionResponse;
import com.vaultpay.userauthmgt.user.User;
import com.vaultpay.utils.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    @GetMapping("/private")
    public ResponseEntity<ApiResponse<Page<TransactionResponse>>> getMyTransactions(
            @AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(new ApiResponse<>(
                true, "Transactions fetched successfully", transactionService.getMyTransactions(user, page, size)
        ));
    }

    @GetMapping("/private/{reference}")
    public ResponseEntity<ApiResponse<TransactionResponse>> getTransaction(
            @AuthenticationPrincipal User user,
            @PathVariable String reference
    ) {
        return ResponseEntity.ok(new ApiResponse<>(
                true, "Transaction fetched successfully", transactionService.getByReference(user, reference)
        ));
    }
}

