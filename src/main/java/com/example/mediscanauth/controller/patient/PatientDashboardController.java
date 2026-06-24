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
    public String records(Authentication authentication, Model model,
                          @RequestParam(defaultValue = "0") int page,
                          @RequestParam(defaultValue = "10") int size,
                          @RequestParam(required = false) String keyword,
                          @RequestParam(required = false) String bodyPart) {
        User patient = userAccountService.findByEmail(authentication.getName());
        model.addAttribute("currentUser", patient);
        model.addAttribute("latestRecord", imagingRecordService.findLatestForPatient(patient));
        model.addAttribute("recordCount", imagingRecordService.countForPatient(patient));
        model.addAttribute("unreadCount", notificationService.countUnread(patient));
        model.addAttribute("activeSection", "records");

        model.addAttribute("keyword", keyword);
        model.addAttribute("bodyPart", bodyPart);
        model.addAttribute("recordPage", imagingRecordService.searchForPatient(patient, keyword, bodyPart, org.springframework.data.domain.PageRequest.of(page, size)));
        return "patient/records";
    }

    @GetMapping("/patient/records/{id}")
    public String recordDetail(Authentication authentication, @org.springframework.web.bind.annotation.PathVariable("id") Long id, Model model, RedirectAttributes redirectAttributes) {
        User patient = userAccountService.findByEmail(authentication.getName());
        try {
            com.example.mediscanauth.model.ImagingRecord record = imagingRecordService.getRecordById(id);
            if (!record.getPatient().getUserId().equals(patient.getUserId())) {
                redirectAttributes.addFlashAttribute("errorMessage", "Bạn không có quyền xem hồ sơ này.");
                return "redirect:/patient/records";
            }
            model.addAttribute("record", record);
            model.addAttribute("currentUser", patient);
            model.addAttribute("activeSection", "records");
            model.addAttribute("unreadCount", notificationService.countUnread(patient));
            return "patient/record-detail";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Không tìm thấy hồ sơ.");
            return "redirect:/patient/records";
        }
    }

    @PostMapping("/patient/records/delete")
    public String deleteRecord(Authentication authentication, @RequestParam("recordId") Long recordId, RedirectAttributes redirectAttributes) {
        User patient = userAccountService.findByEmail(authentication.getName());
        try {
            imagingRecordService.deleteRecordForPatient(recordId, patient);
            redirectAttributes.addFlashAttribute("successMessage", "Đã xóa hồ sơ thành công.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/patient/records";
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
            return "redirect:/patient/records";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Đã xảy ra lỗi: " + e.getMessage());
            return "redirect:/patient/upload";
        }
    }

    private void addModel(Authentication authentication, Model model) {
        User patient = userAccountService.findByEmail(authentication.getName());
        model.addAttribute("currentUser", patient);
        model.addAttribute("latestRecord", imagingRecordService.findLatestForPatient(patient));
        model.addAttribute("recordCount", imagingRecordService.countForPatient(patient));
        model.addAttribute("unreadCount", notificationService.countUnread(patient));
    }
}
