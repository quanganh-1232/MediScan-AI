package com.example.mediscanauth.controller.admin;

import com.example.mediscanauth.service.impl.AiMonitoringService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AdminAiMonitoringController {

    private static final int TREND_DAYS = 14;
    private static final int AI_FAILED_LIMIT = 20;

    private final AiMonitoringService aiMonitoringService;

    public AdminAiMonitoringController(AiMonitoringService aiMonitoringService) {
        this.aiMonitoringService = aiMonitoringService;
    }

    @GetMapping("/admin/ai-monitoring")
    public String aiMonitoring(Model model) {
        model.addAttribute("riskDistribution", aiMonitoringService.riskLevelDistribution());
        model.addAttribute("totalRiskCount", Math.max(1, aiMonitoringService.totalWithKnownRiskLevel()));
        model.addAttribute("confidenceTrend", aiMonitoringService.confidenceTrend(TREND_DAYS));
        model.addAttribute("overrideStats", aiMonitoringService.overrideStats());
        model.addAttribute("aiFailedCases", aiMonitoringService.recentAiFailedCases(AI_FAILED_LIMIT));
        model.addAttribute("aiFailedCount", aiMonitoringService.countAiFailed());
        return "admin/ai-monitoring";
    }
}
