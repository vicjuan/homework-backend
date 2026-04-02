package com.example.demo.event;

import com.example.demo.dto.NotificationResponse;
import com.example.demo.entity.EventStatus;
import com.example.demo.entity.Notification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationEventListenerTest {

    @Mock RedisTemplate<String, Object> redisTemplate;
    @Mock ValueOperations<String, Object> valueOps;
    @Mock ListOperations<String, Object> listOps;

    @InjectMocks
    NotificationEventListener listener;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
        lenient().when(redisTemplate.opsForList()).thenReturn(listOps);
    }

    @Test
    void handleNotificationCreated_setsIndividualCacheKey() {
        NotificationCreatedEvent event = new NotificationCreatedEvent(buildNotification(1L));

        listener.handleNotificationCreated(event);

        verify(valueOps).set(eq("notifications:id:1"), any(NotificationResponse.class));
    }

    @Test
    void handleNotificationCreated_pushesIdToRecentList() {
        NotificationCreatedEvent event = new NotificationCreatedEvent(buildNotification(2L));

        listener.handleNotificationCreated(event);

        verify(listOps).leftPush("notifications:recent", 2L);
    }

    @Test
    void handleNotificationCreated_trimsRecentListTo10() {
        NotificationCreatedEvent event = new NotificationCreatedEvent(buildNotification(3L));

        listener.handleNotificationCreated(event);

        verify(listOps).trim("notifications:recent", 0, 9);
    }

    @Test
    void handleNotificationCreated_swallowsRedisExceptionGracefully() {
        when(redisTemplate.opsForValue()).thenThrow(new RuntimeException("Redis connection failed"));

        NotificationCreatedEvent event = new NotificationCreatedEvent(buildNotification(4L));

        // Should not throw
        listener.handleNotificationCreated(event);
    }

    @Test
    void handleNotificationCreated_allThreeRedisOpsCalledInOrder() {
        NotificationCreatedEvent event = new NotificationCreatedEvent(buildNotification(5L));

        listener.handleNotificationCreated(event);

        var inOrder = inOrder(valueOps, listOps);
        inOrder.verify(valueOps).set(eq("notifications:id:5"), any());
        inOrder.verify(listOps).leftPush("notifications:recent", 5L);
        inOrder.verify(listOps).trim("notifications:recent", 0, 9);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Notification buildNotification(Long id) {
        Notification n = new Notification();
        n.setId(id);
        n.setType("email");
        n.setRecipient("user@example.com");
        n.setSubject("Subject");
        n.setContent("Content");
        n.setEventStatus(EventStatus.PENDING);
        n.setCreatedAt(LocalDateTime.now());
        return n;
    }
}
