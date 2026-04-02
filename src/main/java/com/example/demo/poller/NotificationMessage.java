package com.example.demo.poller;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationMessage implements Serializable {

    private Long id;
    private String type;
    private String recipient;
    private String subject;
    private String content;
    private LocalDateTime createdAt;
}
