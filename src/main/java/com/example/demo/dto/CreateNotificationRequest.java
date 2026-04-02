package com.example.demo.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateNotificationRequest {

    @NotBlank(message = "type is required")
    private String type;

    @NotBlank(message = "recipient is required")
    private String recipient;

    private String subject;

    private String content;
}
