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

        model.addAttribute("record", record);
        model.addAttribute("profile", patientProfile); // Gửi thêm đối tượng profile

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

    private void addModel(Model model) {
        model.addAttribute("queueRecords", imagingRecordService.findQueue());
        model.addAttribute("queueCount", imagingRecordService.countQueue());
    }
}
