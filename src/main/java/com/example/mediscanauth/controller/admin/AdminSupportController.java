package com.example.mediscanauth.controller.admin;

import com.example.mediscanauth.model.SupportRequest;
import com.example.mediscanauth.service.impl.SupportRequestService;
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
public class AdminSupportController {

    private static final int PAGE_SIZE = 20;

    private final SupportRequestService supportRequestService;

    public AdminSupportController(SupportRequestService supportRequestService) {
        this.supportRequestService = supportRequestService;
    }

    @GetMapping("/admin/support")
    public String support(@RequestParam(required = false) String keyword,
                          @RequestParam(required = false) String status,
                          @RequestParam(defaultValue = "0") Integer page,
                          Model model) {
        Page<SupportRequest> requestPage = supportRequestService.filterRequests(keyword, status, page, PAGE_SIZE);

        model.addAttribute("requestPage", requestPage);
        model.addAttribute("requests", requestPage.getContent());
        model.addAttribute("keyword", keyword == null ? "" : keyword);
        model.addAttribute("selectedStatus", status == null ? "" : status);
        model.addAttribute("statuses", SupportRequestService.STATUSES);
        model.addAttribute("openCount", supportRequestService.countByStatus("OPEN"));
        model.addAttribute("inProgressCount", supportRequestService.countByStatus("IN_PROGRESS"));
        model.addAttribute("resolvedCount", supportRequestService.countByStatus("RESOLVED"));
        return "admin/support";
    }

    @PostMapping("/admin/support/{id}/respond")
    public String respond(@PathVariable Long id,
                          @RequestParam String adminResponse,
                          @RequestParam String status,
                          Authentication authentication,
                          RedirectAttributes redirect) {
        try {
            supportRequestService.respond(id, authentication.getName(), adminResponse, status);
            redirect.addFlashAttribute("success", "Đã gửi phản hồi.");
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/support";
    }
}
