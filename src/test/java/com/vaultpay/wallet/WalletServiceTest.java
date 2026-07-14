package com.vaultpay.wallet;

import com.vaultpay.userauthmgt.user.User;
import com.vaultpay.wallet.request.WalletPinSetupRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WalletServiceTest {

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private WalletService walletService;
    private User user;

    @BeforeEach
    void setUp() {
        walletService = new WalletService(walletRepository, passwordEncoder);
        user = User.builder().email("samuel@example.com").firstName("Samuel").lastName("Shola").build();
        user.setId(UUID.randomUUID());
    }

    @Test
    void createsAnNgnWalletForANewUser() {
        when(walletRepository.findByUser(user)).thenReturn(Optional.empty());
        when(walletRepository.existsByWalletNumber(any())).thenReturn(false);
        when(walletRepository.save(any(Wallet.class))).thenAnswer(invocation -> {
            Wallet wallet = invocation.getArgument(0);
            wallet.setId(UUID.randomUUID());
            return wallet;
        });

        var response = walletService.createWalletForUser(user);

        assertThat(response.walletNumber()).hasSize(10);
        assertThat(response.status()).isEqualTo(WalletStatus.ACTIVE);
        assertThat(response.balances()).hasSize(1);
        assertThat(response.balances().getFirst().currency()).isEqualTo(Currency.NGN);
        assertThat(response.balances().getFirst().availableBalance()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void securelySetsTheWalletPin() {
        Wallet wallet = Wallet.builder()
                .user(user).walletNumber("1234567890").status(WalletStatus.ACTIVE)
                .pinSet(false).balances(new ArrayList<>()).build();
        wallet.setId(UUID.randomUUID());
        when(walletRepository.findByUser(user)).thenReturn(Optional.of(wallet));
        when(passwordEncoder.encode("2580")).thenReturn("hashed-pin");
        when(walletRepository.save(wallet)).thenReturn(wallet);

        var response = walletService.setupPin(user, new WalletPinSetupRequest("2580", "2580"));

        assertThat(response.pinSet()).isTrue();
        assertThat(wallet.getPinHash()).isEqualTo("hashed-pin");
    }
}
