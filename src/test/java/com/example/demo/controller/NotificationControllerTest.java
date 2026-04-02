package com.example.demo.controller;

import com.example.demo.dto.CreateNotificationRequest;
import com.example.demo.dto.NotificationResponse;
import com.example.demo.dto.UpdateNotificationRequest;
import com.example.demo.exception.NotFoundException;
import com.example.demo.service.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(NotificationController.class)
class NotificationControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean NotificationService service;

    // ── POST /notifications ────────────────────────────────────────────────────

    @Test
    void createNotification_returns201WithBody() throws Exception {
        CreateNotificationRequest request = buildCreateRequest("email", "user@example.com");
        NotificationResponse response = buildResponse(1L, "email", "user@example.com");
        when(service.createNotification(any())).thenReturn(response);

        mockMvc.perform(post("/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.type").value("email"))
                .andExpect(jsonPath("$.recipient").value("user@example.com"));
    }

    @Test
    void createNotification_returns400WhenTypeMissing() throws Exception {
        CreateNotificationRequest request = buildCreateRequest(null, "user@example.com");

        mockMvc.perform(post("/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.type").exists());
    }

    @Test
    void createNotification_returns400WhenRecipientMissing() throws Exception {
        CreateNotificationRequest request = buildCreateRequest("sms", null);

        mockMvc.perform(post("/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.recipient").exists());
    }

    @Test
    void createNotification_returns400WhenBothRequiredFieldsMissing() throws Exception {
        CreateNotificationRequest request = buildCreateRequest(null, null);

        mockMvc.perform(post("/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.type").exists())
                .andExpect(jsonPath("$.fields.recipient").exists());
    }

    // ── GET /notifications/{id} ───────────────────────────────────────────────

    @Test
    void getById_returns200WithBody() throws Exception {
        NotificationResponse response = buildResponse(1L, "email", "user@example.com");
        when(service.getNotificationById(1L)).thenReturn(response);

        mockMvc.perform(get("/notifications/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.type").value("email"));
    }

    @Test
    void getById_returns404WhenNotFound() throws Exception {
        when(service.getNotificationById(99L)).thenThrow(new NotFoundException(99L));

        mockMvc.perform(get("/notifications/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").exists());
    }

    // ── GET /notifications/recent ─────────────────────────────────────────────

    @Test
    void getRecent_returns200WithList() throws Exception {
        List<NotificationResponse> responses = List.of(
                buildResponse(2L, "email", "a@example.com"),
                buildResponse(1L, "sms", "+886900000000")
        );
        when(service.getRecentNotifications()).thenReturn(responses);

        mockMvc.perform(get("/notifications/recent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(2L))
                .andExpect(jsonPath("$[1].id").value(1L));
    }

    @Test
    void getRecent_returns200WithEmptyList() throws Exception {
        when(service.getRecentNotifications()).thenReturn(List.of());

        mockMvc.perform(get("/notifications/recent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ── PUT /notifications/{id} ───────────────────────────────────────────────

    @Test
    void update_returns200WithUpdatedBody() throws Exception {
        UpdateNotificationRequest request = new UpdateNotificationRequest();
        request.setSubject("New Subject");
        request.setContent("New Content");

        NotificationResponse response = buildResponse(1L, "email", "user@example.com");
        response.setSubject("New Subject");
        response.setContent("New Content");

        when(service.updateNotification(eq(1L), any())).thenReturn(response);

        mockMvc.perform(put("/notifications/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subject").value("New Subject"))
                .andExpect(jsonPath("$.content").value("New Content"));
    }

    @Test
    void update_returns404WhenNotFound() throws Exception {
        UpdateNotificationRequest request = new UpdateNotificationRequest();
        when(service.updateNotification(eq(99L), any())).thenThrow(new NotFoundException(99L));

        mockMvc.perform(put("/notifications/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    // ── DELETE /notifications/{id} ────────────────────────────────────────────

    @Test
    void delete_returns204() throws Exception {
        doNothing().when(service).deleteNotification(1L);

        mockMvc.perform(delete("/notifications/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void delete_returns404WhenNotFound() throws Exception {
        doThrow(new NotFoundException(99L)).when(service).deleteNotification(99L);

        mockMvc.perform(delete("/notifications/99"))
                .andExpect(status().isNotFound());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private CreateNotificationRequest buildCreateRequest(String type, String recipient) {
        CreateNotificationRequest r = new CreateNotificationRequest();
        r.setType(type);
        r.setRecipient(recipient);
        r.setSubject("Subject");
        r.setContent("Content");
        return r;
    }

    private NotificationResponse buildResponse(Long id, String type, String recipient) {
        NotificationResponse r = new NotificationResponse();
        r.setId(id);
        r.setType(type);
        r.setRecipient(recipient);
        r.setSubject("Subject");
        r.setContent("Content");
        r.setCreatedAt(LocalDateTime.now());
        return r;
    }
}
