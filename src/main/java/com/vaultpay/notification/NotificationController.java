package com.vaultpay.notification;

import com.vaultpay.notification.response.NotificationResponse;
import com.vaultpay.userauthmgt.user.User;
import com.vaultpay.utils.ApiResponse;
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
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping("/private")
    public ResponseEntity<ApiResponse<Page<NotificationResponse>>> getAll(
            @AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Notifications fetched successfully",
                notificationService.getAll(user, page, size)));
    }

    @GetMapping("/private/unread-count")
    public ResponseEntity<ApiResponse<Long>> unreadCount(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Unread count fetched successfully",
                notificationService.unreadCount(user)));
    }

    @PutMapping("/private/{id}/read")
    public ResponseEntity<ApiResponse<NotificationResponse>> markRead(
            @AuthenticationPrincipal User user, @PathVariable UUID id
    ) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Notification marked as read",
                notificationService.markRead(user, id)));
    }
}

