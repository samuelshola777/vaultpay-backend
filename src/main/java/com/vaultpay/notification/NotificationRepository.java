package com.vaultpay.notification;

import com.vaultpay.userauthmgt.user.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Page<Notification> findAllByUser(User user, Pageable pageable);

    Optional<Notification> findByIdAndUser(UUID id, User user);

    long countByUserAndReadAtIsNull(User user);
}

