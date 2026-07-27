package com.example.mediscanauth.controller.technician;

import com.example.mediscanauth.service.ImagingRecordService;
import com.example.mediscanauth.service.TechnicianWorkflowService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

@Controller
public class TechnicianDashboardController {

    private final TechnicianWorkflowService technicianWorkflowService;
    private final ImagingRecordService imagingRecordService;

    public TechnicianDashboardController(TechnicianWorkflowService technicianWorkflowService,
                                         ImagingRecordService imagingRecordService) {
        this.technicianWorkflowService = technicianWorkflowService;
        this.imagingRecordService = imagingRecordService;
    }

    @GetMapping("/technician/dashboard")
    public String dashboard(Model model,
                            Authentication authentication) {
        addDashboardData(model, authentication);
        return "/technician/dashboard";
    }

    @GetMapping("/technician/overview")
    public String overview(Model model,
                           Authentication authentication) {
        addDashboardData(model, authentication);
        model.addAttribute(
                "scheduledAppointments",
                technicianWorkflowService.findScheduledAppointments());
        return "/technician/overview";
    }

    @GetMapping("/technician/appointments")
    public String appointments(Model model,
                               Authentication authentication) {
        addDashboardData(model, authentication);
        return "redirect:/technician/xray-upload";
    }

    @GetMapping("/technician/xray-upload")
    public String xrayUploadPage(Model model,
                                 Authentication authentication,
                                 @RequestParam(required = false) Boolean success) {
        addDashboardData(model, authentication);
        model.addAttribute("success", success != null && success);
        model.addAttribute("successMessage", success != null && success ? "Đã chụp phim và gửi tới bác sĩ thành công." : null);
        return "technician/upload";
    }

    @GetMapping("/technician/recent")
    public String recent() {
        return "redirect:/technician/xray-upload";
    }

    @PostMapping("/technician/xray-upload")
    public String uploadXray(Authentication authentication,
                             @RequestParam("image")
                             MultipartFile image,
                             @RequestParam Long appointmentId,
                             Model model) {
        try {
            imagingRecordService.captureAndAnalyzeFromTechnician(
                    authentication.getName(),
                    appointmentId,
                    image
            );
            return "redirect:/technician/xray-upload?success=true";
        } catch (RuntimeException ex) {
            model.addAttribute("error", ex.getMessage());
            addDashboardData(model, authentication);
            return "technician/upload";
        }
    }

    @GetMapping("/technician/record/{recordId}")
    public String technicianRecordDetail(@PathVariable Long recordId, Model model, Authentication authentication) {
        model.addAttribute("record", imagingRecordService.getRecordById(recordId));
        addDashboardData(model, authentication);
        return "technician/record";
    }

    private void addDashboardData(Model model, Authentication authentication) {
        model.addAttribute("recentAppointments", technicianWorkflowService.findRecentAppointments());
        model.addAttribute("scheduledAppointments", technicianWorkflowService.findScheduledAppointments());
        model.addAttribute("recentRecords", technicianWorkflowService.findRecentRecords());
        model.addAttribute("recentImages", technicianWorkflowService.findRecentImages());
        model.addAttribute("scheduledCount", technicianWorkflowService.countScheduledAppointments());
        model.addAttribute("uploadedRecordCount", technicianWorkflowService.countUploadedRecords());
        model.addAttribute("uploadedImageCount", technicianWorkflowService.countUploadedImages());
        model.addAttribute("aiSuccessCount", technicianWorkflowService.countSuccessfulAiResults());
        model.addAttribute("approvedReviewCount", technicianWorkflowService.countApprovedReviews());
        model.addAttribute("eligibleAppointments", imagingRecordService.findAppointmentsEligibleForCapture());
        model.addAttribute("historyRecords", imagingRecordService.findRecordsUploadedByTechnician(authentication.getName()));
    }
}
