package com.example.demo.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateNotificationRequest {

    private String subject;
    private String content;
}
