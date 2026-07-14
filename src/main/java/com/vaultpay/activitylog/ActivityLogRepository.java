package com.vaultpay.activitylog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ActivityLogRepository extends JpaRepository<ActivityLog, UUID> {
    Page<ActivityLog> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
