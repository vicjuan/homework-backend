package com.example.demo.event;

import com.example.demo.config.RedisKeys;
import com.example.demo.dto.NotificationResponse;
import com.example.demo.entity.Notification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private static final long MAX_RECENT = 10;

    private final RedisTemplate<String, Object> redisTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleNotificationCreated(NotificationCreatedEvent event) {
        Notification notification = event.getNotification();
        NotificationResponse response = NotificationResponse.from(notification);
        String idKey = RedisKeys.NOTIFICATION_ID_PREFIX + notification.getId();

        try {
            redisTemplate.opsForValue().set(idKey, response);
            redisTemplate.opsForList().leftPush(RedisKeys.NOTIFICATION_RECENT, notification.getId());
            redisTemplate.opsForList().trim(RedisKeys.NOTIFICATION_RECENT, 0, MAX_RECENT - 1);
        } catch (Exception e) {
            log.warn("Failed to write to Redis for notification id={}: {}", notification.getId(), e.getMessage());
        }
    }
}
