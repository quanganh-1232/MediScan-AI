package com.example.mediscanauth.controller.common;

import com.example.mediscanauth.controller.common.dto.ProfileUpdateDTO;
import com.example.mediscanauth.model.User;
import com.example.mediscanauth.service.NotificationService;
import com.example.mediscanauth.service.UserAccountService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class UserAccountController {

    private final UserAccountService userAccountService;
    private final NotificationService notificationService;

    public UserAccountController(UserAccountService userAccountService,
                                 NotificationService notificationService) {
        this.userAccountService = userAccountService;
        this.notificationService = notificationService;
    }

    @GetMapping("/profile")
    public String profile(Authentication authentication, Model model) {
        User user = userAccountService.findByEmail(authentication.getName());
        model.addAttribute("user", user);
        return "common/profile";
    }

    @GetMapping("/profile/edit")
    public String editProfile(Authentication authentication, Model model) {
        User user = userAccountService.findByEmail(authentication.getName());
        ProfileUpdateDTO dto = new ProfileUpdateDTO();
        dto.setFullName(user.getFullName());
        dto.setEmail(user.getEmail());
        dto.setPhone(user.getPhone());
        model.addAttribute("profileUpdateDTO", dto);
        return "common/edit-profile";
    }

    @PostMapping("/profile/edit")
    public String updateProfile(Authentication authentication,
                                @Valid @ModelAttribute("profileUpdateDTO") ProfileUpdateDTO dto,
                                org.springframework.validation.BindingResult bindingResult,
                                Model model) {
        if (bindingResult.hasErrors()) {
            return "common/edit-profile";
        }

        try {
            userAccountService.updateProfile(authentication.getName(), dto.getFullName(), dto.getEmail(), dto.getPhone());
            model.addAttribute("success", "Cập nhật thông tin thành công. Nếu đổi email, hãy đăng nhập lại để đồng bộ phiên.");
            return "common/edit-profile";
        } catch (IllegalArgumentException ex) {
            model.addAttribute("error", ex.getMessage());
            return "common/edit-profile";
        }
    }

    @GetMapping("/change-password")
    public String changePassword() {
        return "common/change-password";
    }

    @PostMapping("/change-password")
    public String updatePassword(Authentication authentication,
                                 @RequestParam String currentPassword,
                                 @RequestParam String newPassword,
                                 @RequestParam String confirmPassword,
                                 Model model) {
        try {
            userAccountService.changePassword(authentication.getName(), currentPassword, newPassword, confirmPassword);
            model.addAttribute("success", "Đổi mật khẩu thành công.");
        } catch (IllegalArgumentException ex) {
            model.addAttribute("error", ex.getMessage());
        }
        return "common/change-password";
    }

    @GetMapping("/notifications")
    public String notifications(Authentication authentication, Model model) {
        User user = userAccountService.findByEmail(authentication.getName());
        model.addAttribute("notifications", notificationService.findForUser(user));
        model.addAttribute("unreadCount", notificationService.countUnread(user));
        return "common/notifications";
    }
}
