package com.example.demo.event;

import com.example.demo.entity.Notification;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class NotificationCreatedEvent {

    private final Notification notification;
}
