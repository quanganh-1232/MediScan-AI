package com.example.mediscanauth.controller.common;

import com.example.mediscanauth.model.Appointment;
import com.example.mediscanauth.model.User;
import com.example.mediscanauth.repository.AppointmentRepository;
import com.example.mediscanauth.repository.UserRepository;
import com.example.mediscanauth.service.ImagingRecordService;
import com.example.mediscanauth.service.NotificationService;
import com.example.mediscanauth.service.UserAccountService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.LocalDateTime;
import java.util.List;

@Controller
public class HomeController {

    private final UserRepository userRepository;
    private final UserAccountService userAccountService;
    private final ImagingRecordService imagingRecordService;
    private final NotificationService notificationService;
    private final AppointmentRepository appointmentRepository;

    public HomeController(UserRepository userRepository,
                          UserAccountService userAccountService,
                          ImagingRecordService imagingRecordService,
                          NotificationService notificationService,
                          AppointmentRepository appointmentRepository) {
        this.userRepository = userRepository;
        this.userAccountService = userAccountService;
        this.imagingRecordService = imagingRecordService;
        this.notificationService = notificationService;
        this.appointmentRepository = appointmentRepository;
    }

    @GetMapping("/home")
    public String home(Authentication authentication, Model model) {
        User user = userAccountService.findByEmail(authentication.getName());
        model.addAttribute("currentUser", user);

        if (isPatient(user)) {
            return patientHome(user, model);
        }

        if (hasRole(user, "RECEPTIONIST")) {
            return "redirect:/receptionist/dashboard";
        }

        if (hasRole(user, "DOCTOR")) {
            return "redirect:/doctor/records/pending";
        }

        if (hasRole(user, "TECHNICIAN")) {
            return "redirect:/technician/xray-upload";
        }

        if (isAdmin(user)) {
            return "redirect:/admin/dashboard";
        }

        model.addAttribute("activeUserCount", userRepository.countByStatus("ACTIVE"));
        model.addAttribute("queueCount", imagingRecordService.countQueue());
        model.addAttribute("todayRecordCount", imagingRecordService.countToday());
        model.addAttribute("totalRecordCount", imagingRecordService.countAll());
        model.addAttribute("recentRecords", imagingRecordService.findRecent());
        model.addAttribute("notifications", notificationService.findForUser(user));
        model.addAttribute("unreadCount", notificationService.countUnread(user));

        if (isAdmin(user)) {
            model.addAttribute("users", userRepository.findAll());
        }

        return "common/home";
    }

    private String patientHome(User user, Model model) {
        List<Appointment> myAppointments = appointmentRepository.findByPatientUserOrderByScheduledTimeDesc(user);
        Appointment upcoming = myAppointments.stream()
                .filter(a -> a.getScheduledTime() != null && a.getScheduledTime().isAfter(LocalDateTime.now()))
                .filter(a -> !"CANCELLED".equals(a.getStatus()) && !"MISSED".equals(a.getStatus()))
                .reduce((first, second) -> second)
                .orElse(null);

        model.addAttribute("myAppointments", myAppointments);
        model.addAttribute("upcomingAppointment", upcoming);
        model.addAttribute("doctors", userRepository.findByRoleRoleNameInAndStatusOrderByFullNameAsc(
                List.of("DOCTOR", "ROLE_DOCTOR"), "ACTIVE"));
        model.addAttribute("patientRecordCount", imagingRecordService.countForPatient(user));
        model.addAttribute("latestRecord", imagingRecordService.findLatestForPatient(user));
        model.addAttribute("unreadCount", notificationService.countUnread(user));
        // KAN-39: Health summary stats
        model.addAttribute("totalAppointments", myAppointments.size());
        model.addAttribute("completedAppointments",
                myAppointments.stream().filter(a -> "COMPLETED".equals(a.getStatus())).count());
        model.addAttribute("pendingAppointments",
                myAppointments.stream().filter(a -> "PENDING".equals(a.getStatus()) || "SCHEDULED".equals(a.getStatus())).count());
        return "patient/home";
    }

    private boolean isAdmin(User user) {
        return hasRole(user, "ADMIN");
    }

    private boolean isPatient(User user) {
        return hasRole(user, "PATIENT");
    }

    private boolean hasRole(User user, String roleName) {
        return user.getRole() != null
                && (roleName.equals(user.getRole().getRoleName()) || ("ROLE_" + roleName).equals(user.getRole().getRoleName()));
    }
}
