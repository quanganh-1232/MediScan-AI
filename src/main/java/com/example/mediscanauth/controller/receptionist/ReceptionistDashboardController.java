package com.example.mediscanauth.controller.receptionist;

import com.example.mediscanauth.model.Appointment;
import com.example.mediscanauth.repository.AppointmentRepository;
import com.example.mediscanauth.repository.AppointmentStatusHistoryRepository;
import com.example.mediscanauth.repository.UserRepository;
import com.example.mediscanauth.service.ReceptionistService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Controller
public class ReceptionistDashboardController {

    private final AppointmentRepository appointmentRepository;
    private final AppointmentStatusHistoryRepository appointmentStatusHistoryRepository;
    private final UserRepository userRepository;
    private final ReceptionistService receptionistService;

    public ReceptionistDashboardController(AppointmentRepository appointmentRepository,
                                           AppointmentStatusHistoryRepository appointmentStatusHistoryRepository,
                                           UserRepository userRepository,
                                           ReceptionistService receptionistService) {
        this.appointmentRepository = appointmentRepository;
        this.appointmentStatusHistoryRepository = appointmentStatusHistoryRepository;
        this.userRepository = userRepository;
        this.receptionistService = receptionistService;
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

    @PostMapping("/receptionist/appointments/{id}/confirm")
    public String confirmAppointment(@PathVariable("id") Long appointmentId,
                                     Authentication authentication,
                                     RedirectAttributes redirectAttributes) {
        try {
            receptionistService.confirmAppointment(appointmentId, authentication.getName());
            redirectAttributes.addFlashAttribute("success", "Đã xác nhận lịch hẹn.");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/receptionist/dashboard";
    }

    @PostMapping("/receptionist/appointments/{id}/checkin")
    public String checkInAppointment(@PathVariable("id") Long appointmentId,
                                     Authentication authentication,
                                     RedirectAttributes redirectAttributes) {
        try {
            receptionistService.checkInAppointment(appointmentId, authentication.getName());
            redirectAttributes.addFlashAttribute("success", "Đã check-in bệnh nhân.");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/receptionist/dashboard";
    }

    @GetMapping("/receptionist/appointments/{id}")
    public String appointmentDetail(@PathVariable("id") Long appointmentId, Model model) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy lịch hẹn."));
        model.addAttribute("appointment", appointment);
        model.addAttribute("history", appointmentStatusHistoryRepository.findByAppointmentOrderByCreatedAtAsc(appointment));
        model.addAttribute("doctors", userRepository.findByRoleRoleNameInAndStatusOrderByFullNameAsc(
                List.of("DOCTOR", "ROLE_DOCTOR"), "ACTIVE"));
        return "receptionist/appointment-detail";
    }

    @PostMapping("/receptionist/appointments/{id}/assign-doctor")
    public String assignDoctor(@PathVariable("id") Long appointmentId,
                               @RequestParam Long doctorId,
                               @RequestParam(required = false) String note,
                               Authentication authentication,
                               RedirectAttributes redirectAttributes) {
        try {
            receptionistService.assignDoctor(appointmentId, doctorId, note, authentication.getName());
            redirectAttributes.addFlashAttribute("success", "Đã điều hướng bệnh nhân đến bác sĩ.");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/receptionist/appointments/" + appointmentId;
    }
}
