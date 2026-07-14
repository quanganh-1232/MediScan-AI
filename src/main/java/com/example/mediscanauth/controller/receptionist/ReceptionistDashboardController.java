package com.example.mediscanauth.controller.receptionist;

import com.example.mediscanauth.exception.customize.DoctorScheduleConflictException;
import com.example.mediscanauth.model.Appointment;
import com.example.mediscanauth.model.User;
import com.example.mediscanauth.repository.AppointmentRepository;
import com.example.mediscanauth.repository.AppointmentStatusHistoryRepository;
import com.example.mediscanauth.repository.UserRepository;
import com.example.mediscanauth.service.ReceptionistService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

@Controller
public class ReceptionistDashboardController {

    private static final List<String> DOCTOR_ROLE_NAMES = List.of("DOCTOR", "ROLE_DOCTOR");
    private static final LocalTime SCHEDULE_OPEN = LocalTime.of(6, 0);
    private static final LocalTime SCHEDULE_CLOSE = LocalTime.of(21, 0);
    private static final int SCHEDULE_TOTAL_MINUTES = (int) Duration.between(SCHEDULE_OPEN, SCHEDULE_CLOSE).toMinutes();
    private static final int SCHEDULE_SLOT_MINUTES = 30;

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

    /**
     * Overview: numbers only, no actions — so a receptionist can never end up on a page
     * where a button does nothing.
     */
    @GetMapping("/receptionist/dashboard")
    public String dashboard(Model model) {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime endOfDay = startOfDay.plusDays(1);

        model.addAttribute("todayCount", appointmentRepository.countByScheduledTimeBetween(startOfDay, endOfDay));
        model.addAttribute("waitingCheckinCount", appointmentRepository.countByStatusIn(List.of("PENDING", "CONFIRMED", "SCHEDULED")));
        model.addAttribute("receivedCount", appointmentRepository.countByStatusIn(List.of("CHECKED_IN", "TRIAGED", "IN_PROGRESS", "COMPLETED")));
        model.addAttribute("waitingCount", appointmentRepository.countByStatus("CHECKED_IN"));
        model.addAttribute("doctorsOnDutyCount", userRepository.findByRoleRoleNameInAndStatusOrderByFullNameAsc(
                DOCTOR_ROLE_NAMES, "ACTIVE").size());
        model.addAttribute("todayAppointments",
                appointmentRepository.findByScheduledTimeBetweenOrderByScheduledTimeAsc(startOfDay, endOfDay));
        model.addAttribute("today", LocalDate.now());
        return "receptionist/dashboard";
    }

    /**
     * Tiếp nhận lịch hẹn & check-in: search/filter table with the confirm/check-in/
     * cancel/no-show actions.
     */
    @GetMapping("/receptionist/appointments")
    public String appointments(@RequestParam(required = false) String keyword,
                               @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate date,
                               @RequestParam(required = false) String status,
                               @RequestParam(defaultValue = "0") int page,
                               Model model) {
        LocalDateTime dateFrom = date != null ? date.atStartOfDay() : null;
        LocalDateTime dateTo = date != null ? date.plusDays(1).atStartOfDay() : null;
        Page<Appointment> appointmentsPage = appointmentRepository.searchAppointments(
                keyword, dateFrom, dateTo, status, PageRequest.of(Math.max(page, 0), 10));

        model.addAttribute("appointmentsPage", appointmentsPage);
        model.addAttribute("keyword", keyword == null ? "" : keyword);
        model.addAttribute("filterDate", date);
        model.addAttribute("selectedStatus", status == null ? "" : status);
        model.addAttribute("backUrl", buildBackUrl(keyword, date, status, appointmentsPage.getNumber()));
        return "receptionist/appointments";
    }

    @PostMapping("/receptionist/appointments/{id}/confirm")
    public String confirmAppointment(@PathVariable("id") Long appointmentId,
                                     @RequestParam(required = false) String redirectTo,
                                     Authentication authentication,
                                     RedirectAttributes redirectAttributes) {
        try {
            receptionistService.confirmAppointment(appointmentId, authentication.getName());
            redirectAttributes.addFlashAttribute("success", "Đã xác nhận lịch hẹn.");
        } catch (DoctorScheduleConflictException ex) {
            redirectAttributes.addFlashAttribute("conflictError", ex.getMessage());
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:" + safeRedirect(redirectTo, "/receptionist/appointments");
    }

    @PostMapping("/receptionist/appointments/{id}/checkin")
    public String checkInAppointment(@PathVariable("id") Long appointmentId,
                                     @RequestParam(required = false) String redirectTo,
                                     Authentication authentication,
                                     RedirectAttributes redirectAttributes) {
        try {
            receptionistService.checkInAppointment(appointmentId, authentication.getName());
            redirectAttributes.addFlashAttribute("success", "Đã check-in bệnh nhân.");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:" + safeRedirect(redirectTo, "/receptionist/appointments");
    }

    @GetMapping("/receptionist/appointments/{id}")
    public String appointmentDetail(@PathVariable("id") Long appointmentId, Model model) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy lịch hẹn."));
        model.addAttribute("appointment", appointment);
        model.addAttribute("history", appointmentStatusHistoryRepository.findByAppointmentOrderByCreatedAtAsc(appointment));
        model.addAttribute("doctors", userRepository.findByRoleRoleNameInAndStatusOrderByFullNameAsc(
                DOCTOR_ROLE_NAMES, "ACTIVE"));
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
        } catch (DoctorScheduleConflictException ex) {
            redirectAttributes.addFlashAttribute("conflictError", ex.getMessage());
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/receptionist/appointments/" + appointmentId;
    }

    @PostMapping("/receptionist/appointments/{id}/cancel")
    public String cancelAppointment(@PathVariable("id") Long appointmentId,
                                    @RequestParam(required = false) String reason,
                                    @RequestParam(required = false) String redirectTo,
                                    Authentication authentication,
                                    RedirectAttributes redirectAttributes) {
        try {
            receptionistService.cancelAppointment(appointmentId, reason, authentication.getName());
            redirectAttributes.addFlashAttribute("success", "Đã hủy lịch hẹn.");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:" + safeRedirect(redirectTo, "/receptionist/appointments");
    }

    @PostMapping("/receptionist/appointments/{id}/missed")
    public String markMissed(@PathVariable("id") Long appointmentId,
                             @RequestParam(required = false) String redirectTo,
                             Authentication authentication,
                             RedirectAttributes redirectAttributes) {
        try {
            receptionistService.markMissed(appointmentId, authentication.getName());
            redirectAttributes.addFlashAttribute("success", "Đã đánh dấu bệnh nhân vắng mặt.");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:" + safeRedirect(redirectTo, "/receptionist/appointments");
    }

    /**
     * Danh sách chờ: full CHECKED_IN queue + "call next" action.
     */
    @GetMapping("/receptionist/waiting")
    public String waitingList(Model model) {
        model.addAttribute("waitingList", appointmentRepository.findByStatusOrderByScheduledTimeAsc("CHECKED_IN"));
        return "receptionist/waiting";
    }

    @PostMapping("/receptionist/appointments/call-next")
    public String callNextPatient(Authentication authentication, RedirectAttributes redirectAttributes) {
        try {
            Appointment appointment = receptionistService.callNextPatient(authentication.getName());
            String patientName = appointment.getPatient() != null ? appointment.getPatient().getFullName() : "bệnh nhân";
            redirectAttributes.addFlashAttribute("success",
                    "Đã gọi " + patientName + " (" + appointment.getAppointmentCode() + ") vào phòng khám.");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/receptionist/waiting";
    }

    /**
     * Tạo lịch mới: walk-in quick registration form.
     */
    @GetMapping("/receptionist/appointments/new")
    public String newWalkInForm(Model model) {
        model.addAttribute("doctorsOnDuty", userRepository.findByRoleRoleNameInAndStatusOrderByFullNameAsc(
                DOCTOR_ROLE_NAMES, "ACTIVE"));
        return "receptionist/new-appointment";
    }

    @PostMapping("/receptionist/appointments/walk-in")
    public String createWalkInAppointment(@RequestParam String fullName,
                                          @RequestParam String phone,
                                          @RequestParam(required = false) String symptom,
                                          @RequestParam(required = false) Long doctorId,
                                          @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate scheduledDate,
                                          @RequestParam(required = false) @DateTimeFormat(pattern = "HH:mm") LocalTime scheduledTime,
                                          Authentication authentication,
                                          RedirectAttributes redirectAttributes) {
        try {
            Appointment appointment = receptionistService.createWalkInAppointment(
                    fullName, phone, symptom, doctorId, scheduledDate, scheduledTime, authentication.getName());
            redirectAttributes.addFlashAttribute("success",
                    "Đã đăng ký lịch hẹn " + appointment.getAppointmentCode() + " cho khách vãng lai.");
        } catch (DoctorScheduleConflictException ex) {
            redirectAttributes.addFlashAttribute("conflictError", ex.getMessage());
            addWalkInFormBackFill(redirectAttributes, fullName, phone, symptom, doctorId, scheduledDate, scheduledTime);
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            addWalkInFormBackFill(redirectAttributes, fullName, phone, symptom, doctorId, scheduledDate, scheduledTime);
        }
        return "redirect:/receptionist/appointments/new";
    }

    /**
     * On validation failure, keep what the receptionist already typed so a
     * mistake in one field doesn't force re-entering the whole walk-in form.
     */
    private void addWalkInFormBackFill(RedirectAttributes redirectAttributes,
                                       String fullName, String phone, String symptom,
                                       Long doctorId, LocalDate scheduledDate, LocalTime scheduledTime) {
        redirectAttributes.addFlashAttribute("formFullName", fullName);
        redirectAttributes.addFlashAttribute("formPhone", phone);
        redirectAttributes.addFlashAttribute("formSymptom", symptom);
        redirectAttributes.addFlashAttribute("formDoctorId", doctorId);
        redirectAttributes.addFlashAttribute("formScheduledDate", scheduledDate);
        redirectAttributes.addFlashAttribute("formScheduledTime", scheduledTime);
    }

    /**
     * Bác sĩ trực: view-only.
     */
    @GetMapping("/receptionist/doctors")
    public String doctorsOnDuty(Model model) {
        model.addAttribute("doctorsOnDuty", userRepository.findByRoleRoleNameInAndStatusOrderByFullNameAsc(
                DOCTOR_ROLE_NAMES, "ACTIVE"));
        return "receptionist/doctors";
    }

    /**
     * Lịch bác sĩ theo ngày: timeline trực quan giúp lễ tân thấy ngay khung giờ
     * trống/bận của từng bác sĩ trước khi đặt lịch, thay vì phải đoán hoặc chờ
     * popup trùng lịch khi submit form.
     */
    @GetMapping("/receptionist/schedule")
    public String schedule(@RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate date,
                           Model model) {
        LocalDate selectedDate = date != null ? date : LocalDate.now();
        LocalDateTime startOfDay = selectedDate.atStartOfDay();
        LocalDateTime endOfDay = startOfDay.plusDays(1);

        List<User> doctors = userRepository.findByRoleRoleNameInAndStatusOrderByFullNameAsc(DOCTOR_ROLE_NAMES, "ACTIVE");
        List<Appointment> dayAppointments = appointmentRepository.findByScheduledTimeBetweenOrderByScheduledTimeAsc(startOfDay, endOfDay);

        List<DoctorScheduleRow> scheduleRows = new ArrayList<>();
        for (User doctor : doctors) {
            List<ScheduleSlot> slots = new ArrayList<>();
            for (Appointment appointment : dayAppointments) {
                if (appointment.getDoctor() != null && appointment.getDoctor().getUserId().equals(doctor.getUserId())) {
                    slots.add(toScheduleSlot(appointment));
                }
            }
            scheduleRows.add(new DoctorScheduleRow(doctor, slots));
        }
        List<ScheduleSlot> unassignedSlots = dayAppointments.stream()
                .filter(a -> a.getDoctor() == null)
                .map(this::toScheduleSlot)
                .toList();

        model.addAttribute("scheduleRows", scheduleRows);
        model.addAttribute("unassignedSlots", unassignedSlots);
        model.addAttribute("selectedDate", selectedDate);
        model.addAttribute("hourMarks", IntStream.rangeClosed(SCHEDULE_OPEN.getHour(), SCHEDULE_CLOSE.getHour()).boxed().toList());
        return "receptionist/schedule";
    }

    private ScheduleSlot toScheduleSlot(Appointment appointment) {
        LocalTime time = appointment.getScheduledTime().toLocalTime();
        int minutesFromOpen = (int) Duration.between(SCHEDULE_OPEN, time).toMinutes();
        double leftPercent = Math.max(0, Math.min(100, minutesFromOpen * 100.0 / SCHEDULE_TOTAL_MINUTES));
        double widthPercent = Math.min(100 - leftPercent, SCHEDULE_SLOT_MINUTES * 100.0 / SCHEDULE_TOTAL_MINUTES);
        return new ScheduleSlot(appointment, leftPercent, widthPercent);
    }

    /**
     * Presentation-only wrappers so the timeline template can position each
     * appointment chip with a plain percentage, instead of doing time-math in Thymeleaf.
     */
    public static class ScheduleSlot {
        private final Appointment appointment;
        private final double leftPercent;
        private final double widthPercent;

        public ScheduleSlot(Appointment appointment, double leftPercent, double widthPercent) {
            this.appointment = appointment;
            this.leftPercent = leftPercent;
            this.widthPercent = widthPercent;
        }

        public Appointment getAppointment() {
            return appointment;
        }

        public double getLeftPercent() {
            return leftPercent;
        }

        public double getWidthPercent() {
            return widthPercent;
        }
    }

    public static class DoctorScheduleRow {
        private final User doctor;
        private final List<ScheduleSlot> slots;

        public DoctorScheduleRow(User doctor, List<ScheduleSlot> slots) {
            this.doctor = doctor;
            this.slots = slots;
        }

        public User getDoctor() {
            return doctor;
        }

        public List<ScheduleSlot> getSlots() {
            return slots;
        }
    }

    /**
     * Only allow redirecting back within the receptionist module, to avoid an open-redirect
     * via the redirectTo request parameter.
     */
    private String safeRedirect(String redirectTo, String fallback) {
        if (redirectTo != null && redirectTo.startsWith("/receptionist/") && !redirectTo.contains("://")) {
            return redirectTo;
        }
        return fallback;
    }

    private String buildBackUrl(String keyword, LocalDate date, String status, int page) {
        StringBuilder url = new StringBuilder("/receptionist/appointments?page=").append(page);
        if (keyword != null && !keyword.isBlank()) {
            url.append("&keyword=").append(URLEncoder.encode(keyword, StandardCharsets.UTF_8));
        }
        if (date != null) {
            url.append("&date=").append(date);
        }
        if (status != null && !status.isBlank()) {
            url.append("&status=").append(status);
        }
        return url.toString();
    }
}
