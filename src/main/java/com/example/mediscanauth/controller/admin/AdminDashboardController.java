package com.example.mediscanauth.controller.admin;

import com.example.mediscanauth.constant.OperationalConfig;
import com.example.mediscanauth.model.User;
import com.example.mediscanauth.service.ImagingRecordService;
import com.example.mediscanauth.service.impl.UserAdminService;
import com.example.mediscanauth.service.impl.UserExcelService;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.ByteArrayOutputStream;

@Controller
public class AdminDashboardController {

    private static final int USER_PAGE_SIZE = OperationalConfig.ADMIN_USER_PAGE_SIZE;

    private final UserAdminService userAdminService;
    private final ImagingRecordService imagingRecordService;
    private final UserExcelService userExcelService;

    public AdminDashboardController(UserAdminService userAdminService,
                                    ImagingRecordService imagingRecordService,
                                    UserExcelService userExcelService) {
        this.userAdminService = userAdminService;
        this.imagingRecordService = imagingRecordService;
        this.userExcelService = userExcelService;
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

    @GetMapping("/admin/users/export-excel")
    public ResponseEntity<byte[]> exportUsersExcel(@RequestParam(required = false) String keyword,
                                                   @RequestParam(required = false) String role,
                                                   @RequestParam(required = false) String status) throws Exception {
        ByteArrayOutputStream out = userExcelService.exportUsers(keyword, role, status);
        return buildExcelResponse(out, "users_export.xlsx");
    }

    @GetMapping("/admin/users/import-template")
    public ResponseEntity<byte[]> downloadImportTemplate() throws Exception {
        ByteArrayOutputStream out = userExcelService.buildImportTemplate();
        return buildExcelResponse(out, "user_import_template.xlsx");
    }

    @PostMapping("/admin/users/import-excel")
    public String importUsersExcel(@RequestPart("file") MultipartFile file,
                                   RedirectAttributes redirectAttributes) {
        try {
            UserExcelService.ImportResult result = userExcelService.importUsers(file);
            redirectAttributes.addFlashAttribute("success",
                    "Imported " + result.successCount() + " users. Failed: " + result.failCount());
            if (!result.errors().isEmpty()) {
                redirectAttributes.addFlashAttribute("importErrors", result.errors());
            }
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("error", "Import failed: " + ex.getMessage());
        }
        return "redirect:/admin/users";
    }

    private ResponseEntity<byte[]> buildExcelResponse(ByteArrayOutputStream out, String filename) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(out.toByteArray());
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
                             Authentication authentication,
                             RedirectAttributes redirectAttributes) {
        try {
            userAdminService.unlockAccount(userId, authentication.getName());
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
