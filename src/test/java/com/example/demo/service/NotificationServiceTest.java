package com.example.demo.service;

import com.example.demo.entity.EventStatus;
import com.example.demo.dto.CreateNotificationRequest;
import com.example.demo.dto.NotificationResponse;
import com.example.demo.dto.UpdateNotificationRequest;
import com.example.demo.entity.Notification;
import com.example.demo.event.NotificationCreatedEvent;
import com.example.demo.exception.NotFoundException;
import com.example.demo.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock private NotificationRepository repository;
    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private ValueOperations<String, Object> valueOps;
    @Mock private ListOperations<String, Object> listOps;

    @InjectMocks
    private NotificationService service;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
        lenient().when(redisTemplate.opsForList()).thenReturn(listOps);
    }

    // ── createNotification ────────────────────────────────────────────────────

    @Test
    void createNotification_savesEntityAndPublishesEvent() {
        CreateNotificationRequest request = new CreateNotificationRequest();
        request.setType("email");
        request.setRecipient("user@example.com");
        request.setSubject("Hello");
        request.setContent("World");

        Notification saved = buildNotification(1L, "email", "user@example.com");
        when(repository.save(any())).thenReturn(saved);

        NotificationResponse response = service.createNotification(request);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getType()).isEqualTo("email");

        ArgumentCaptor<NotificationCreatedEvent> eventCaptor =
                ArgumentCaptor.forClass(NotificationCreatedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getNotification().getId()).isEqualTo(1L);
    }

    @Test
    void createNotification_setsEventStatusToPending() {
        CreateNotificationRequest request = new CreateNotificationRequest();
        request.setType("sms");
        request.setRecipient("+886900000000");

        Notification saved = buildNotification(2L, "sms", "+886900000000");
        when(repository.save(any())).thenReturn(saved);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        service.createNotification(request);

        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getEventStatus()).isEqualTo(EventStatus.PENDING);
    }

    // ── getNotificationById ───────────────────────────────────────────────────

    @Test
    void getNotificationById_returnsCachedValueWhenPresent() {
        NotificationResponse cached = new NotificationResponse();
        cached.setId(1L);
        when(valueOps.get("notifications:id:1")).thenReturn(cached);

        NotificationResponse result = service.getNotificationById(1L);

        assertThat(result.getId()).isEqualTo(1L);
        verify(repository, never()).findById(any());
    }

    @Test
    void getNotificationById_fetchesFromDbOnCacheMiss() {
        when(valueOps.get("notifications:id:1")).thenReturn(null);
        Notification notification = buildNotification(1L, "email", "user@example.com");
        when(repository.findById(1L)).thenReturn(Optional.of(notification));

        NotificationResponse result = service.getNotificationById(1L);

        assertThat(result.getId()).isEqualTo(1L);
        verify(valueOps).set(eq("notifications:id:1"), any(NotificationResponse.class));
    }

    @Test
    void getNotificationById_throws404WhenNotFound() {
        when(valueOps.get(any())).thenReturn(null);
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getNotificationById(99L))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("99");
    }

    // ── getRecentNotifications ────────────────────────────────────────────────

    @Test
    void getRecentNotifications_returnsListFromRedis() {
        NotificationResponse r1 = new NotificationResponse();
        r1.setId(1L);
        NotificationResponse r2 = new NotificationResponse();
        r2.setId(2L);

        when(listOps.range("notifications:recent", 0, 9)).thenReturn(List.of(1L, 2L));
        when(valueOps.multiGet(List.of("notifications:id:1", "notifications:id:2")))
                .thenReturn(List.of(r1, r2));

        List<NotificationResponse> result = service.getRecentNotifications();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getId()).isEqualTo(1L);
        assertThat(result.get(1).getId()).isEqualTo(2L);
    }

    @Test
    void getRecentNotifications_fetchesFromDbOnCacheMiss() {
        Notification n = buildNotification(1L, "email", "user@example.com");

        when(listOps.range("notifications:recent", 0, 9)).thenReturn(List.of(1L));
        when(valueOps.multiGet(List.of("notifications:id:1"))).thenReturn(Collections.singletonList(null));
        when(repository.findById(1L)).thenReturn(Optional.of(n));

        List<NotificationResponse> result = service.getRecentNotifications();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(1L);
    }

    @Test
    void getRecentNotifications_filtersOutDeletedNotifications() {
        NotificationResponse r1 = new NotificationResponse();
        r1.setId(1L);

        when(listOps.range("notifications:recent", 0, 9)).thenReturn(List.of(1L, 99L));
        when(valueOps.multiGet(List.of("notifications:id:1", "notifications:id:99")))
                .thenReturn(Arrays.asList(r1, null));
        when(repository.findById(99L)).thenReturn(Optional.empty());

        List<NotificationResponse> result = service.getRecentNotifications();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(1L);
    }

    @Test
    void getRecentNotifications_returnsEmptyListWhenCacheMiss() {
        when(listOps.range("notifications:recent", 0, 9)).thenReturn(List.of());

        List<NotificationResponse> result = service.getRecentNotifications();

        assertThat(result).isEmpty();
    }

    // ── updateNotification ────────────────────────────────────────────────────

    @Test
    void updateNotification_updatesFieldsAndInvalidatesCache() {
        Notification existing = buildNotification(1L, "email", "user@example.com");
        when(repository.findById(1L)).thenReturn(Optional.of(existing));

        UpdateNotificationRequest request = new UpdateNotificationRequest();
        request.setSubject("New Subject");
        request.setContent("New Content");

        NotificationResponse result = service.updateNotification(1L, request);

        assertThat(result).isNotNull();
        verify(redisTemplate).delete("notifications:id:1");
    }

    @Test
    void updateNotification_throws404WhenNotFound() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        UpdateNotificationRequest request = new UpdateNotificationRequest();
        assertThatThrownBy(() -> service.updateNotification(99L, request))
                .isInstanceOf(NotFoundException.class);
    }

    // ── deleteNotification ────────────────────────────────────────────────────

    @Test
    void deleteNotification_deletesFromDbAndInvalidatesCache() {
        Notification existing = buildNotification(1L, "email", "user@example.com");
        when(repository.findById(1L)).thenReturn(Optional.of(existing));

        service.deleteNotification(1L);

        verify(repository).delete(existing);
        verify(redisTemplate).delete("notifications:id:1");
    }

    @Test
    void deleteNotification_throws404WhenNotFound() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteNotification(99L))
                .isInstanceOf(NotFoundException.class);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Notification buildNotification(Long id, String type, String recipient) {
        Notification n = new Notification();
        n.setId(id);
        n.setType(type);
        n.setRecipient(recipient);
        n.setSubject("Subject");
        n.setContent("Content");
        n.setEventStatus(EventStatus.PENDING);
        n.setCreatedAt(LocalDateTime.now());
        return n;
    }
}
