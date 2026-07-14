package com.vaultpay.admin;

import com.vaultpay.activitylog.ActivityAction;
import com.vaultpay.activitylog.ActivityLogService;
import com.vaultpay.admin.response.AdminDashboardResponse;
import com.vaultpay.admin.response.AdminUserResponse;
import com.vaultpay.notification.NotificationService;
import com.vaultpay.notification.NotificationType;
import com.vaultpay.transaction.TransactionService;
import com.vaultpay.transaction.TransactionStatus;
import com.vaultpay.transaction.WalletTransactionRepository;
import com.vaultpay.transaction.response.TransactionResponse;
import com.vaultpay.userauthmgt.user.User;
import com.vaultpay.userauthmgt.user.UserRepository;
import com.vaultpay.userauthmgt.user.UserStatus;
import com.vaultpay.utils.exception.InvalidOperationException;
import com.vaultpay.utils.exception.ResourceNotFoundException;
import com.vaultpay.wallet.Wallet;
import com.vaultpay.wallet.WalletRepository;
import com.vaultpay.wallet.WalletStatus;
import com.vaultpay.payment.PaymentService;
import com.vaultpay.withdrawal.WithdrawalService;
import com.vaultpay.activitylog.ActivityLog;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final WalletTransactionRepository transactionRepository;
    private final TransactionService transactionService;
    private final NotificationService notificationService;
    private final ActivityLogService activityLogService;
    private final PaymentService paymentService;
    private final WithdrawalService withdrawalService;

    @Transactional(readOnly = true)
    public AdminDashboardResponse dashboard() {
        return new AdminDashboardResponse(
                userRepository.count(), userRepository.countByStatus(UserStatus.ACTIVE),
                walletRepository.count(), walletRepository.countByStatus(WalletStatus.FROZEN),
                transactionRepository.count(), transactionRepository.countByStatus(TransactionStatus.COMPLETED),
                transactionRepository.countByStatusIn(java.util.List.of(TransactionStatus.PENDING, TransactionStatus.PROCESSING))
        );
    }

    @Transactional(readOnly = true)
    public Page<AdminUserResponse> users(int page, int size) {
        return userRepository.findAll(PageRequest.of(Math.max(page - 1, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt"))).map(this::mapUser);
    }

    @Transactional(readOnly = true)
    public Page<TransactionResponse> transactions(int page, int size) {
        return transactionRepository.findAll(PageRequest.of(Math.max(page - 1, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt"))).map(transactionService::mapToResponse);
    }

    @Transactional(readOnly = true)
    public TransactionResponse transaction(String reference) {
        return transactionService.getByReferenceForAdmin(reference);
    }

    @Transactional(readOnly = true)
    public Page<ActivityLog> activityLogs(int page, int size) {
        return activityLogService.getAll(page, size);
    }

    public void reconcileFunding(User admin, String reference) {
        paymentService.verifyFunding(reference);
        activityLogService.log(admin.getId(), ActivityAction.STATUS_CHANGED, "ADMIN",
                "Triggered funding reconciliation: " + reference);
    }

    public void reconcileWithdrawal(User admin, String reference) {
        withdrawalService.reconcile(reference);
        activityLogService.log(admin.getId(), ActivityAction.STATUS_CHANGED, "ADMIN",
                "Triggered withdrawal reconciliation: " + reference);
    }

    @Transactional
    public AdminUserResponse updateUserStatus(User admin, UUID userId, UserStatus status) {
        User user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (user.getId().equals(admin.getId())) {
            throw new InvalidOperationException("You cannot change your own account status");
        }
        if (status == UserStatus.ACTIVE && !user.isEmailVerified()) {
            throw new InvalidOperationException("An unverified account cannot be activated");
        }
        user.setStatus(status);
        userRepository.save(user);
        notificationService.create(user, NotificationType.ACCOUNT, "Account status updated",
                "Your account status is now " + status.name() + ".");
        activityLogService.log(admin.getId(), ActivityAction.STATUS_CHANGED, "ADMIN",
                "Changed user " + userId + " status to " + status);
        return mapUser(user);
    }

    @Transactional
    public void updateWalletStatus(User admin, UUID walletId, WalletStatus status) {
        Wallet wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet not found"));
        wallet.setStatus(status);
        walletRepository.save(wallet);
        notificationService.create(wallet.getUser(), NotificationType.ACCOUNT, "Wallet status updated",
                "Your wallet status is now " + status.name() + ".");
        activityLogService.log(admin.getId(), ActivityAction.STATUS_CHANGED, "ADMIN",
                "Changed wallet " + walletId + " status to " + status);
    }

    private AdminUserResponse mapUser(User user) {
        return new AdminUserResponse(user.getId(), user.getFirstName(), user.getLastName(), user.getEmail(),
                user.getPhoneNumber(), user.getRole(), user.getStatus(), user.isEmailVerified(), user.getCreatedAt());
    }
}
