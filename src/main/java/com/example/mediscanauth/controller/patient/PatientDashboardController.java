package com.example.mediscanauth.controller.patient;

import com.example.mediscanauth.model.User;
import com.example.mediscanauth.service.ImagingRecordService;
import com.example.mediscanauth.service.NotificationService;
import com.example.mediscanauth.service.UserAccountService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import com.example.mediscanauth.service.PatientWorkflowService;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class PatientDashboardController {

    private final UserAccountService userAccountService;
    private final ImagingRecordService imagingRecordService;
    private final NotificationService notificationService;
    private final PatientWorkflowService patientWorkflowService;

    public PatientDashboardController(UserAccountService userAccountService,
                                      ImagingRecordService imagingRecordService,
                                      NotificationService notificationService,
                                      PatientWorkflowService patientWorkflowService) {
        this.userAccountService = userAccountService;
        this.imagingRecordService = imagingRecordService;
        this.notificationService = notificationService;
        this.patientWorkflowService = patientWorkflowService;
    }

    @GetMapping("/patient/dashboard")
    public String dashboard() {
        return "redirect:/home";
    }

    @GetMapping("/patient/overview")
    public String overview() {
        return "redirect:/home";
    }

    @GetMapping("/patient/records")
    public String records(Authentication authentication, Model model) {
        addModel(authentication, model);
        return "patient/records";
    }

    @GetMapping("/patient/results")
    public String results(Authentication authentication, Model model) {
        addModel(authentication, model);
        return "patient/results";
    }

    @GetMapping("/patient/support")
    public String support(Authentication authentication, Model model) {
        addModel(authentication, model);
        return "patient/support";
    }

    @GetMapping("/patient/upload")
    public String uploadForm(Authentication authentication, Model model) {
        addModel(authentication, model);
        model.addAttribute("activeSection", "upload-form");
        return "patient/upload";
    }

    @PostMapping("/patient/upload")
    public String uploadXray(Authentication authentication,
                             @RequestParam("bodyPart") String bodyPart,
                             @RequestParam("xrayImage") MultipartFile file,
                             RedirectAttributes redirectAttributes) {
        if (file.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Vui lòng chọn file ảnh.");
            return "redirect:/patient/upload";
        }
        
        try {
            patientWorkflowService.uploadImageAndAnalyze(authentication.getName(), bodyPart, file);
            redirectAttributes.addFlashAttribute("successMessage", "Tải lên và phân tích AI thành công!");
            return "redirect:/patient/results";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Đã xảy ra lỗi: " + e.getMessage());
            return "redirect:/patient/upload";
        }
    }

    private void addModel(Authentication authentication, Model model) {
        User patient = userAccountService.findByEmail(authentication.getName());
        model.addAttribute("currentUser", patient);
        model.addAttribute("records", imagingRecordService.findForPatient(patient));
        model.addAttribute("latestRecord", imagingRecordService.findLatestForPatient(patient));
        model.addAttribute("recordCount", imagingRecordService.countForPatient(patient));
        model.addAttribute("unreadCount", notificationService.countUnread(patient));
    }
}
