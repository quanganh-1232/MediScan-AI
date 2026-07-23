package com.example.mediscanauth.controller.admin;

import com.example.mediscanauth.constant.OperationalConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AdminSystemConfigController {

    @Value("${ai.service.url}")
    private String aiServiceUrl;

    @Value("${ai.service.api-key}")
    private String aiServiceApiKey;

    @GetMapping("/admin/system-config")
    public String systemConfig(Model model) {
        model.addAttribute("aiServiceUrl", aiServiceUrl);
        model.addAttribute("aiServiceApiKeyMasked", maskSecret(aiServiceApiKey));
        model.addAttribute("aiConnectTimeoutMs", OperationalConfig.AI_CONNECT_TIMEOUT_MS);
        model.addAttribute("aiReadTimeoutMs", OperationalConfig.AI_READ_TIMEOUT_MS);
        model.addAttribute("aiConfidenceThreshold", OperationalConfig.AI_CONFIDENCE_STRONG_THRESHOLD);
        model.addAttribute("legacyTextLimit", OperationalConfig.LEGACY_TEXT_COLUMN_LIMIT);
        model.addAttribute("clinicOpenHour", OperationalConfig.CLINIC_OPEN_HOUR);
        model.addAttribute("clinicCloseHour", OperationalConfig.CLINIC_CLOSE_HOUR);
        model.addAttribute("slotMinutes", OperationalConfig.SLOT_MINUTES);
        model.addAttribute("maxFutureBookingDays", OperationalConfig.MAX_FUTURE_BOOKING_DAYS);
        model.addAttribute("maxSymptomLength", OperationalConfig.MAX_SYMPTOM_LENGTH);
        model.addAttribute("maxNoteLength", OperationalConfig.MAX_NOTE_LENGTH);
        model.addAttribute("adminUserPageSize", OperationalConfig.ADMIN_USER_PAGE_SIZE);
        return "admin/system-config";
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
