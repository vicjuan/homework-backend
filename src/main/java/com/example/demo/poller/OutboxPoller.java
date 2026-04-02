package com.example.demo.poller;

import com.example.demo.entity.EventStatus;
import com.example.demo.entity.Notification;
import com.example.demo.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPoller {

    private static final String TOPIC = "notification-topic";

    private final NotificationRepository repository;
    private final RocketMQTemplate rocketMQTemplate;

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void poll() {
        int count = repository.transitionEventStatus(EventStatus.PENDING, EventStatus.PROCESSING);
        if (count == 0) {
            return;
        }

        log.debug("OutboxPoller: claimed {} notification(s) for processing", count);

        repository.findByEventStatus(EventStatus.PROCESSING).forEach(this::publish);
    }

    private void publish(Notification notification) {
        try {
            NotificationMessage message = NotificationMessage.builder()
                    .id(notification.getId())
                    .type(notification.getType())
                    .recipient(notification.getRecipient())
                    .subject(notification.getSubject())
                    .content(notification.getContent())
                    .createdAt(notification.getCreatedAt())
                    .build();

            rocketMQTemplate.syncSend(TOPIC, message);

            notification.setEventStatus(EventStatus.SENT);
            repository.save(notification);

            log.info("OutboxPoller: sent notification id={} to topic={}", notification.getId(), TOPIC);
        } catch (Exception e) {
            log.error("OutboxPoller: failed to send notification id={}: {}", notification.getId(), e.getMessage());
            notification.setEventStatus(EventStatus.PENDING);
            repository.save(notification);
        }
    }
}
