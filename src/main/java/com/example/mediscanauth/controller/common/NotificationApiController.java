package com.example.mediscanauth.controller.common;

import com.example.mediscanauth.model.Notification;
import com.example.mediscanauth.model.User;
import com.example.mediscanauth.service.NotificationService;
import com.example.mediscanauth.service.UserAccountService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController
@RequestMapping("/api/notifications")
public class NotificationApiController {

    private final UserAccountService userAccountService;
    private final NotificationService notificationService;

    public NotificationApiController(UserAccountService userAccountService,
                                     NotificationService notificationService) {
        this.userAccountService = userAccountService;
        this.notificationService = notificationService;
    }

    @GetMapping("/recent")
    public ResponseEntity<Map<String, Object>> getRecentNotifications(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getName())) {
            return ResponseEntity.status(401).build();
        }

        try {
            User user = userAccountService.findByEmail(authentication.getName());
            Map<String, Object> response = new HashMap<>();
            response.put("unreadCount", notificationService.countUnread(user));

            // Manually map to plain maps to avoid lazy-loading / infinite recursion
            List<Notification> notifications = notificationService.findRecentForUser(user);
            List<Map<String, Object>> dtos = new ArrayList<>();
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
            for (Notification n : notifications) {
                Map<String, Object> dto = new LinkedHashMap<>();
                dto.put("notificationId", n.getNotificationId());
                dto.put("title", n.getTitle());
                dto.put("message", n.getMessage());
                dto.put("read", n.isRead());
                dto.put("createdAt", n.getCreatedAt() != null ? n.getCreatedAt().format(fmt) : null);
                dtos.add(dto);
            }
            response.put("notifications", dtos);
            return ResponseEntity.ok(response);
        } catch (RuntimeException ex) {
            return ResponseEntity.status(500).build();
        }
    }
}
