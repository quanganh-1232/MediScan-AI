package com.example.mediscanauth.controller.common;

import com.example.mediscanauth.model.User;
import com.example.mediscanauth.repository.UserRepository;
import com.example.mediscanauth.service.ImagingRecordService;
import com.example.mediscanauth.service.NotificationService;
import com.example.mediscanauth.service.UserAccountService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    private final UserRepository userRepository;
    private final UserAccountService userAccountService;
    private final ImagingRecordService imagingRecordService;
    private final NotificationService notificationService;

    public HomeController(UserRepository userRepository,
                          UserAccountService userAccountService,
                          ImagingRecordService imagingRecordService,
                          NotificationService notificationService) {
        this.userRepository = userRepository;
        this.userAccountService = userAccountService;
        this.imagingRecordService = imagingRecordService;
        this.notificationService = notificationService;
    }

    @GetMapping("/home")
    public String home(Authentication authentication, Model model) {
        User user = userAccountService.findByEmail(authentication.getName());
        model.addAttribute("currentUser", user);
        model.addAttribute("activeUserCount", userRepository.countByStatus("ACTIVE"));
        model.addAttribute("queueCount", imagingRecordService.countQueue());
        model.addAttribute("todayRecordCount", imagingRecordService.countToday());
        model.addAttribute("totalRecordCount", imagingRecordService.countAll());
        model.addAttribute("recentRecords", imagingRecordService.findRecent());
        model.addAttribute("notifications", notificationService.findForUser(user));
        model.addAttribute("unreadCount", notificationService.countUnread(user));

        if (isAdmin(user)) {
            model.addAttribute("users", userRepository.findAll());
        }

        if (isPatient(user)) {
            model.addAttribute("patientRecordCount", imagingRecordService.countForPatient(user));
            model.addAttribute("latestRecord", imagingRecordService.findLatestForPatient(user));
        }

        return "common/home";
    }

    private boolean isAdmin(User user) {
        return user.getRole() != null
                && ("ADMIN".equals(user.getRole().getRoleName()) || "ROLE_ADMIN".equals(user.getRole().getRoleName()));
    }

    private boolean isPatient(User user) {
        return user.getRole() != null
                && ("PATIENT".equals(user.getRole().getRoleName()) || "ROLE_PATIENT".equals(user.getRole().getRoleName()));
    }
}
