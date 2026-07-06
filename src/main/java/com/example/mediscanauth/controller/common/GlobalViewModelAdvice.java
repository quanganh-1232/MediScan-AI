package com.example.mediscanauth.controller.common;

import com.example.mediscanauth.model.User;
import com.example.mediscanauth.service.NotificationService;
import com.example.mediscanauth.service.UserAccountService;
import org.springframework.security.core.Authentication;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class GlobalViewModelAdvice {

    private final UserAccountService userAccountService;
    private final NotificationService notificationService;

    public GlobalViewModelAdvice(UserAccountService userAccountService,
                                 NotificationService notificationService) {
        this.userAccountService = userAccountService;
        this.notificationService = notificationService;
    }

    @ModelAttribute
    public void addGlobalViewData(Authentication authentication, Model model) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return;
        }

        String email = authentication.getName();
        if (email == null || "anonymousUser".equals(email)) {
            return;
        }

        try {
            User user = userAccountService.findByEmail(email);
            model.addAttribute("currentUser", user);
            model.addAttribute("currentRoleLabel", roleLabel(user));
            model.addAttribute("headerNotifications", notificationService.findRecentForUser(user));
            model.addAttribute("headerUnreadCount", notificationService.countUnread(user));
        } catch (RuntimeException ex) {
            model.addAttribute("headerNotifications", java.util.List.of());
            model.addAttribute("headerUnreadCount", 0);
        }
    }

    private String roleLabel(User user) {
        if (user == null || user.getRole() == null || user.getRole().getRoleName() == null) {
            return "Người dùng";
        }

        return switch (user.getRole().getRoleName()) {
            case "ADMIN", "ROLE_ADMIN" -> "Quản trị viên";
            case "DOCTOR", "ROLE_DOCTOR" -> "Bác sĩ";
            case "TECHNICIAN", "ROLE_TECHNICIAN" -> "Kỹ thuật viên";
            case "PATIENT", "ROLE_PATIENT" -> "Bệnh nhân";
            case "RECEPTIONIST", "ROLE_RECEPTIONIST" -> "Lễ tân";
            default -> "Người dùng";
        };
    }
}
