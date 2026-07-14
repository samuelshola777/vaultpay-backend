package com.vaultpay.admin;

import com.vaultpay.userauthmgt.user.User;
import com.vaultpay.userauthmgt.user.UserRepository;
import com.vaultpay.userauthmgt.user.UserRole;
import com.vaultpay.userauthmgt.user.UserStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DefaultAdminSeeder implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.email:}")
    private String email;

    @Value("${app.admin.password:}")
    private String password;

    @Override
    public void run(ApplicationArguments args) {
        if (email == null || email.isBlank() || password == null || password.isBlank()
                || userRepository.existsByEmailIgnoreCase(email)) {
            return;
        }
        userRepository.save(User.builder()
                .firstName("VaultPay").lastName("Administrator").email(email.trim().toLowerCase())
                .password(passwordEncoder.encode(password)).role(UserRole.SUPER_ADMIN)
                .status(UserStatus.ACTIVE).emailVerified(true).build());
    }
}

