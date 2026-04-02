package com.example.demo.dto;

import com.example.demo.entity.Notification;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationResponseTest {

    @Test
    void from_mapsAllFieldsCorrectly() {
        LocalDateTime now = LocalDateTime.now();
        Notification n = new Notification();
        n.setId(1L);
        n.setType("email");
        n.setRecipient("user@example.com");
        n.setSubject("Hello");
        n.setContent("World");
        n.setCreatedAt(now);

        NotificationResponse response = NotificationResponse.from(n);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getType()).isEqualTo("email");
        assertThat(response.getRecipient()).isEqualTo("user@example.com");
        assertThat(response.getSubject()).isEqualTo("Hello");
        assertThat(response.getContent()).isEqualTo("World");
        assertThat(response.getCreatedAt()).isEqualTo(now);
    }

    @Test
    void from_handlesNullOptionalFields() {
        Notification n = new Notification();
        n.setId(2L);
        n.setType("sms");
        n.setRecipient("+886900000000");
        n.setSubject(null);
        n.setContent(null);
        n.setCreatedAt(LocalDateTime.now());

        NotificationResponse response = NotificationResponse.from(n);

        assertThat(response.getSubject()).isNull();
        assertThat(response.getContent()).isNull();
        assertThat(response.getType()).isEqualTo("sms");
    }

}
