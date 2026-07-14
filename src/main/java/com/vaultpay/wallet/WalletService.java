package com.vaultpay.wallet;

import com.vaultpay.userauthmgt.user.User;
import com.vaultpay.utils.exception.InvalidInputException;
import com.vaultpay.utils.exception.InvalidOperationException;
import com.vaultpay.utils.exception.ResourceNotFoundException;
import com.vaultpay.wallet.request.WalletPinChangeRequest;
import com.vaultpay.wallet.request.WalletPinSetupRequest;
import com.vaultpay.wallet.response.WalletBalanceResponse;
import com.vaultpay.wallet.response.WalletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.Comparator;

@Service
@RequiredArgsConstructor
public class WalletService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int WALLET_NUMBER_ATTEMPTS = 20;

    private final WalletRepository walletRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public WalletResponse createWalletForUser(User user) {
        return walletRepository.findByUser(user)
                .map(this::mapToResponse)
                .orElseGet(() -> createNewWallet(user));
    }

    @Transactional(readOnly = true)
    public WalletResponse getWallet(User user) {
        return mapToResponse(findByUser(user));
    }

    @Transactional
    public WalletResponse setupPin(User user, WalletPinSetupRequest request) {
        Wallet wallet = findByUser(user);
        if (wallet.isPinSet()) {
            throw new InvalidOperationException("Wallet PIN has already been set");
        }
        if (!request.pin().equals(request.confirmPin())) {
            throw new InvalidInputException("PIN confirmation does not match");
        }
        rejectWeakPin(request.pin());

        wallet.setPinHash(passwordEncoder.encode(request.pin()));
        wallet.setPinSet(true);
        return mapToResponse(walletRepository.save(wallet));
    }

    @Transactional
    public WalletResponse changePin(User user, WalletPinChangeRequest request) {
        Wallet wallet = findByUser(user);
        if (!wallet.isPinSet() || !passwordEncoder.matches(request.currentPin(), wallet.getPinHash())) {
            throw new InvalidInputException("Current PIN is incorrect");
        }
        if (!request.newPin().equals(request.confirmNewPin())) {
            throw new InvalidInputException("New PIN confirmation does not match");
        }
        if (request.currentPin().equals(request.newPin())) {
            throw new InvalidInputException("New PIN must be different from the current PIN");
        }
        rejectWeakPin(request.newPin());

        wallet.setPinHash(passwordEncoder.encode(request.newPin()));
        return mapToResponse(walletRepository.save(wallet));
    }

    public void verifyTransactionPin(Wallet wallet, String pin) {
        if (!wallet.isPinSet() || pin == null || !passwordEncoder.matches(pin, wallet.getPinHash())) {
            throw new InvalidInputException("Transaction PIN is incorrect");
        }
    }

    private WalletResponse createNewWallet(User user) {
        Wallet wallet = Wallet.builder()
                .user(user)
                .walletNumber(generateUniqueWalletNumber())
                .status(WalletStatus.ACTIVE)
                .pinSet(false)
                .build();

        wallet.addBalance(WalletBalance.builder()
                .currency(Currency.NGN)
                .availableBalance(BigDecimal.ZERO)
                .heldBalance(BigDecimal.ZERO)
                .build());

        return mapToResponse(walletRepository.save(wallet));
    }

    public Wallet getWalletEntity(User user) {
        return findByUser(user);
    }

    public Wallet findByWalletNumber(String walletNumber) {
        return walletRepository.findByWalletNumber(walletNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Destination wallet not found"));
    }

    private Wallet findByUser(User user) {
        return walletRepository.findByUser(user)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet not found"));
    }

    private String generateUniqueWalletNumber() {
        for (int attempt = 0; attempt < WALLET_NUMBER_ATTEMPTS; attempt++) {
            String candidate = String.valueOf(1_000_000_000L + SECURE_RANDOM.nextLong(9_000_000_000L));
            if (!walletRepository.existsByWalletNumber(candidate)) {
                return candidate;
            }
        }
        throw new InvalidOperationException("Unable to generate a wallet number. Please try again");
    }

    private void rejectWeakPin(String pin) {
        if (pin.chars().distinct().count() == 1 || "0123456789".contains(pin) || "9876543210".contains(pin)) {
            throw new InvalidInputException("Choose a less predictable PIN");
        }
    }

    private WalletResponse mapToResponse(Wallet wallet) {
        var balances = wallet.getBalances().stream()
                .sorted(Comparator.comparing(balance -> balance.getCurrency().name()))
                .map(balance -> new WalletBalanceResponse(
                        balance.getCurrency(),
                        balance.getAvailableBalance(),
                        balance.getHeldBalance(),
                        balance.getAvailableBalance().add(balance.getHeldBalance())
                ))
                .toList();

        return new WalletResponse(
                wallet.getId(),
                wallet.getWalletNumber(),
                wallet.getStatus(),
                wallet.isPinSet(),
                balances,
                wallet.getCreatedAt()
        );
    }
}
