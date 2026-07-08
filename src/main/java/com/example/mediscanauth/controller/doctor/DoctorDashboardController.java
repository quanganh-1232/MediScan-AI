package com.example.mediscanauth.controller.doctor;

import com.example.mediscanauth.model.ImagingRecord;
import com.example.mediscanauth.repository.PatientRepository;
import com.example.mediscanauth.service.ImagingRecordService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class DoctorDashboardController {

    private final ImagingRecordService imagingRecordService;
    private final PatientRepository patientRepository;

    public DoctorDashboardController(ImagingRecordService imagingRecordService,
                                     PatientRepository patientRepository) {
        this.imagingRecordService = imagingRecordService;
        this.patientRepository = patientRepository;
    }

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
        if (bboxX != null || bboxY != null || bboxWidth != null || bboxHeight != null) {
            imagingRecordService.updateRecordCoordinates(recordId, bboxX, bboxY, bboxWidth, bboxHeight);
        }
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

    @GetMapping("/doctor/library")
    public String library(@RequestParam(required = false) String q,
                          @RequestParam(required = false) String bodyPart,
                          @RequestParam(defaultValue = "0") int page,
                          @RequestParam(defaultValue = "8") int size,
                          Model model) {
        Sort sort = Sort.by(
                new Sort.Order(Sort.Direction.DESC, "confirmedAt", Sort.NullHandling.NULLS_LAST),
                Sort.Order.desc("createdAt")
        );
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.max(size, 1), sort);
        model.addAttribute("recordsPage", imagingRecordService.searchConfirmedLibrary(q, bodyPart, pageable));
        model.addAttribute("q", q == null ? "" : q);
        model.addAttribute("bodyPart", bodyPart == null ? "" : bodyPart);
        model.addAttribute("bodyPartFilters", java.util.List.of("Cẳng tay", "Cổ tay", "Bàn tay", "Cẳng chân", "Cổ chân", "Bàn chân", "Xương sườn", "Vai", "Khuỷu tay", "Đầu gối"));
        return "doctor/library";
    }

    @GetMapping("/doctor/record/{recordId}")
    public String recordDetail(@PathVariable Long recordId, Model model) {
        model.addAttribute("record", imagingRecordService.getRecordById(recordId));
        return "doctor/record-detail";
    }
}
