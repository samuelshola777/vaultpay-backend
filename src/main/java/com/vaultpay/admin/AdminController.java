package com.vaultpay.admin;

import com.vaultpay.admin.response.AdminDashboardResponse;
import com.vaultpay.admin.response.AdminUserResponse;
import com.vaultpay.transaction.response.TransactionResponse;
import com.vaultpay.userauthmgt.user.User;
import com.vaultpay.userauthmgt.user.UserStatus;
import com.vaultpay.utils.ApiResponse;
import com.vaultpay.wallet.WalletStatus;
import com.vaultpay.activitylog.ActivityLog;
import org.springframework.web.bind.annotation.PostMapping;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    @GetMapping("/private/dashboard")
    public ResponseEntity<ApiResponse<AdminDashboardResponse>> dashboard() {
        return ResponseEntity.ok(new ApiResponse<>(true, "Dashboard fetched successfully", adminService.dashboard()));
    }

    @GetMapping("/private/users")
    public ResponseEntity<ApiResponse<Page<AdminUserResponse>>> users(
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Users fetched successfully", adminService.users(page, size)));
    }

    @GetMapping("/private/transactions")
    public ResponseEntity<ApiResponse<Page<TransactionResponse>>> transactions(
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Transactions fetched successfully",
                adminService.transactions(page, size)));
    }

    @GetMapping("/private/transactions/{reference}")
    public ResponseEntity<ApiResponse<TransactionResponse>> transaction(@PathVariable String reference) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Transaction fetched successfully",
                adminService.transaction(reference)));
    }

    @GetMapping("/private/activity-logs")
    public ResponseEntity<ApiResponse<Page<ActivityLog>>> activityLogs(
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Activity logs fetched successfully",
                adminService.activityLogs(page, size)));
    }

    @PostMapping("/private/reconciliation/funding/{reference}")
    public ResponseEntity<ApiResponse<Void>> reconcileFunding(
            @AuthenticationPrincipal User admin, @PathVariable String reference
    ) {
        adminService.reconcileFunding(admin, reference);
        return ResponseEntity.ok(new ApiResponse<>(true, "Funding reconciliation completed", null));
    }

    @PostMapping("/private/reconciliation/withdrawal/{reference}")
    public ResponseEntity<ApiResponse<Void>> reconcileWithdrawal(
            @AuthenticationPrincipal User admin, @PathVariable String reference
    ) {
        adminService.reconcileWithdrawal(admin, reference);
        return ResponseEntity.ok(new ApiResponse<>(true, "Withdrawal reconciliation completed", null));
    }

    @PutMapping("/private/users/{userId}/status")
    public ResponseEntity<ApiResponse<AdminUserResponse>> updateUserStatus(
            @AuthenticationPrincipal User admin, @PathVariable UUID userId, @RequestParam UserStatus status
    ) {
        return ResponseEntity.ok(new ApiResponse<>(true, "User status updated successfully",
                adminService.updateUserStatus(admin, userId, status)));
    }

    @PutMapping("/private/wallets/{walletId}/status")
    public ResponseEntity<ApiResponse<Void>> updateWalletStatus(
            @AuthenticationPrincipal User admin, @PathVariable UUID walletId, @RequestParam WalletStatus status
    ) {
        adminService.updateWalletStatus(admin, walletId, status);
        return ResponseEntity.ok(new ApiResponse<>(true, "Wallet status updated successfully", null));
    }
}
