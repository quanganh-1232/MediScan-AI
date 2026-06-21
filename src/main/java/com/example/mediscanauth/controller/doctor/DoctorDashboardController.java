package com.example.mediscanauth.controller.doctor;

import com.example.mediscanauth.model.ImagingRecord;
import com.example.mediscanauth.model.Patient;
import com.example.mediscanauth.model.dto.DashboardDTO;
import com.example.mediscanauth.service.ImagingRecordService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.security.Principal;
import java.util.List;

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

    @GetMapping("/doctor/records/pending")
    public String pendingRecords(Model model, Principal principal) {
        String email = principal.getName();
        Long doctorId = imagingRecordService.getDoctorIdByEmail(email);

        List<DashboardDTO.QueueItemDTO> pendingList = imagingRecordService.getPendingDTOsForDoctor(doctorId);

        model.addAttribute("pendingRecords", pendingList);
        model.addAttribute("queueCount", pendingList.size());

        return "doctor/pending-list";
    }

    @GetMapping("/doctor/records/{id}/review")
    public String reviewDetail(@PathVariable Long id, Model model) {
        ImagingRecord record = imagingRecordService.getRecordDetail(id);
        Patient patientProfile = imagingRecordService.getPatientProfile(record.getPatient());

        // LẤY DỮ LIỆU TOẠ ĐỘ AI TỪ DATABASE
        List<com.example.mediscanauth.model.dto.AiRegionProjection> aiRegions = imagingRecordService
                .getAiRegionsByRecordId(id);

        model.addAttribute("record", record);
        model.addAttribute("profile", patientProfile);
        model.addAttribute("aiRegions", aiRegions); // Đẩy sang giao diện Thymeleaf

        return "doctor/review-detail";
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

    @GetMapping("/doctor/records/completed")
    public String completedRecords(Model model, Principal principal) {
        String email = principal.getName();
        Long doctorId = imagingRecordService.getDoctorIdByEmail(email);

        List<DashboardDTO.QueueItemDTO> completedList = imagingRecordService.getCompletedDTOsForDoctor(doctorId);

        model.addAttribute("completedRecords", completedList);
        model.addAttribute("completedCount", completedList.size());

        return "doctor/completed-list";
    }

    private void addModel(Model model) {
        model.addAttribute("queueRecords", imagingRecordService.findQueue());
        model.addAttribute("queueCount", imagingRecordService.countQueue());
    }
}
