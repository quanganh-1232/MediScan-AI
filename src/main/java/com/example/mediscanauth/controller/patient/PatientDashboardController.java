package com.example.mediscanauth.controller.patient;

import com.example.mediscanauth.model.User;
import com.example.mediscanauth.service.ImagingRecordService;
import com.example.mediscanauth.service.NotificationService;
import com.example.mediscanauth.service.UserAccountService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PatientDashboardController {

    private final UserAccountService userAccountService;
    private final ImagingRecordService imagingRecordService;
    private final NotificationService notificationService;

    public PatientDashboardController(UserAccountService userAccountService,
                                      ImagingRecordService imagingRecordService,
                                      NotificationService notificationService) {
        this.userAccountService = userAccountService;
        this.imagingRecordService = imagingRecordService;
        this.notificationService = notificationService;
    }

    @GetMapping("/patient/dashboard")
    public String dashboard(Authentication authentication, Model model) {
        addModel(authentication, model);
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
        return "patient/records";
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

    private void addModel(Authentication authentication, Model model) {
        User patient = userAccountService.findByEmail(authentication.getName());
        model.addAttribute("currentUser", patient);
        model.addAttribute("records", imagingRecordService.findForPatient(patient));
        model.addAttribute("latestRecord", imagingRecordService.findLatestForPatient(patient));
        model.addAttribute("recordCount", imagingRecordService.countForPatient(patient));
        model.addAttribute("unreadCount", notificationService.countUnread(patient));
    }
}
