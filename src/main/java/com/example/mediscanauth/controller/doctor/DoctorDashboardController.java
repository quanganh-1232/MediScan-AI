package com.example.mediscanauth.controller.doctor;

import com.example.mediscanauth.model.ImagingRecord;
import com.example.mediscanauth.model.Patient;
import com.example.mediscanauth.model.dto.DashboardDTO;
import com.example.mediscanauth.service.ImagingRecordService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class DoctorDashboardController {

    private final ImagingRecordService imagingRecordService;

    public DoctorDashboardController(ImagingRecordService imagingRecordService) {
        this.imagingRecordService = imagingRecordService;
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
    public String queue(Model model, Principal principal) {
        Long doctorId = imagingRecordService.getDoctorIdByEmail(principal.getName());

        DashboardDTO stats = imagingRecordService.getDoctorDashboardStats(doctorId);

        model.addAttribute("queueRecords", stats.getQueueRecords());
        model.addAttribute("queueCount", stats.getQueueCount());
        return "doctor/queue";
    }

    @PostMapping("/doctor/records/confirm")
    public String confirm(Authentication authentication,
                          @RequestParam Long recordId,
                          @RequestParam(required = false) String conclusion,
                          @RequestParam(required = false) String recommendation) {
        imagingRecordService.confirmDoctorReview(recordId, authentication.getName(), conclusion, recommendation);
        return "redirect:/doctor/library";
    }

    @PostMapping("/doctor/records/reject")
    public String reject(Authentication authentication,
                         @RequestParam Long recordId,
                         @RequestParam(required = false) String conclusion,
                         @RequestParam(required = false) String recommendation) {
        imagingRecordService.rejectDoctorReview(recordId, authentication.getName(), conclusion, recommendation);
        return "redirect:/doctor/queue";
    }

    @GetMapping("/doctor/library")
    public String library(@RequestParam(required = false) String q,
                          @RequestParam(required = false) String bodyPart,
                          @RequestParam(defaultValue = "0") int page,
                          @RequestParam(defaultValue = "8") int size,
                          Model model) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.max(size, 1));
        model.addAttribute("recordsPage", imagingRecordService.searchConfirmedLibrary(q, bodyPart, pageable));
        model.addAttribute("q", q == null ? "" : q);
        model.addAttribute("bodyPart", bodyPart == null ? "" : bodyPart);
        model.addAttribute("bodyPartFilters", java.util.List.of("Cẳng tay", "Cổ tay", "Bàn tay", "Cẳng chân", "Cổ chân", "Bàn chân", "Xương sườn", "Vai", "Khuỷu tay", "Đầu gối"));
        addModel(model);
        return "doctor/library";
    }

    @GetMapping("/doctor/record/{recordId}")
    public String recordDetail(@PathVariable Long recordId, Model model) {
        model.addAttribute("record", imagingRecordService.getRecordById(recordId));
        addModel(model);
        return "doctor/record-detail";
    }

    @PostMapping("/doctor/record/{recordId}/coordinates")
    public String saveRecordCoordinates(@PathVariable Long recordId,
                                        @RequestParam(required = false) Integer bboxX,
                                        @RequestParam(required = false) Integer bboxY,
                                        @RequestParam(required = false) Integer bboxWidth,
                                        @RequestParam(required = false) Integer bboxHeight) {
        imagingRecordService.updateRecordCoordinates(recordId, bboxX, bboxY, bboxWidth, bboxHeight);
        return "redirect:/doctor/record/" + recordId;
    }

    private void addModel(Model model) {
        model.addAttribute("queueRecords", imagingRecordService.findQueue());
        model.addAttribute("queueCount", imagingRecordService.countQueue());
    }
}
