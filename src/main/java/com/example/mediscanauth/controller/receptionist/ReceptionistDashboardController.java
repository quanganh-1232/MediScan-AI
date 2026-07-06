package com.example.mediscanauth.controller.receptionist;

import com.example.mediscanauth.model.Appointment;
import com.example.mediscanauth.repository.AppointmentRepository;
import com.example.mediscanauth.repository.UserRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Controller
public class ReceptionistDashboardController {

    private final AppointmentRepository appointmentRepository;
    private final UserRepository userRepository;

    public ReceptionistDashboardController(AppointmentRepository appointmentRepository,
                                           UserRepository userRepository) {
        this.appointmentRepository = appointmentRepository;
        this.userRepository = userRepository;
    }

    @GetMapping("/receptionist/dashboard")
    public String dashboard(Model model) {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime endOfDay = startOfDay.plusDays(1);

        List<Appointment> todayAppointments =
                appointmentRepository.findByScheduledTimeBetweenOrderByScheduledTimeAsc(startOfDay, endOfDay);

        model.addAttribute("todayCount", appointmentRepository.countByScheduledTimeBetween(startOfDay, endOfDay));
        model.addAttribute("waitingCheckinCount", appointmentRepository.countByStatusIn(List.of("PENDING", "CONFIRMED", "SCHEDULED")));
        model.addAttribute("receivedCount", appointmentRepository.countByStatusIn(List.of("CHECKED_IN", "TRIAGED", "IN_PROGRESS", "COMPLETED")));
        model.addAttribute("doctorsOnDuty", userRepository.findByRoleRoleNameInAndStatusOrderByFullNameAsc(
                List.of("DOCTOR", "ROLE_DOCTOR"), "ACTIVE"));
        model.addAttribute("todayAppointments", todayAppointments);
        model.addAttribute("recentAppointments", appointmentRepository.findTop10ByOrderByScheduledTimeDesc());
        model.addAttribute("today", LocalDate.now());
        return "receptionist/dashboard";
    }
}
