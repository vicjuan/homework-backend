package com.example.demo.exception;

public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }

    public NotFoundException(Long id) {
        super("Notification not found: " + id);
    }
}
