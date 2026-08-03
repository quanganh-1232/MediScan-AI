package com.example.mediscanauth.controller.doctor;

import com.example.mediscanauth.model.ImagingRecord;
import com.example.mediscanauth.model.Notification;
import com.example.mediscanauth.model.Patient;
import com.example.mediscanauth.model.User;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import com.example.mediscanauth.model.dto.DashboardDTO;
import com.example.mediscanauth.repository.PatientRepository;
import com.example.mediscanauth.repository.UserRepository;
import com.example.mediscanauth.service.CloudinaryService;
import com.example.mediscanauth.service.ImagingRecordService;
import com.example.mediscanauth.service.NotificationService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.util.List;
import java.util.stream.Collectors;

@Controller
public class DoctorDashboardController {

    private final CloudinaryService cloudinaryService;
    private final ImagingRecordService imagingRecordService;
    private final PatientRepository patientRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    // 2. Tiêm toàn bộ qua một Constructor duy nhất (Không dùng @Autowired rời rạc
    // nữa)
    public DoctorDashboardController(CloudinaryService cloudinaryService,
            ImagingRecordService imagingRecordService,
            PatientRepository patientRepository,
            UserRepository userRepository,
            NotificationService notificationService) {
        this.cloudinaryService = cloudinaryService;
        this.imagingRecordService = imagingRecordService;
        this.patientRepository = patientRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    // ==================== Các method cũ giữ nguyên ====================
    @GetMapping("/doctor/dashboard")
    public String dashboard() {
        return "redirect:/home";
    }

    @GetMapping("/doctor/overview")
    public String overview() {
        return "redirect:/home";
    }

    @GetMapping("/doctor/queue")
    public String queue() {
        return "redirect:/doctor/records/pending";
    }

    @GetMapping("/doctor/records/pending")
    public String pendingList(Model model, Principal principal) {
        Long doctorId = imagingRecordService.getDoctorIdByEmail(principal.getName());
        model.addAttribute("pendingRecords", imagingRecordService.findQueueForDoctor(doctorId));
        model.addAttribute("todayRecordCount", imagingRecordService.countTodayForDoctor(doctorId));
        model.addAttribute("totalRecordCount", imagingRecordService.countAllForDoctor(doctorId));
        model.addAttribute("libraryPreview", imagingRecordService.searchConfirmedLibrary(null, null, doctorId,
                PageRequest.of(0, 3, Sort.by(Sort.Order.desc("confirmedAt")))).getContent());
        return "doctor/pending-list";
    }

    @GetMapping("/doctor/records/{recordId}/review")
    public String reviewDetail(@PathVariable Long recordId, Model model,
                               Authentication authentication, RedirectAttributes redirectAttributes) {
        ImagingRecord record;
        try {
            record = imagingRecordService.getRecordForDoctor(recordId, authentication.getName());
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/doctor/records/pending";
        }

        String patientName = record.getPatient() != null ? record.getPatient().getFullName() : "Unknown_Patient";
        String recordCode = record.getRecordCode() != null ? record.getRecordCode() : "Unknown_Record";

        // URL hiển thị
        String displayUrl = cloudinaryService.getDisplayImageUrl(
                record.getFileName(), record.getStatus(), patientName, recordCode);

        String originalUrl = cloudinaryService.getOriginalImageUrl(
                record.getFileName(), patientName, recordCode);

        model.addAttribute("record", record);
        model.addAttribute("displayImageUrl", displayUrl);
        model.addAttribute("originalImageUrl", originalUrl);
        model.addAttribute("aiRegions", imagingRecordService.getAiRegionsByRecordId(recordId));
        model.addAttribute("visibility", record.getVisibility()); // Truyền visibility ra view

        // Nếu đã COMPLETED → đảm bảo đổ đầy thông tin bác sĩ
        if ("COMPLETED".equals(record.getStatus())) {
            if (record.getDoctor() != null) {
                model.addAttribute("doctorName", record.getDoctor().getFullName());
            }
            if (record.getDoctorConclusion() != null) {
                model.addAttribute("doctorConclusion", record.getDoctorConclusion());
            }
        }

        patientRepository.findByUser(record.getPatient())
                .ifPresent(p -> model.addAttribute("profile", p));

        return "doctor/review-detail";
    }

    @PostMapping("/doctor/records/{recordId}/conclusion")
    public String saveConclusion(Authentication authentication,
            @PathVariable Long recordId,
            @RequestParam(required = false) String conclusion,
            @RequestParam(defaultValue = "PRIVATE") String visibility,
            @RequestParam(required = false) String screenshotData, // <--- Nhận dữ liệu ảnh chụp màn hình ở đây
            @RequestParam(required = false) Integer bboxX,
            @RequestParam(required = false) Integer bboxY,
            @RequestParam(required = false) Integer bboxWidth,
            @RequestParam(required = false) Integer bboxHeight,
            RedirectAttributes redirectAttributes) {

        try {
            // Nếu bác sĩ có vẽ hoặc cập nhật tọa độ box
            if (bboxX != null || bboxY != null || bboxWidth != null || bboxHeight != null) {
                imagingRecordService.updateRecordCoordinates(recordId, bboxX, bboxY, bboxWidth, bboxHeight);
            }

            // Truyền screenshotData vào hàm confirmDoctorReview
            imagingRecordService.confirmDoctorReview(recordId, authentication.getName(), conclusion, null, screenshotData, visibility);
            redirectAttributes.addFlashAttribute("diagnosisSuccess", true);
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/doctor/records/pending";
    }

    @PostMapping("/doctor/records/reject")
    public String reject(Authentication authentication,
            @RequestParam Long recordId,
            @RequestParam(required = false) String conclusion,
            @RequestParam(required = false) String recommendation,
            RedirectAttributes redirectAttributes) {
        try {
            imagingRecordService.rejectDoctorReview(recordId, authentication.getName(), conclusion, recommendation);
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/doctor/records/pending";
    }

    @GetMapping("/doctor/patients")
    public String listPatients(Model model, Principal principal) {
        Long doctorId = imagingRecordService.getDoctorIdByEmail(principal.getName());
        List<Patient> patients = imagingRecordService.getPatientsForDoctor(doctorId);
        model.addAttribute("patients", patients);
        return "doctor/patient-list";
    }

    @GetMapping("/doctor/patients/{id}")
    public String patientDetail(@PathVariable Long id, Model model, Principal principal) {
        Long doctorId = imagingRecordService.getDoctorIdByEmail(principal.getName());
        Patient patient = imagingRecordService.getPatientById(id);
        List<ImagingRecord> records = imagingRecordService.findForPatientAndDoctor(patient.getUser(), doctorId);
        model.addAttribute("profile", patient);
        model.addAttribute("records", records);
        return "doctor/patient-profile-detail";
    }

    // ==================== LIBRARY ====================
    // Thư viện chẩn đoán là kho dùng chung: mọi bác sĩ đều xem được toàn bộ hồ
    // sơ đã hoàn thành (COMPLETED), khác với hàng chờ đọc ảnh/danh sách bệnh
    // nhân vốn chỉ giới hạn theo bác sĩ được phân công.
    @GetMapping("/doctor/library")
    public String library(@RequestParam(required = false) String q,
            @RequestParam(required = false) String bodyPart,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "8") int size,
            Model model) {

        List<DashboardDTO.QueueItemDTO> completedList = imagingRecordService.getAllCompletedDTOs();

        // Filter client-side
        if (q != null && !q.trim().isEmpty()) {
            String keyword = q.toLowerCase().trim();
            completedList = completedList.stream()
                    .filter(r -> (r.getRecordCode() != null && r.getRecordCode().toLowerCase().contains(keyword)) ||
                            (r.getPatient() != null && r.getPatient().getFullName() != null &&
                                    r.getPatient().getFullName().toLowerCase().contains(keyword))
                            ||
                            (r.getBodyPart() != null && r.getBodyPart().toLowerCase().contains(keyword)) ||
                            (r.getAiPrediction() != null && r.getAiPrediction().toLowerCase().contains(keyword)))
                    .collect(Collectors.toList());
        }

        if (bodyPart != null && !bodyPart.isEmpty()) {
            completedList = completedList.stream()
                    .filter(r -> bodyPart.equals(r.getBodyPart()))
                    .collect(Collectors.toList());
        }

        model.addAttribute("completedRecords", completedList);
        model.addAttribute("completedCount", completedList.size());
        model.addAttribute("q", q == null ? "" : q);
        model.addAttribute("bodyPart", bodyPart == null ? "" : bodyPart);

        model.addAttribute("bodyPartFilters", java.util.List.of(
                "Cẳng tay", "Cổ tay", "Bàn tay", "Cẳng chân",
                "Cổ chân", "Bàn chân", "Xương sườn", "Vai",
                "Khuỷu tay", "Đầu gối"));

        return "doctor/library";
    }

    @GetMapping("/doctor/record/{recordId}")
    public String recordDetail(@PathVariable Long recordId, Model model,
                               Authentication authentication, RedirectAttributes redirectAttributes) {
        try {
            model.addAttribute("record", imagingRecordService.getRecordForDoctor(recordId, authentication.getName()));
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/doctor/records/pending";
        }
        return "doctor/record-detail";
    }

    @PostMapping("/doctor/record/{recordId}/visibility")
    public String updateRecordVisibility(@PathVariable Long recordId,
                                         @RequestParam String visibility,
                                         Authentication authentication,
                                         RedirectAttributes redirectAttributes) {
        try {
            imagingRecordService.updateRecordVisibility(recordId, authentication.getName(), visibility);
            redirectAttributes.addFlashAttribute("success", "PUBLIC".equals(visibility)
                    ? "Đã công khai kết quả với bệnh nhân."
                    : "Đã chuyển kết quả về chế độ riêng tư.");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/doctor/record/" + recordId;
    }

    // ==================== NOTIFICATIONS ====================
    @GetMapping("/doctor/notifications")
    public String notifications(Model model, Principal principal) {
        String email = principal.getName();
        Long doctorId = imagingRecordService.getDoctorIdByEmail(email);

        if (doctorId != null) {
            User currentUser = userRepository.findById(doctorId).orElse(null);

            if (currentUser != null) {
                List<Notification> notifications = notificationService.findForUser(currentUser);
                long unreadCount = notificationService.countUnread(currentUser);

                model.addAttribute("notifications", notifications);
                model.addAttribute("unreadCount", unreadCount);
                return "common/notifications";
            }
        }

        // Fallback
        model.addAttribute("notifications", List.of());
        model.addAttribute("unreadCount", 0);
        return "common/notifications";
    }
}