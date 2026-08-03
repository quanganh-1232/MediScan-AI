package com.example.mediscanauth.controller.admin;

import com.example.mediscanauth.constant.OperationalConfig;
import com.example.mediscanauth.model.ClinicSettings;
import com.example.mediscanauth.service.impl.ClinicSettingsService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AdminSystemConfigController {

    private final ClinicSettingsService clinicSettingsService;

    @Value("${ai.service.url}")
    private String aiServiceUrl;

    @Value("${ai.service.api-key}")
    private String aiServiceApiKey;

    public AdminSystemConfigController(ClinicSettingsService clinicSettingsService) {
        this.clinicSettingsService = clinicSettingsService;
    }

    @GetMapping("/admin/system-config")
    public String systemConfig(Model model) {
        ClinicSettings clinicSettings = clinicSettingsService.getSettings();

        model.addAttribute("aiServiceUrl", aiServiceUrl);
        model.addAttribute("aiServiceApiKeyMasked", maskSecret(aiServiceApiKey));
        model.addAttribute("aiConnectTimeoutMs", OperationalConfig.AI_CONNECT_TIMEOUT_MS);
        model.addAttribute("aiReadTimeoutMs", OperationalConfig.AI_READ_TIMEOUT_MS);
        model.addAttribute("aiConfidenceThreshold", OperationalConfig.AI_CONFIDENCE_STRONG_THRESHOLD);
        model.addAttribute("legacyTextLimit", OperationalConfig.LEGACY_TEXT_COLUMN_LIMIT);
        model.addAttribute("clinicOpenHour", clinicSettings.getOpenHour());
        model.addAttribute("clinicCloseHour", clinicSettings.getCloseHour());
        model.addAttribute("slotMinutes", clinicSettings.getSlotMinutes());
        model.addAttribute("maxFutureBookingDays", clinicSettings.getMaxFutureBookingDays());
        model.addAttribute("maxSymptomLength", OperationalConfig.MAX_SYMPTOM_LENGTH);
        model.addAttribute("maxNoteLength", OperationalConfig.MAX_NOTE_LENGTH);
        model.addAttribute("adminUserPageSize", OperationalConfig.ADMIN_USER_PAGE_SIZE);
        return "admin/system-config";
    }

    @PostMapping("/admin/system-config/clinic")
    public String updateClinicSettings(@RequestParam Integer clinicOpenHour,
                                       @RequestParam Integer clinicCloseHour,
                                       @RequestParam Integer slotMinutes,
                                       @RequestParam Integer maxFutureBookingDays,
                                       Authentication authentication,
                                       RedirectAttributes redirect) {
        try {
            clinicSettingsService.updateSettings(clinicOpenHour, clinicCloseHour, slotMinutes,
                    maxFutureBookingDays, authentication.getName());
            redirect.addFlashAttribute("success", "Đã cập nhật cấu hình lịch hẹn & vận hành phòng khám.");
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/system-config";
    }

    private String maskSecret(String value) {
        if (value == null || value.isBlank()) {
            return "Chưa cấu hình";
        }
        if (value.length() <= 6) {
            return "*".repeat(value.length());
        }
        return value.substring(0, 4) + "*".repeat(Math.max(4, value.length() - 8)) + value.substring(value.length() - 4);
    }
}
