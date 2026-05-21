package com.example.mediscanauth.controller.technician;

import com.example.mediscanauth.service.TechnicianWorkflowService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDateTime;

@Controller
public class TechnicianDashboardController {

    private final TechnicianWorkflowService technicianWorkflowService;

    public TechnicianDashboardController(TechnicianWorkflowService technicianWorkflowService) {
        this.technicianWorkflowService = technicianWorkflowService;
    }

    @GetMapping("/technician/dashboard")
    public String dashboard(Model model) {
        addDashboardData(model);
        return "technician/dashboard";
    }

    @GetMapping("/technician/overview")
    public String overview(Model model) {
        addDashboardData(model);
        return "technician/dashboard";
    }

    @GetMapping("/technician/appointments")
    public String appointments(Model model) {
        addDashboardData(model);
        return "technician/appointments";
    }

    @GetMapping("/technician/xray-upload")
    public String xrayUploadPage(Model model) {
        addDashboardData(model);
        return "technician/upload";
    }

    @GetMapping("/technician/recent")
    public String recent(Model model) {
        addDashboardData(model);
        return "technician/recent";
    }

    @PostMapping("/technician/appointments")
    public String createAppointment(Authentication authentication,
                                    @RequestParam String patientEmail,
                                    @RequestParam(required = false) String doctorEmail,
                                    @RequestParam String scheduledTime,
                                    @RequestParam String bodyPart,
                                    @RequestParam(required = false) String location,
                                    @RequestParam(required = false) String note,
                                    Model model) {
        try {
            technicianWorkflowService.createAppointment(
                    authentication.getName(),
                    patientEmail,
                    doctorEmail,
                    LocalDateTime.parse(scheduledTime),
                    bodyPart,
                    location,
                    note
            );
            return "redirect:/technician/dashboard?appointmentCreated";
        } catch (RuntimeException ex) {
            model.addAttribute("error", ex.getMessage());
            addDashboardData(model);
            return "technician/dashboard";
        }
    }

    @PostMapping("/technician/xray-upload")
    public String uploadXray(Authentication authentication,
                             @RequestParam(required = false) Long appointmentId,
                             @RequestParam(required = false) String patientEmail,
                             @RequestParam(required = false) String doctorEmail,
                             @RequestParam(required = false) String symptomDescription,
                             @RequestParam(required = false) String bodyPart,
                             @RequestParam String originalImagePath,
                             @RequestParam(required = false) String viewPosition,
                             Model model) {
        try {
            technicianWorkflowService.uploadImageAndCreateRecord(
                    authentication.getName(),
                    appointmentId,
                    patientEmail,
                    doctorEmail,
                    symptomDescription,
                    bodyPart,
                    originalImagePath,
                    viewPosition
            );
            return "redirect:/technician/dashboard?imageUploaded";
        } catch (RuntimeException ex) {
            model.addAttribute("error", ex.getMessage());
            addDashboardData(model);
            return "technician/upload";
        }
    }

    private void addDashboardData(Model model) {
        model.addAttribute("recentAppointments", technicianWorkflowService.findRecentAppointments());
        model.addAttribute("scheduledAppointments", technicianWorkflowService.findScheduledAppointments());
        model.addAttribute("recentRecords", technicianWorkflowService.findRecentRecords());
        model.addAttribute("recentImages", technicianWorkflowService.findRecentImages());
        model.addAttribute("scheduledCount", technicianWorkflowService.countScheduledAppointments());
        model.addAttribute("uploadedRecordCount", technicianWorkflowService.countUploadedRecords());
        model.addAttribute("uploadedImageCount", technicianWorkflowService.countUploadedImages());
        model.addAttribute("aiSuccessCount", technicianWorkflowService.countSuccessfulAiResults());
        model.addAttribute("approvedReviewCount", technicianWorkflowService.countApprovedReviews());
    }
}
