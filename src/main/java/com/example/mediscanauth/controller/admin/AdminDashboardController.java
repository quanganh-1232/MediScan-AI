package com.example.mediscanauth.controller.admin;

import com.example.mediscanauth.model.User;
import com.example.mediscanauth.service.ImagingRecordService;
import com.example.mediscanauth.service.impl.UserAdminService;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AdminDashboardController {

    private static final int USER_PAGE_SIZE = 20;

    private final UserAdminService userAdminService;
    private final ImagingRecordService imagingRecordService;

    public AdminDashboardController(UserAdminService userAdminService,
                                    ImagingRecordService imagingRecordService) {
        this.userAdminService = userAdminService;
        this.imagingRecordService = imagingRecordService;
    }

    @GetMapping("/admin/dashboard")
    public String dashboard(Model model) {
        addSharedModel(model);
        return "admin/dashboard";
    }

    @GetMapping("/admin/overview")
    public String overview(Model model) {
        addSharedModel(model);
        return "admin/dashboard";
    }

    @GetMapping("/admin/metrics")
    public String metrics(Model model) {
        addSharedModel(model);
        return "admin/metrics";
    }

    @GetMapping("/admin/users")
    public String users(@RequestParam(required = false) String keyword,
                        @RequestParam(required = false) String role,
                        @RequestParam(required = false) String status,
                        @RequestParam(defaultValue = "0") Integer page,
                        Model model) {
        addSharedModel(model);
        addUserManagementModel(model, keyword, role, status, page, null);
        return "admin/users";
    }

    @GetMapping("/admin/users/{userId}")
    public String userDetail(@PathVariable Long userId,
                             @RequestParam(required = false) String keyword,
                             @RequestParam(required = false) String role,
                             @RequestParam(required = false) String status,
                             @RequestParam(defaultValue = "0") Integer page,
                             Model model) {
        addSharedModel(model);
        addUserManagementModel(model, keyword, role, status, page, userId);
        return "admin/users";
    }

    @PostMapping("/admin/users/{userId}/lock")
    public String lockUser(@PathVariable Long userId,
                           Authentication authentication,
                           RedirectAttributes redirectAttributes) {
        try {
            userAdminService.lockAccount(userId, authentication.getName());
            redirectAttributes.addFlashAttribute("success", "Account locked successfully.");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return redirectToUserDetail(userId);
    }

    @PostMapping("/admin/users/{userId}/unlock")
    public String unlockUser(@PathVariable Long userId,
                             RedirectAttributes redirectAttributes) {
        try {
            userAdminService.unlockAccount(userId);
            redirectAttributes.addFlashAttribute("success", "Account unlocked successfully.");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return redirectToUserDetail(userId);
    }

    @PostMapping("/admin/users/{userId}/role")
    public String assignRole(@PathVariable Long userId,
                             @RequestParam String roleName,
                             Authentication authentication,
                             RedirectAttributes redirectAttributes) {
        try {
            userAdminService.assignUserRole(userId, roleName, authentication.getName());
            redirectAttributes.addFlashAttribute("success", "User role updated successfully.");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return redirectToUserDetail(userId);
    }

    @GetMapping("/admin/recent-records")
    public String recentRecords(Model model) {
        addSharedModel(model);
        return "admin/recent-records";
    }

    private void addSharedModel(Model model) {
        model.addAttribute("users", userAdminService.findLatestUsers(8));
        model.addAttribute("totalUserCount", userAdminService.count());
        model.addAttribute("activeUserCount", userAdminService.countByStatusUsingFilter("ACTIVE"));
        model.addAttribute("lockedUserCount", userAdminService.countByStatusUsingFilter("LOCKED"));
        model.addAttribute("queueCount", imagingRecordService.countQueue());
        model.addAttribute("todayRecordCount", imagingRecordService.countToday());
        model.addAttribute("totalRecordCount", imagingRecordService.countAll());
        model.addAttribute("recentRecords", imagingRecordService.findRecent());
    }

    private void addUserManagementModel(Model model,
                                        String keyword,
                                        String role,
                                        String status,
                                        Integer page,
                                        Long selectedUserId) {
        Page<User> userPage = userAdminService.filterUsers(keyword, role, status, page, USER_PAGE_SIZE);

        model.addAttribute("userPage", userPage);
        model.addAttribute("users", userPage.getContent());
        model.addAttribute("roles", userAdminService.getAssignableRoles());
        model.addAttribute("keyword", keyword == null ? "" : keyword);
        model.addAttribute("selectedRole", role == null ? "" : role);
        model.addAttribute("selectedStatus", status == null ? "" : status);

        if (selectedUserId != null) {
            try {
                model.addAttribute("selectedUser", userAdminService.getUserDetail(selectedUserId));
            } catch (IllegalArgumentException ex) {
                model.addAttribute("error", ex.getMessage());
            }
        }
    }

    private String redirectToUserDetail(Long userId) {
        return "redirect:/admin/users/" + userId;
    }
}
