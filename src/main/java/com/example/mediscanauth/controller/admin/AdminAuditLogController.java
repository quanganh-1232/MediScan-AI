package com.example.mediscanauth.controller.admin;

import com.example.mediscanauth.model.SystemLog;
import com.example.mediscanauth.service.impl.AuditLogService;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.List;

@Controller
public class AdminAuditLogController {

    private static final int PAGE_SIZE = 20;
    private static final List<String> ENTITY_NAMES = List.of("User", "Patient", "Appointment", "ImagingRecord");

    private final AuditLogService auditLogService;

    public AdminAuditLogController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @GetMapping("/admin/audit-log")
    public String auditLog(@RequestParam(required = false) String keyword,
                           @RequestParam(required = false) String action,
                           @RequestParam(required = false) String entityName,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
                           @RequestParam(defaultValue = "0") Integer page,
                           Model model) {
        Page<SystemLog> logPage = auditLogService.filterLogs(keyword, action, entityName, fromDate, toDate, page, PAGE_SIZE);

        model.addAttribute("logPage", logPage);
        model.addAttribute("logs", logPage.getContent());
        model.addAttribute("keyword", keyword == null ? "" : keyword);
        model.addAttribute("selectedAction", action == null ? "" : action);
        model.addAttribute("selectedEntity", entityName == null ? "" : entityName);
        model.addAttribute("fromDate", fromDate);
        model.addAttribute("toDate", toDate);
        model.addAttribute("actions", auditLogService.distinctActions());
        model.addAttribute("entityNames", ENTITY_NAMES);
        return "admin/audit-log";
    }
}
