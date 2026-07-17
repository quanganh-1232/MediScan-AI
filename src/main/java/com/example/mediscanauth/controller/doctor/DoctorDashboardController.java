package com.example.mediscanauth.controller.doctor;

import com.example.mediscanauth.model.ImagingRecord;
import com.example.mediscanauth.model.Notification;
import com.example.mediscanauth.model.Patient;
import com.example.mediscanauth.model.User;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import com.example.mediscanauth.model.dto.DashboardDTO;
import com.example.mediscanauth.repository.PatientRepository;
import com.example.mediscanauth.repository.UserRepository;
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

    private final ImagingRecordService imagingRecordService;
    private final PatientRepository patientRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public DoctorDashboardController(ImagingRecordService imagingRecordService,
            PatientRepository patientRepository,
            UserRepository userRepository,
            NotificationService notificationService) {
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
    public String pendingList(Model model) {
        model.addAttribute("pendingRecords", imagingRecordService.findQueue());
        model.addAttribute("todayRecordCount", imagingRecordService.countToday());
        model.addAttribute("totalRecordCount", imagingRecordService.countAll());
        model.addAttribute("libraryPreview", imagingRecordService.searchConfirmedLibrary(null, null,
                PageRequest.of(0, 3, Sort.by(Sort.Order.desc("confirmedAt")))).getContent());
        return "doctor/pending-list";
    }

    @GetMapping("/doctor/records/{recordId}/review")
    public String reviewDetail(@PathVariable Long recordId, Model model) {
        ImagingRecord record = imagingRecordService.getRecordById(recordId);
        model.addAttribute("record", record);
        patientRepository.findByUser(record.getPatient())
                .ifPresent(profile -> model.addAttribute("profile", profile));
        return "doctor/review-detail";
    }

    @PostMapping("/doctor/records/{recordId}/conclusion")
    public String saveConclusion(Authentication authentication,
            @PathVariable Long recordId,
            @RequestParam(required = false) String conclusion,
            @RequestParam(required = false) Integer bboxX,
            @RequestParam(required = false) Integer bboxY,
            @RequestParam(required = false) Integer bboxWidth,
            @RequestParam(required = false) Integer bboxHeight,
            RedirectAttributes redirectAttributes) {
            
        // Nếu bác sĩ có vẽ hoặc cập nhật tọa độ box
        if (bboxX != null || bboxY != null || bboxWidth != null || bboxHeight != null) {
            imagingRecordService.updateRecordCoordinates(recordId, bboxX, bboxY, bboxWidth, bboxHeight);
        }
        
        // Gọi service xử lý: service sẽ tự động lấy ảnh local từ tên file trong DB -> đẩy lên Cloudinary -> đổi sang Public ID
        imagingRecordService.confirmDoctorReview(recordId, authentication.getName(), conclusion, null);
        
        redirectAttributes.addFlashAttribute("diagnosisSuccess", true);
        return "redirect:/doctor/records/pending";
    }

    @PostMapping("/doctor/records/reject")
    public String reject(Authentication authentication,
            @RequestParam Long recordId,
            @RequestParam(required = false) String conclusion,
            @RequestParam(required = false) String recommendation) {
        imagingRecordService.rejectDoctorReview(recordId, authentication.getName(), conclusion, recommendation);
        return "redirect:/doctor/records/pending";
    }

    @GetMapping("/doctor/patients")
    public String listPatients(Model model) {
        List<Patient> patients = imagingRecordService.getAllPatients();
        model.addAttribute("patients", patients);
        return "doctor/patient-list";
    }

    @GetMapping("/doctor/patients/{id}")
    public String patientDetail(@PathVariable Long id, Model model) {
        Patient patient = imagingRecordService.getPatientById(id);
        List<ImagingRecord> records = imagingRecordService.findForPatient(patient.getUser());
        model.addAttribute("profile", patient);
        model.addAttribute("records", records);
        return "doctor/patient-profile-detail";
    }

    // ==================== LIBRARY ====================
    @GetMapping("/doctor/library")
    public String library(@RequestParam(required = false) String q,
            @RequestParam(required = false) String bodyPart,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "8") int size,
            Model model,
            Principal principal) {

        String email = principal.getName();
        Long doctorId = imagingRecordService.getDoctorIdByEmail(email);

        List<DashboardDTO.QueueItemDTO> completedList = imagingRecordService.getCompletedDTOsForDoctor(doctorId);

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
    public String recordDetail(@PathVariable Long recordId, Model model) {
        model.addAttribute("record", imagingRecordService.getRecordById(recordId));
        return "doctor/record-detail";
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