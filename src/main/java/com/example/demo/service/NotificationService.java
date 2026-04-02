package com.example.demo.service;

import com.example.demo.dto.CreateNotificationRequest;
import com.example.demo.dto.NotificationResponse;
import com.example.demo.dto.UpdateNotificationRequest;
import com.example.demo.entity.EventStatus;
import com.example.demo.entity.Notification;
import com.example.demo.event.NotificationCreatedEvent;
import com.example.demo.exception.NotFoundException;
import com.example.demo.config.RedisKeys;
import com.example.demo.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository repository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public NotificationResponse createNotification(CreateNotificationRequest request) {
        Notification notification = Notification.builder()
                .type(request.getType())
                .recipient(request.getRecipient())
                .subject(request.getSubject())
                .content(request.getContent())
                .eventStatus(EventStatus.PENDING)
                .build();

        Notification saved = repository.save(notification);

        eventPublisher.publishEvent(new NotificationCreatedEvent(saved));

        return NotificationResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public NotificationResponse getNotificationById(Long id) {
        String idKey = RedisKeys.NOTIFICATION_ID_PREFIX + id;

        Object cached = redisTemplate.opsForValue().get(idKey);
        if (cached instanceof NotificationResponse response) {
            return response;
        }

        Notification notification = repository.findById(id)
                .orElseThrow(() -> new NotFoundException(id));

        NotificationResponse response = NotificationResponse.from(notification);

        try {
            redisTemplate.opsForValue().set(idKey, response);
        } catch (Exception e) {
            log.warn("Failed to repopulate Redis cache for id={}: {}", id, e.getMessage());
        }

        return response;
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> getRecentNotifications() {
        List<Object> rawIds = redisTemplate.opsForList().range(RedisKeys.NOTIFICATION_RECENT, 0, 9);
        if (rawIds == null || rawIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> ids = rawIds.stream()
                .map(obj -> ((Number) obj).longValue())
                .toList();

        List<String> keys = ids.stream()
                .map(id -> RedisKeys.NOTIFICATION_ID_PREFIX + id)
                .toList();

        List<Object> values = redisTemplate.opsForValue().multiGet(keys);

        List<NotificationResponse> result = new ArrayList<>();
        for (int i = 0; i < ids.size(); i++) {
            Object value = values != null ? values.get(i) : null;
            if (value instanceof NotificationResponse response) {
                result.add(response);
            } else {
                repository.findById(ids.get(i))
                        .map(NotificationResponse::from)
                        .ifPresent(result::add);
            }
        }
        return result;
    }

    @Transactional
    public NotificationResponse updateNotification(Long id, UpdateNotificationRequest request) {
        Notification notification = repository.findById(id)
                .orElseThrow(() -> new NotFoundException(id));

        notification.setSubject(request.getSubject());
        notification.setContent(request.getContent());

        try {
            redisTemplate.delete(RedisKeys.NOTIFICATION_ID_PREFIX + id);
        } catch (Exception e) {
            log.warn("Failed to invalidate Redis cache for id={}: {}", id, e.getMessage());
        }

        return NotificationResponse.from(notification);
    }

    @Transactional
    public void deleteNotification(Long id) {
        Notification notification = repository.findById(id)
                .orElseThrow(() -> new NotFoundException(id));

        repository.delete(notification);

        try {
            redisTemplate.delete(RedisKeys.NOTIFICATION_ID_PREFIX + id);
        } catch (Exception e) {
            log.warn("Failed to delete Redis cache for id={}: {}", id, e.getMessage());
        }
    }
}
