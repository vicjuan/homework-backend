package com.example.demo.poller;

import com.example.demo.entity.EventStatus;
import com.example.demo.entity.Notification;
import com.example.demo.repository.NotificationRepository;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxPollerTest {

    @Mock NotificationRepository repository;
    @Mock RocketMQTemplate rocketMQTemplate;

    @InjectMocks
    OutboxPoller poller;

    // ── empty list ────────────────────────────────────────────────────────────

    @Test
    void poll_doesNothingWhenNoPendingNotifications() {
        when(repository.transitionEventStatus(EventStatus.PENDING, EventStatus.PROCESSING)).thenReturn(0);

        poller.poll();

        verify(rocketMQTemplate, never()).syncSend(anyString(), (Object) any());
        verify(repository, never()).save(any());
    }

    // ── happy path ────────────────────────────────────────────────────────────

    @Test
    void poll_sendsMessageAndMarksSent() {
        Notification processing = buildNotification(1L);
        when(repository.transitionEventStatus(EventStatus.PENDING, EventStatus.PROCESSING)).thenReturn(1);
        when(repository.findByEventStatus(EventStatus.PROCESSING)).thenReturn(List.of(processing));

        poller.poll();

        verify(rocketMQTemplate).syncSend(eq("notification-topic"), (Object) any(NotificationMessage.class));

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getEventStatus()).isEqualTo(EventStatus.SENT);
    }

    @Test
    void poll_sendsCorrectMessagePayload() {
        Notification processing = buildNotification(1L);
        when(repository.transitionEventStatus(EventStatus.PENDING, EventStatus.PROCESSING)).thenReturn(1);
        when(repository.findByEventStatus(EventStatus.PROCESSING)).thenReturn(List.of(processing));

        poller.poll();

        ArgumentCaptor<NotificationMessage> messageCaptor =
                ArgumentCaptor.forClass(NotificationMessage.class);
        verify(rocketMQTemplate).syncSend(eq("notification-topic"), messageCaptor.capture());

        NotificationMessage sent = messageCaptor.getValue();
        assertThat(sent.getId()).isEqualTo(1L);
        assertThat(sent.getType()).isEqualTo("email");
        assertThat(sent.getRecipient()).isEqualTo("user@example.com");
        assertThat(sent.getSubject()).isEqualTo("Subject");
        assertThat(sent.getContent()).isEqualTo("Content");
    }

    @Test
    void poll_processesMultipleNotifications() {
        Notification n1 = buildNotification(1L);
        Notification n2 = buildNotification(2L);
        Notification n3 = buildNotification(3L);
        when(repository.transitionEventStatus(EventStatus.PENDING, EventStatus.PROCESSING)).thenReturn(3);
        when(repository.findByEventStatus(EventStatus.PROCESSING)).thenReturn(List.of(n1, n2, n3));

        poller.poll();

        verify(rocketMQTemplate, times(3)).syncSend(eq("notification-topic"), (Object) any());
        verify(repository, times(3)).save(any());
    }

    // ── failure handling ──────────────────────────────────────────────────────

    @Test
    void poll_revertsToWhenRocketMQFails() {
        Notification processing = buildNotification(1L);
        when(repository.transitionEventStatus(EventStatus.PENDING, EventStatus.PROCESSING)).thenReturn(1);
        when(repository.findByEventStatus(EventStatus.PROCESSING)).thenReturn(List.of(processing));
        doThrow(new RuntimeException("RocketMQ unavailable"))
                .when(rocketMQTemplate).syncSend(anyString(), (Object) any());

        poller.poll();

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getEventStatus()).isEqualTo(EventStatus.PENDING);
    }

    @Test
    void poll_continuesProcessingRemainingAfterOneFailure() {
        Notification failing = buildNotification(1L);
        Notification succeeding = buildNotification(2L);
        when(repository.transitionEventStatus(EventStatus.PENDING, EventStatus.PROCESSING)).thenReturn(2);
        when(repository.findByEventStatus(EventStatus.PROCESSING)).thenReturn(List.of(failing, succeeding));

        doThrow(new RuntimeException("RocketMQ error"))
                .when(rocketMQTemplate).syncSend(eq("notification-topic"), (Object) argThat(
                        msg -> msg instanceof NotificationMessage && ((NotificationMessage) msg).getId().equals(1L)
                ));

        poller.poll();

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(repository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues().get(0).getEventStatus()).isEqualTo(EventStatus.PENDING);
        assertThat(captor.getAllValues().get(1).getEventStatus()).isEqualTo(EventStatus.SENT);
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
