package com.vaultpay.userauthmgt.userotp;

import com.vaultpay.userauthmgt.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserOtpRepository extends JpaRepository<UserOtp, UUID> {

    Optional<UserOtp> findFirstByUserAndTypeAndUsedFalseOrderByCreatedAtDesc(User user, UserOtpType type);

    void deleteAllByUserAndType(User user, UserOtpType type);
}

