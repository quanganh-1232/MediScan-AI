package com.example.mediscanauth.controller.doctor;

import com.example.mediscanauth.service.ImagingRecordService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DoctorDashboardController {

    private final ImagingRecordService imagingRecordService;

    public DoctorDashboardController(ImagingRecordService imagingRecordService) {
        this.imagingRecordService = imagingRecordService;
    }

    @GetMapping("/doctor/dashboard")
    public String dashboard(Model model) {
        addModel(model);
        return "doctor/dashboard";
    }

    @GetMapping("/doctor/overview")
    public String overview(Model model) {
        addModel(model);
        return "doctor/dashboard";
    }

    @GetMapping("/doctor/queue")
    public String queue(Model model) {
        addModel(model);
        return "doctor/queue";
    }

    private void addModel(Model model) {
        model.addAttribute("queueRecords", imagingRecordService.findQueue());
        model.addAttribute("queueCount", imagingRecordService.countQueue());
    }
}
