package com.vaultpay.activitylog;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ActivityLogService {

    private final ActivityLogRepository activityLogRepository;
    private final ObjectProvider<HttpServletRequest> requestProvider;

    public void log(UUID userId, ActivityAction action, String module, String description) {
        activityLogRepository.save(ActivityLog.builder()
                .userId(userId).action(action).module(module).description(description)
                .ipAddress(resolveIp()).build());
    }

    private String resolveIp() {
        try {
            HttpServletRequest request = requestProvider.getIfAvailable();
            if (request == null) {
                return null;
            }
            String forwarded = request.getHeader("X-Forwarded-For");
            return forwarded == null || forwarded.isBlank()
                    ? request.getRemoteAddr()
                    : forwarded.split(",")[0].trim();
        } catch (IllegalStateException exception) {
            return null;
        }
    }

    public Page<ActivityLog> getAll(int page, int size) {
        return activityLogRepository.findAllByOrderByCreatedAtDesc(
                PageRequest.of(Math.max(page - 1, 0), Math.min(Math.max(size, 1), 100))
        );
    }
}
