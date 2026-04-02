package com.example.demo.dto;

import com.example.demo.entity.Notification;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDateTime;

@Getter
@Setter
public class NotificationResponse implements Serializable {

    private Long id;
    private String type;
    private String recipient;
    private String subject;
    private String content;
    private LocalDateTime createdAt;

    public static NotificationResponse from(Notification n) {
        NotificationResponse r = new NotificationResponse();
        r.id = n.getId();
        r.type = n.getType();
        r.recipient = n.getRecipient();
        r.subject = n.getSubject();
        r.content = n.getContent();
        r.createdAt = n.getCreatedAt();
        return r;
    }
}
