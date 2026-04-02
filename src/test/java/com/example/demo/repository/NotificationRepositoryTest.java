package com.example.demo.repository;

import com.example.demo.entity.EventStatus;
import com.example.demo.entity.Notification;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class NotificationRepositoryTest {

    @Autowired TestEntityManager em;
    @Autowired NotificationRepository repository;

    @Test
    void findByEventStatus_returnsPendingRecords() {
        persist(buildNotification("email", "a@example.com", EventStatus.PENDING));
        persist(buildNotification("sms", "+886900000000", EventStatus.PENDING));
        persist(buildNotification("email", "b@example.com", EventStatus.SENT));
        em.flush();

        List<Notification> result = repository.findByEventStatus(EventStatus.PENDING);

        assertThat(result).hasSize(2);
        assertThat(result).allMatch(n -> EventStatus.PENDING.equals(n.getEventStatus()));
    }

    @Test
    void findByEventStatus_returnsSentRecords() {
        persist(buildNotification("email", "a@example.com", EventStatus.PENDING));
        persist(buildNotification("email", "b@example.com", EventStatus.SENT));
        persist(buildNotification("sms", "+886900000001", EventStatus.SENT));
        em.flush();

        List<Notification> result = repository.findByEventStatus(EventStatus.SENT);

        assertThat(result).hasSize(2);
        assertThat(result).allMatch(n -> EventStatus.SENT.equals(n.getEventStatus()));
    }

    @Test
    void findByEventStatus_returnsEmptyListWhenNoMatches() {
        persist(buildNotification("email", "a@example.com", EventStatus.SENT));
        em.flush();

        List<Notification> result = repository.findByEventStatus(EventStatus.PENDING);

        assertThat(result).isEmpty();
    }

    @Test
    void findByEventStatus_returnsEmptyListWhenTableIsEmpty() {
        List<Notification> result = repository.findByEventStatus(EventStatus.PENDING);

        assertThat(result).isEmpty();
    }

    @Test
    void save_persistsAllFields() {
        Notification n = buildNotification("email", "user@example.com", EventStatus.PENDING);
        n.setSubject("Hello");
        n.setContent("World");

        Notification saved = repository.save(n);
        em.flush();
        em.clear();

        Notification found = repository.findById(saved.getId()).orElseThrow();
        assertThat(found.getType()).isEqualTo("email");
        assertThat(found.getRecipient()).isEqualTo("user@example.com");
        assertThat(found.getSubject()).isEqualTo("Hello");
        assertThat(found.getContent()).isEqualTo("World");
        assertThat(found.getEventStatus()).isEqualTo(EventStatus.PENDING);
        assertThat(found.getCreatedAt()).isNotNull();
    }

    @Test
    void save_defaultEventStatusIsPending() {
        Notification n = new Notification();
        n.setType("sms");
        n.setRecipient("+886900000000");

        Notification saved = repository.save(n);
        em.flush();
        em.clear();

        Notification found = repository.findById(saved.getId()).orElseThrow();
        assertThat(found.getEventStatus()).isEqualTo(EventStatus.PENDING);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Notification persist(Notification n) {
        return em.persist(n);
    }

    private Notification buildNotification(String type, String recipient, EventStatus eventStatus) {
        Notification n = new Notification();
        n.setType(type);
        n.setRecipient(recipient);
        n.setEventStatus(eventStatus);
        return n;
    }
}
