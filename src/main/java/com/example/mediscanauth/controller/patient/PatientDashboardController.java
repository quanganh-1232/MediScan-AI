package com.example.mediscanauth.controller.patient;

import com.example.mediscanauth.model.MedicalRecord;
import com.example.mediscanauth.model.User;
import com.example.mediscanauth.service.ImagingRecordService;
import com.example.mediscanauth.service.NotificationService;
import com.example.mediscanauth.service.UserAccountService;
import com.example.mediscanauth.service.MedicalRecordService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.data.domain.Page;

@Controller
public class PatientDashboardController {

    private final UserAccountService userAccountService;
    private final ImagingRecordService imagingRecordService;
    private final NotificationService notificationService;
    private final MedicalRecordService medicalRecordService;

    public PatientDashboardController(UserAccountService userAccountService,
                                      ImagingRecordService imagingRecordService,
                                      NotificationService notificationService,
                                      MedicalRecordService medicalRecordService) {
        this.userAccountService = userAccountService;
        this.imagingRecordService = imagingRecordService;
        this.notificationService = notificationService;
        this.medicalRecordService = medicalRecordService;
    }

    // =========================================================================
    // TRANG CHỦ & ĐIỀU HƯỚNG
    // =========================================================================

    @GetMapping("/patient/dashboard")
    public String dashboard(Authentication authentication,
                            @RequestParam(required = false) String bodyPart,
                            Model model) {

        // BƯỚC NÀY CHỈ LÀM LỌC, CHƯA PHÂN TRANG (Truyền page = 0)
        addModel(authentication, model, bodyPart, 0);
        model.addAttribute("activeMenu", "overview");
        return "patient/dashboard";
    }

    @GetMapping("/patient/overview")
    public String overview(Authentication authentication, Model model) {
        addModel(authentication, model, null, 0);
        model.addAttribute("activeMenu", "overview");
        return "patient/dashboard";
    }

    @GetMapping("/patient/records")
    public String records(Authentication authentication, Model model) {
        addModel(authentication, model, null, 0);
        model.addAttribute("activeMenu", "records");
        return "patient/dashboard";
    }

    @GetMapping("/patient/results")
    public String results(Authentication authentication, Model model) {
        addModel(authentication, model, null, 0);
        return "patient/results";
    }

    @GetMapping("/patient/support")
    public String support(Authentication authentication, Model model) {
        addModel(authentication, model, null, 0);
        return "patient/support";
    }

    // =========================================================================
    // HÀM DÙNG CHUNG (ĐÃ SỬA LỖI GHI ĐÈ)
    // =========================================================================

    private void addModel(Authentication authentication, Model model, String bodyPart, int page) {
        User patient = userAccountService.findByEmail(authentication.getName());
        model.addAttribute("currentUser", patient);

        // Tạm thời lấy size = 100 để hiển thị hết trên 1 trang (Commit sau sẽ làm phân trang)
        Page<MedicalRecord> recordPage = medicalRecordService.findPatientRecords(patient, bodyPart, page, 100);

        model.addAttribute("records", recordPage.getContent());
        model.addAttribute("bodyPart", bodyPart); // Giữ lại giá trị vùng chụp đang lọc

        model.addAttribute("latestRecord", imagingRecordService.findLatestForPatient(patient));
        model.addAttribute("recordCount", imagingRecordService.countForPatient(patient));
        model.addAttribute("unreadCount", notificationService.countUnread(patient));
    }

    // =========================================================================
    // LUỒNG 1: TẠO CA CHẨN ĐOÁN AI
    // =========================================================================

    @GetMapping("/patient/upload")
    public String showUploadForm(Authentication authentication, Model model) {
        addModel(authentication, model, null, 0);
        return "patient/upload";
    }

    @PostMapping("/patient/upload")
    public String handleSelfCheckUpload(@RequestParam("file") MultipartFile file,
                                        @RequestParam("bodyPart") String bodyPart,
                                        @RequestParam(value = "symptoms", required = false) String symptoms,
                                        Authentication authentication,
                                        RedirectAttributes redirectAttributes) {

        if (file == null || file.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Vui lòng chọn ảnh X-Quang để AI phân tích.");
            return "redirect:/patient/upload";
        }
        if (bodyPart == null || bodyPart.trim().isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Vui lòng chọn vùng xương cần chẩn đoán.");
            return "redirect:/patient/upload";
        }

        try {
            User currentUser = userAccountService.findByEmail(authentication.getName());
            medicalRecordService.createPatientSelfCheck(currentUser, bodyPart, symptoms, file);
            redirectAttributes.addFlashAttribute("successMessage", "Đã gửi hồ sơ thành công! Vui lòng chờ hệ thống AI xử lý.");
            return "redirect:/patient/records";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Lỗi hệ thống: " + e.getMessage());
            return "redirect:/patient/upload";
        }
    }

    // =========================================================================
    // LUỒNG 2: XEM CHI TIẾT, XÓA, VÀ GIẢ LẬP AI
    // =========================================================================

    @GetMapping("/patient/results/{id}")
    public String viewResultDetail(@PathVariable("id") Long id, Authentication authentication, Model model) {
        try {
            User currentUser = userAccountService.findByEmail(authentication.getName());
            MedicalRecord record = medicalRecordService.getRecordDetail(id, currentUser);

            model.addAttribute("record", record);
            model.addAttribute("xrayImage", record.getXrayImages().isEmpty() ? null : record.getXrayImages().get(0));
            addModel(authentication, model, null, 0);
            model.addAttribute("activeMenu", "records");
            return "patient/result-detail";
        } catch (Exception e) {
            return "redirect:/patient/records";
        }
    }

    @PostMapping("/patient/records/{id}/delete")
    public String deleteRecord(@PathVariable("id") Long id, Authentication authentication, RedirectAttributes redirectAttributes) {
        try {
            User currentUser = userAccountService.findByEmail(authentication.getName());
            medicalRecordService.deleteRecord(id, currentUser);
            redirectAttributes.addFlashAttribute("successMessage", "Đã xóa ca bệnh thành công!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/patient/records";
    }

    @PostMapping("/patient/records/{id}/mock-ai")
    public String runMockAi(@PathVariable("id") Long id, Authentication authentication, RedirectAttributes redirectAttributes) {
        try {
            User currentUser = userAccountService.findByEmail(authentication.getName());
            medicalRecordService.simulateAiProcessing(id, currentUser);
            redirectAttributes.addFlashAttribute("successMessage", "AI đã phân tích xong kết quả!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Lỗi giả lập AI.");
        }
        return "redirect:/patient/records";
    }
}