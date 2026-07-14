package com.vaultpay.beneficiary;

import com.vaultpay.userauthmgt.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BeneficiaryRepository extends JpaRepository<Beneficiary, UUID> {

    List<Beneficiary> findAllByUserAndActiveTrueOrderByCreatedAtDesc(User user);

    Optional<Beneficiary> findByIdAndUserAndActiveTrue(UUID id, User user);

    Optional<Beneficiary> findByUserAndAccountNumberAndBankCode(
            User user, String accountNumber, String bankCode
    );

    boolean existsByUserAndAccountNumberAndBankCodeAndActiveTrue(User user, String accountNumber, String bankCode);
}
