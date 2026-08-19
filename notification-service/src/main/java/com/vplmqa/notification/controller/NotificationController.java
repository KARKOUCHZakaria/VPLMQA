package com.vplmqa.notification.controller;

import com.vplmqa.common.ApiResponse;
import com.vplmqa.notification.dto.DeliveryLogResponse;
import com.vplmqa.notification.dto.NotificationPreferenceRequest;
import com.vplmqa.notification.dto.NotificationPreferenceResponse;
import com.vplmqa.notification.entity.DeliveryLog;
import com.vplmqa.notification.service.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<DeliveryLog>>> list(@RequestParam(required = false) UUID userId,
                                                               @RequestHeader(value = "X-User-Id", required = false) UUID currentUserId) {
        UUID effectiveUserId = userId != null ? userId : currentUserId;
        return ResponseEntity.ok(ApiResponse.ok(notificationService.getLogs(effectiveUserId)));
    }

    @GetMapping("/preferences")
    public ResponseEntity<ApiResponse<NotificationPreferenceResponse>> getPreferences(@RequestParam UUID userId,
                                                                                       @RequestParam UUID projectId) {
        return ResponseEntity.ok(ApiResponse.ok(notificationService.getPreferences(userId, projectId)));
    }

    @PutMapping("/preferences")
    public ResponseEntity<ApiResponse<NotificationPreferenceResponse>> updatePreferences(@RequestBody NotificationPreferenceRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(notificationService.updatePreferences(request)));
    }

    @GetMapping("/log")
    public ResponseEntity<ApiResponse<List<DeliveryLog>>> log(@RequestParam UUID userId) {
        return ResponseEntity.ok(ApiResponse.ok(notificationService.getLogs(userId)));
    }
}
