package com.vaultpay.wallet;

import com.vaultpay.userauthmgt.user.User;
import com.vaultpay.utils.ApiResponse;
import com.vaultpay.wallet.request.WalletPinChangeRequest;
import com.vaultpay.wallet.request.WalletPinSetupRequest;
import com.vaultpay.wallet.response.WalletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/wallets")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;

    @GetMapping("/private/me")
    public ResponseEntity<ApiResponse<WalletResponse>> getMyWallet(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(new ApiResponse<>(
                true,
                "Wallet fetched successfully",
                walletService.getWallet(user)
        ));
    }

    @PostMapping("/private/pin")
    public ResponseEntity<ApiResponse<WalletResponse>> setupPin(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody WalletPinSetupRequest request
    ) {
        return ResponseEntity.ok(new ApiResponse<>(
                true,
                "Wallet PIN created successfully",
                walletService.setupPin(user, request)
        ));
    }

    @PutMapping("/private/pin")
    public ResponseEntity<ApiResponse<WalletResponse>> changePin(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody WalletPinChangeRequest request
    ) {
        return ResponseEntity.ok(new ApiResponse<>(
                true,
                "Wallet PIN changed successfully",
                walletService.changePin(user, request)
        ));
    }
}

