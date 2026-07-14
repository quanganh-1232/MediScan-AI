package com.example.mediscanauth.controller.patient;

import com.example.mediscanauth.model.ImagingRecord;
import com.example.mediscanauth.model.Notification;
import com.example.mediscanauth.model.Patient;
import com.example.mediscanauth.model.User;
import com.example.mediscanauth.repository.AppointmentRepository;
import com.example.mediscanauth.repository.NotificationRepository;
import com.example.mediscanauth.repository.PatientRepository;
import com.example.mediscanauth.service.ImagingRecordService;
import com.example.mediscanauth.service.NotificationService;
import com.example.mediscanauth.service.PatientWorkflowService;
import com.example.mediscanauth.service.UserAccountService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class PatientDashboardController {

    private final UserAccountService userAccountService;
    private final ImagingRecordService imagingRecordService;
    private final NotificationService notificationService;
    private final PatientWorkflowService patientWorkflowService;
    private final PatientRepository patientRepository;
    private final AppointmentRepository appointmentRepository;
    private final NotificationRepository notificationRepository;

    public PatientDashboardController(UserAccountService userAccountService,
                                      ImagingRecordService imagingRecordService,
                                      NotificationService notificationService,
                                      PatientWorkflowService patientWorkflowService,
                                      PatientRepository patientRepository,
                                      AppointmentRepository appointmentRepository,
                                      NotificationRepository notificationRepository) {
        this.userAccountService = userAccountService;
        this.imagingRecordService = imagingRecordService;
        this.notificationService = notificationService;
        this.patientWorkflowService = patientWorkflowService;
        this.patientRepository = patientRepository;
        this.appointmentRepository = appointmentRepository;
        this.notificationRepository = notificationRepository;
    }

    // ── KAN-28: Patient Dashboard ──────────────────────────────────────────
    @GetMapping("/patient/dashboard")
    public String dashboard(Authentication authentication, Model model) {
        User patient = getUser(authentication);
        List<ImagingRecord> records = imagingRecordService.findForPatient(patient);

        long total       = records.size();
        long processing  = records.stream().filter(r -> r.getStatus().contains("PENDING") || r.getStatus().contains("PROCESSING")).count();
        long completed   = records.stream().filter(r -> "DOCTOR_CONFIRMED".equals(r.getStatus())).count();
        long needAttention = records.stream().filter(r -> "DOCTOR_REJECTED".equals(r.getStatus()) || "AI_FAILED".equals(r.getStatus())).count();

        model.addAttribute("currentUser", patient);
        model.addAttribute("unreadCount", notificationService.countUnread(patient));
        model.addAttribute("records", records);
        model.addAttribute("latestRecord", imagingRecordService.findLatestForPatient(patient));
        model.addAttribute("recordCount", total);
        model.addAttribute("processingCount", processing);
        model.addAttribute("completedCount", completed);
        model.addAttribute("needAttentionCount", needAttention);
        return "patient/dashboard";
    }

    @GetMapping("/patient/overview")
    public String overview(Authentication authentication, Model model) {
        return dashboard(authentication, model);
    }

    // ── KAN-30: Patient Records + Filter ──────────────────────────────────
    @GetMapping("/patient/records")
    public String records(Authentication authentication,
                          Model model,
                          @RequestParam(defaultValue = "") String keyword,
                          @RequestParam(defaultValue = "") String bodyPart,
                          @RequestParam(defaultValue = "") String fromDate,
                          @RequestParam(defaultValue = "") String toDate,
                          @RequestParam(defaultValue = "0") int page) {

        User patient = getUser(authentication);
        boolean hasFilter = !keyword.isBlank() || !bodyPart.isBlank() || !fromDate.isBlank() || !toDate.isBlank();

        Page<ImagingRecord> recordPage = imagingRecordService.searchForPatient(
                patient,
                keyword.isBlank() ? null : keyword,
                bodyPart.isBlank() ? null : bodyPart,
                PageRequest.of(page, 10, Sort.by(Sort.Direction.DESC, "capturedAt"))
        );

        model.addAttribute("currentUser", patient);
        model.addAttribute("unreadCount", notificationService.countUnread(patient));
        model.addAttribute("records", recordPage.getContent());
        model.addAttribute("recordPage", recordPage);
        model.addAttribute("latestRecord", imagingRecordService.findLatestForPatient(patient));
        model.addAttribute("recordCount", imagingRecordService.countForPatient(patient));
        model.addAttribute("keyword", keyword);
        model.addAttribute("bodyPart", bodyPart);
        model.addAttribute("fromDate", fromDate);
        model.addAttribute("toDate", toDate);
        model.addAttribute("currentPage", page);
        model.addAttribute("hasFilter", hasFilter);
        return "patient/records";
    }

    // ── KAN-31: Patient Record Detail ──────────────────────────────────────
    @GetMapping("/patient/records/{id}")
    public String recordDetail(@PathVariable Long id,
                               Authentication authentication,
                               Model model) {
        User patient = getUser(authentication);
        ImagingRecord record = imagingRecordService.getRecordById(id);

        // Ownership check — patient can only view their own records
        if (record == null || !record.getPatient().getUserId().equals(patient.getUserId())) {
            return "redirect:/patient/records";
        }

        model.addAttribute("currentUser", patient);
        model.addAttribute("unreadCount", notificationService.countUnread(patient));
        model.addAttribute("record", record);
        return "patient/record-detail";
    }

    // Patient self-upload page disabled — only technicians upload X-rays.
    // POST /patient/upload still active for chatbot image analysis.
    @GetMapping("/patient/upload")
    public String uploadForm() {
        return "redirect:/home";
    }

    @PostMapping("/patient/upload")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> uploadXray(
            Authentication authentication,
            @RequestParam("bodyPart") String bodyPart,
            @RequestParam("xrayImage") MultipartFile file) {

        Map<String, Object> result = new HashMap<>();
        if (file.isEmpty()) {
            result.put("success", false);
            result.put("message", "Vui lòng chọn file ảnh.");
            return ResponseEntity.badRequest().body(result);
        }
        try {
            ImagingRecord record = patientWorkflowService.uploadImageAndAnalyze(
                    authentication.getName(), bodyPart, file);
            result.put("success", true);
            result.put("message", "Phân tích AI hoàn tất!");
            result.put("recordId", record.getRecordId());
            result.put("recordCode", record.getRecordCode());
            result.put("riskLevel", record.getRiskLevel());
            result.put("aiConfidence", record.getAiConfidence());
            result.put("aiPrediction", record.getAiPrediction());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "Lỗi xử lý: " + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    @GetMapping("/patient/results")
    public String results(Authentication authentication, Model model) {
        addBaseModel(authentication, model);
        return "patient/results";
    }

    @PostMapping("/patient/book-appointment")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> bookAppointment(
            Authentication authentication,
            @RequestParam(required = false) Long doctorId,
            @RequestParam String date,
            @RequestParam String time) {
        Map<String, Object> result = new HashMap<>();
        try {
            com.example.mediscanauth.model.Appointment appointment = 
                patientWorkflowService.bookAppointment(authentication.getName(), doctorId, date, time);
            result.put("success", true);
            result.put("message", "Đặt lịch khám thành công!");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "Lỗi: " + e.getMessage());
            return ResponseEntity.badRequest().body(result);
        }
    }

    @GetMapping("/patient/support")
    public String support(Authentication authentication, Model model) {
        addBaseModel(authentication, model);
        return "patient/support";
    }

    // ── KAN-36: Patient Profile ─────────────────────────────────────────────
    @GetMapping("/patient/profile")
    public String profile(Authentication authentication, Model model) {
        User user = getUser(authentication);
        model.addAttribute("currentUser", user);
        model.addAttribute("unreadCount", notificationService.countUnread(user));
        patientRepository.findByUser(user).ifPresent(p -> model.addAttribute("profile", p));
        return "patient/profile";
    }

    @PostMapping("/patient/profile")
    public String updateProfile(Authentication authentication,
                                @RequestParam String fullName,
                                @RequestParam(required = false) String phone,
                                @RequestParam(required = false) String gender,
                                @RequestParam(required = false)
                                @DateTimeFormat(pattern = "yyyy-MM-dd") java.time.LocalDate dateOfBirth,
                                @RequestParam(required = false) String address,
                                @RequestParam(required = false) String medicalHistory,
                                RedirectAttributes redirectAttributes) {
        try {
            patientWorkflowService.updatePatientProfile(
                    authentication.getName(), fullName, phone, gender, dateOfBirth, address, medicalHistory);
            redirectAttributes.addFlashAttribute("success", "Cập nhật thông tin thành công!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Lỗi: " + e.getMessage());
        }
        return "redirect:/patient/profile";
    }

    // ── KAN-37: Cancel appointment ──────────────────────────────────────────
    @PostMapping("/patient/appointments/{id}/cancel")
    @ResponseBody
    public ResponseEntity<java.util.Map<String, Object>> cancelAppointment(
            Authentication authentication,
            @PathVariable Long id) {
        java.util.Map<String, Object> result = new java.util.HashMap<>();
        try {
            patientWorkflowService.cancelAppointment(authentication.getName(), id);
            result.put("success", true);
            result.put("message", "Đã hủy lịch hẹn thành công.");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(result);
        }
    }

    // ── KAN-38: Notifications page ──────────────────────────────────────────
    @GetMapping("/patient/notifications")
    public String notifications(Authentication authentication, Model model) {
        User user = getUser(authentication);
        model.addAttribute("currentUser", user);
        model.addAttribute("unreadCount", notificationService.countUnread(user));
        model.addAttribute("notifications", notificationService.findForUser(user));
        return "patient/notifications";
    }

    @PostMapping("/patient/notifications/{id}/read")
    @ResponseBody
    public ResponseEntity<java.util.Map<String, Object>> markNotificationRead(
            @PathVariable Long id) {
        java.util.Map<String, Object> result = new java.util.HashMap<>();
        try {
            notificationService.markAsRead(id);
            result.put("success", true);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("success", false);
            return ResponseEntity.badRequest().body(result);
        }
    }

    @PostMapping("/patient/notifications/read-all")
    @ResponseBody
    public ResponseEntity<java.util.Map<String, Object>> markAllRead(Authentication authentication) {
        User user = getUser(authentication);
        notificationRepository.findByUserOrderByCreatedAtDesc(user)
                .stream().filter(n -> !n.isRead())
                .forEach(n -> { n.setRead(true); notificationRepository.save(n); });
        java.util.Map<String, Object> result = new java.util.HashMap<>();
        result.put("success", true);
        return ResponseEntity.ok(result);
    }

    // ── Helpers ────────────────────────────────────────────────────────────
    private User getUser(Authentication authentication) {
        return userAccountService.findByEmail(authentication.getName());
    }

    private void addBaseModel(Authentication authentication, Model model) {
        User patient = getUser(authentication);
        model.addAttribute("currentUser", patient);
        model.addAttribute("records", imagingRecordService.findForPatient(patient));
        model.addAttribute("latestRecord", imagingRecordService.findLatestForPatient(patient));
        model.addAttribute("recordCount", imagingRecordService.countForPatient(patient));
        model.addAttribute("unreadCount", notificationService.countUnread(patient));
    }
}
