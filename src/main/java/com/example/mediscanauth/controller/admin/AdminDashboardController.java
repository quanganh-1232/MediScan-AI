package com.example.mediscanauth.controller.admin;

import com.example.mediscanauth.repository.UserRepository;
import com.example.mediscanauth.service.ImagingRecordService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AdminDashboardController {

    private final UserRepository userRepository;
    private final ImagingRecordService imagingRecordService;

    public AdminDashboardController(UserRepository userRepository,
                                    ImagingRecordService imagingRecordService) {
        this.userRepository = userRepository;
        this.imagingRecordService = imagingRecordService;
    }

    @GetMapping("/admin/dashboard")
    public String dashboard() {
        return "redirect:/home";
    }

    @GetMapping("/admin/overview")
    public String overview() {
        return "redirect:/home";
    }

    @GetMapping("/admin/metrics")
    public String metrics(Model model) {
        addSharedModel(model);
        return "admin/metrics";
    }

    @GetMapping("/admin/users")
    public String users(Model model) {
        addSharedModel(model);
        return "admin/users";
    }

    @GetMapping("/admin/recent-records")
    public String recentRecords(Model model) {
        addSharedModel(model);
        return "admin/recent-records";
    }

    private void addSharedModel(Model model) {
        model.addAttribute("users", userRepository.findAll());
        model.addAttribute("activeUserCount", userRepository.countByStatus("ACTIVE"));
        model.addAttribute("queueCount", imagingRecordService.countQueue());
        model.addAttribute("todayRecordCount", imagingRecordService.countToday());
        model.addAttribute("totalRecordCount", imagingRecordService.countAll());
        model.addAttribute("recentRecords", imagingRecordService.findRecent());
    }
}
