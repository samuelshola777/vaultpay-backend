package com.vaultpay.notification;

import com.vaultpay.notification.response.NotificationResponse;
import com.vaultpay.userauthmgt.user.User;
import com.vaultpay.utils.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public void create(User user, NotificationType type, String title, String message) {
        notificationRepository.save(Notification.builder()
                .user(user).type(type).title(title).message(message).build());
    }

    @Transactional(readOnly = true)
    public Page<NotificationResponse> getAll(User user, int page, int size) {
        return notificationRepository.findAllByUser(user, PageRequest.of(
                Math.max(page - 1, 0), Math.min(Math.max(size, 1), 100), Sort.by(Sort.Direction.DESC, "createdAt")
        )).map(this::map);
    }

    @Transactional
    public NotificationResponse markRead(User user, UUID id) {
        Notification notification = notificationRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found"));
        if (notification.getReadAt() == null) {
            notification.setReadAt(LocalDateTime.now());
            notificationRepository.save(notification);
        }
        return map(notification);
    }

    public long unreadCount(User user) {
        return notificationRepository.countByUserAndReadAtIsNull(user);
    }

    private NotificationResponse map(Notification value) {
        return new NotificationResponse(value.getId(), value.getType(), value.getTitle(), value.getMessage(),
                value.getReadAt() != null, value.getCreatedAt());
    }
}

