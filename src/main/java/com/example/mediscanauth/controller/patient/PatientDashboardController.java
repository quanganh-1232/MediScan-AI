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

    @GetMapping("/patient/dashboard")
    public String dashboard(Authentication authentication, Model model) {
        addModel(authentication, model);
        // Báo cho giao diện biết đang ở tab Tổng quan
        model.addAttribute("activeMenu", "overview");
        return "patient/dashboard";
    }

    @GetMapping("/patient/overview")
    public String overview(Authentication authentication, Model model) {
        addModel(authentication, model);
        return "patient/dashboard";
    }

    @GetMapping("/patient/records")
    public String records(Authentication authentication, Model model) {
        addModel(authentication, model);
        // Báo cho giao diện biết đang ở tab Hồ sơ
        model.addAttribute("activeMenu", "records");
        return "patient/dashboard";
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

    // =========================================================================
    // LUỒNG 1: TẠO CA CHẨN ĐOÁN AI (CREATE SELF-CHECK CASE)
    // =========================================================================

    // 1. Hiển thị form tải ảnh
    @GetMapping("/patient/upload")
    public String showUploadForm(Authentication authentication, Model model) {
        addModel(authentication, model);
        return "patient/upload";
    }

    // 2. Tiếp nhận ảnh, Validate và Gửi yêu cầu
    @PostMapping("/patient/upload")
    public String handleSelfCheckUpload(@RequestParam("file") MultipartFile file,
                                        @RequestParam("bodyPart") String bodyPart,
                                        @RequestParam(value = "symptoms", required = false) String symptoms,
                                        Authentication authentication,
                                        RedirectAttributes redirectAttributes) {

        // Khối: Xác thực dữ liệu đầu vào (Validate)
        if (file == null || file.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Vui lòng chọn ảnh X-Quang để AI phân tích.");
            return "redirect:/patient/upload";
        }
        if (bodyPart == null || bodyPart.trim().isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Vui lòng chọn vùng xương cần chẩn đoán.");
            return "redirect:/patient/upload";
        }

        try {
            // Lấy thông tin user hiện tại
            User currentUser = userAccountService.findByEmail(authentication.getName());

            // Gọi Service để lưu ảnh và tạo Database record
            medicalRecordService.createPatientSelfCheck(currentUser, bodyPart, symptoms, file);

            // Thành công: Quay về trang lịch sử
            redirectAttributes.addFlashAttribute("successMessage", "Đã gửi hồ sơ thành công! Vui lòng chờ hệ thống AI xử lý.");
            return "redirect:/patient/records";

        } catch (Exception e) {
            // Khối: Hiển thị lỗi xác thực/lỗi hệ thống
            redirectAttributes.addFlashAttribute("errorMessage", "Lỗi hệ thống: " + e.getMessage());
            return "redirect:/patient/upload";
        }
    }

    private void addModel(Authentication authentication, Model model) {
        User patient = userAccountService.findByEmail(authentication.getName());
        model.addAttribute("currentUser", patient);
        model.addAttribute("records", medicalRecordService.findPatientRecords(patient));
        model.addAttribute("latestRecord", imagingRecordService.findLatestForPatient(patient));
        model.addAttribute("recordCount", imagingRecordService.countForPatient(patient));
        model.addAttribute("unreadCount", notificationService.countUnread(patient));
    }
    // =========================================================================
    // LUỒNG 2: XEM CHI TIẾT, XÓA, VÀ GIẢ LẬP AI
    // =========================================================================

    // 1. Xem chi tiết kết quả
    @GetMapping("/patient/results/{id}")
    public String viewResultDetail(@PathVariable("id") Long id, Authentication authentication, Model model) {
        try {
            User currentUser = userAccountService.findByEmail(authentication.getName());
            MedicalRecord record = medicalRecordService.getRecordDetail(id, currentUser);

            // Lấy ảnh Xray (giả sử ca bệnh có 1 ảnh đầu tiên)
            model.addAttribute("record", record);
            model.addAttribute("xrayImage", record.getXrayImages().isEmpty() ? null : record.getXrayImages().get(0));
            addModel(authentication, model);
            model.addAttribute("activeMenu", "records");
            return "patient/result-detail";
        } catch (Exception e) {
            return "redirect:/patient/records";
        }
    }

    // 2. Xóa hồ sơ
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

    // 3. Giả lập AI chạy (dành cho demo)
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