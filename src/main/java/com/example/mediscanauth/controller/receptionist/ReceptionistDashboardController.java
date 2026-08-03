package com.example.mediscanauth.controller.receptionist;

import com.example.mediscanauth.exception.customize.DoctorScheduleConflictException;
import com.example.mediscanauth.model.Appointment;
import com.example.mediscanauth.model.User;
import com.example.mediscanauth.repository.AppointmentRepository;
import com.example.mediscanauth.repository.AppointmentStatusHistoryRepository;
import com.example.mediscanauth.repository.UserRepository;
import com.example.mediscanauth.service.ReceptionistService;
import com.example.mediscanauth.service.impl.ClinicSettingsService;
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
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

@Controller
public class ReceptionistDashboardController {

    private static final List<String> DOCTOR_ROLE_NAMES = List.of("DOCTOR", "ROLE_DOCTOR");

    // Phòng khám chỉ vận hành với đúng 2 bác sĩ chuyên khoa cố định (ràng buộc
    // nghiệp vụ enforced ở UserAdminService). Tên chuyên khoa gắn với từng
    // bác sĩ cụ thể theo user_id, dùng chung cho mọi nơi hiển thị bác sĩ.
    private static final Map<Long, String> DOCTOR_SPECIALTIES = Map.of(
            2L, "Chấn thương chỉnh hình",
            7L, "Cột sống - Cơ xương khớp"
    );
    private static final String DEFAULT_SPECIALTY = "Chuyên khoa Xương Khớp";
    private static final Set<String> BUSY_STATUSES = Set.of("IN_PROGRESS", "TRIAGED");

    private static String specialtyOf(User doctor) {
        return DOCTOR_SPECIALTIES.getOrDefault(doctor.getUserId(), DEFAULT_SPECIALTY);
    }

    private final AppointmentRepository appointmentRepository;
    private final AppointmentStatusHistoryRepository appointmentStatusHistoryRepository;
    private final UserRepository userRepository;
    private final ReceptionistService receptionistService;
    private final ClinicSettingsService clinicSettingsService;

    public ReceptionistDashboardController(AppointmentRepository appointmentRepository,
                                           AppointmentStatusHistoryRepository appointmentStatusHistoryRepository,
                                           UserRepository userRepository,
                                           ReceptionistService receptionistService,
                                           ClinicSettingsService clinicSettingsService) {
        this.appointmentRepository = appointmentRepository;
        this.appointmentStatusHistoryRepository = appointmentStatusHistoryRepository;
        this.userRepository = userRepository;
        this.receptionistService = receptionistService;
        this.clinicSettingsService = clinicSettingsService;
    }

    private LocalTime scheduleOpen() {
        return clinicSettingsService.getOpenTime();
    }

    private LocalTime scheduleClose() {
        return clinicSettingsService.getCloseTime();
    }

    private int scheduleSlotMinutes() {
        return clinicSettingsService.getSlotMinutes();
    }

    private int scheduleTotalMinutes() {
        return (int) Duration.between(scheduleOpen(), scheduleClose()).toMinutes();
    }

    /**
     * Overview: numbers only, no actions — so a receptionist can never end up on a page
     * where a button does nothing.
     */
    @GetMapping("/receptionist/dashboard")
    public String dashboard(Model model) {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime endOfDay = startOfDay.plusDays(1);

        List<Appointment> todayAppointments = appointmentRepository.findByScheduledTimeBetweenOrderByScheduledTimeAsc(startOfDay, endOfDay);
        List<User> doctorsOnDuty = userRepository.findByRoleRoleNameInAndStatusOrderByFullNameAsc(DOCTOR_ROLE_NAMES, "ACTIVE");

        // Stat counts
        long todayCount = todayAppointments.size();
        long pendingCount = todayAppointments.stream().filter(a -> "PENDING".equals(a.getStatus())).count();
        long confirmedCount = todayAppointments.stream().filter(a -> "CONFIRMED".equals(a.getStatus()) || "SCHEDULED".equals(a.getStatus())).count();
        long checkedInCount = todayAppointments.stream().filter(a -> "CHECKED_IN".equals(a.getStatus())).count();
        long inProgressCount = todayAppointments.stream().filter(a -> "IN_PROGRESS".equals(a.getStatus()) || "TRIAGED".equals(a.getStatus())).count();
        long completedCount = todayAppointments.stream().filter(a -> "COMPLETED".equals(a.getStatus())).count();
        long cancelledCount = todayAppointments.stream().filter(a -> "CANCELLED".equals(a.getStatus()) || "MISSED".equals(a.getStatus())).count();
        long unassignedCount = todayAppointments.stream().filter(a -> a.getDoctor() == null).count();

        // Shift calculations (Morning 06-12, Afternoon 12-17, Evening 17-21)
        long morningShiftCount = todayAppointments.stream()
                .filter(a -> a.getScheduledTime().getHour() >= 6 && a.getScheduledTime().getHour() < 12)
                .count();
        long afternoonShiftCount = todayAppointments.stream()
                .filter(a -> a.getScheduledTime().getHour() >= 12 && a.getScheduledTime().getHour() < 17)
                .count();
        long eveningShiftCount = todayAppointments.stream()
                .filter(a -> a.getScheduledTime().getHour() >= 17 && a.getScheduledTime().getHour() < 21)
                .count();

        // Hourly breakdown across the clinic's operating hours
        int openHour = scheduleOpen().getHour();
        int lastFullHour = scheduleClose().getHour() - 1;
        List<String> hourlyLabels = new ArrayList<>();
        for (int h = openHour; h <= lastFullHour; h++) {
            hourlyLabels.add(String.format("%02d:00", h));
        }
        List<Integer> hourlyData = new ArrayList<>();
        int maxHourlyCount = 0;
        String peakHourRange = "N/A";
        for (int h = openHour; h <= lastFullHour; h++) {
            final int hour = h;
            int count = (int) todayAppointments.stream()
                    .filter(a -> a.getScheduledTime().getHour() == hour)
                    .count();
            hourlyData.add(count);
            if (count > maxHourlyCount) {
                maxHourlyCount = count;
                peakHourRange = String.format("%02d:00 - %02d:00", h, h + 1);
            }
        }

        // Doctor Workloads
        List<DoctorWorkloadDto> doctorWorkloads = new ArrayList<>();
        for (User doctor : doctorsOnDuty) {
            long docCount = todayAppointments.stream()
                    .filter(a -> a.getDoctor() != null && a.getDoctor().getUserId().equals(doctor.getUserId()))
                    .count();
            long docCompleted = todayAppointments.stream()
                    .filter(a -> a.getDoctor() != null && a.getDoctor().getUserId().equals(doctor.getUserId()) && "COMPLETED".equals(a.getStatus()))
                    .count();
            boolean busy = todayAppointments.stream()
                    .anyMatch(a -> a.getDoctor() != null && a.getDoctor().getUserId().equals(doctor.getUserId())
                            && BUSY_STATUSES.contains(a.getStatus()));
            doctorWorkloads.add(new DoctorWorkloadDto(doctor, specialtyOf(doctor), busy, docCount, docCompleted));
        }
        long doctorsWorkingCount = doctorWorkloads.stream().filter(DoctorWorkloadDto::isBusy).count();

        // Waiting appointments (CHECKED_IN queue)
        List<Appointment> waitingAppointments = todayAppointments.stream()
                .filter(a -> "CHECKED_IN".equals(a.getStatus()))
                .toList();

        model.addAttribute("todayCount", todayCount);
        model.addAttribute("waitingCheckinCount", pendingCount + confirmedCount);
        model.addAttribute("receivedCount", checkedInCount + inProgressCount + completedCount);
        model.addAttribute("waitingCount", checkedInCount);
        model.addAttribute("pendingCount", pendingCount);
        model.addAttribute("confirmedCount", confirmedCount);
        model.addAttribute("inProgressCount", inProgressCount);
        model.addAttribute("completedCount", completedCount);
        model.addAttribute("cancelledCount", cancelledCount);
        model.addAttribute("unassignedCount", unassignedCount);
        model.addAttribute("doctorsOnDutyCount", doctorsOnDuty.size());
        model.addAttribute("doctorsOnDuty", doctorsOnDuty);
        model.addAttribute("todayAppointments", todayAppointments);
        model.addAttribute("waitingAppointments", waitingAppointments);
        model.addAttribute("today", LocalDate.now());

        model.addAttribute("morningShiftCount", morningShiftCount);
        model.addAttribute("afternoonShiftCount", afternoonShiftCount);
        model.addAttribute("eveningShiftCount", eveningShiftCount);
        model.addAttribute("hourlyLabels", hourlyLabels);
        model.addAttribute("hourlyData", hourlyData);
        model.addAttribute("peakHourRange", peakHourRange);
        model.addAttribute("maxHourlyCount", maxHourlyCount);
        model.addAttribute("doctorWorkloads", doctorWorkloads);
        model.addAttribute("doctorsWorkingCount", doctorsWorkingCount);
        model.addAttribute("maxActiveDoctors", DOCTOR_SPECIALTIES.size());
        model.addAttribute("clinicOpenHour", openHour);
        model.addAttribute("clinicCloseHour", scheduleClose().getHour());

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

    @PostMapping("/receptionist/appointments/{id}/call-in")
    public String callInAppointment(@PathVariable("id") Long appointmentId,
                                     @RequestParam(required = false) String redirectTo,
                                     Authentication authentication,
                                     RedirectAttributes redirectAttributes) {
        try {
            receptionistService.callInAppointment(appointmentId, authentication.getName());
            redirectAttributes.addFlashAttribute("success", "Đã gọi bệnh nhân vào phòng khám/chụp.");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:" + safeRedirect(redirectTo, "/receptionist/waiting");
    }

    @GetMapping("/receptionist/appointments/{id}")
    public String appointmentDetail(@PathVariable("id") Long appointmentId, Model model,
                                    RedirectAttributes redirectAttributes) {
        java.util.Optional<Appointment> found = appointmentRepository.findById(appointmentId);
        if (found.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Không tìm thấy lịch hẹn.");
            return "redirect:/receptionist/appointments";
        }
        Appointment appointment = found.get();
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

    @PostMapping("/receptionist/appointments/{id}/complete")
    public String completeAppointment(@PathVariable("id") Long appointmentId,
                                      @RequestParam(required = false) String redirectTo,
                                      Authentication authentication,
                                      RedirectAttributes redirectAttributes) {
        try {
            receptionistService.completeAppointment(appointmentId, authentication.getName());
            redirectAttributes.addFlashAttribute("success", "Đã hoàn tất buổi khám.");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:" + safeRedirect(redirectTo, "/receptionist/appointments");
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
        model.addAttribute("waitingList", appointmentRepository.findByStatusOrderByQueueNumberAsc("CHECKED_IN"));
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
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime endOfDay = startOfDay.plusDays(1);

        List<User> doctorsOnDuty = userRepository.findByRoleRoleNameInAndStatusOrderByFullNameAsc(
                DOCTOR_ROLE_NAMES, "ACTIVE");
        List<Appointment> todayAppointments = appointmentRepository.findByScheduledTimeBetweenOrderByScheduledTimeAsc(startOfDay, endOfDay);

        List<DoctorWorkloadDto> doctorWorkloads = new ArrayList<>();
        for (User doctor : doctorsOnDuty) {
            long docCount = todayAppointments.stream()
                    .filter(a -> a.getDoctor() != null && a.getDoctor().getUserId().equals(doctor.getUserId()))
                    .count();
            long docCompleted = todayAppointments.stream()
                    .filter(a -> a.getDoctor() != null && a.getDoctor().getUserId().equals(doctor.getUserId()) && "COMPLETED".equals(a.getStatus()))
                    .count();
            boolean busy = todayAppointments.stream()
                    .anyMatch(a -> a.getDoctor() != null && a.getDoctor().getUserId().equals(doctor.getUserId())
                            && BUSY_STATUSES.contains(a.getStatus()));
            doctorWorkloads.add(new DoctorWorkloadDto(doctor, specialtyOf(doctor), busy, docCount, docCompleted));
        }

        List<Appointment> recentTodayAppointments = todayAppointments.stream()
                .sorted((a, b) -> b.getAppointmentId().compareTo(a.getAppointmentId()))
                .limit(5)
                .toList();

        model.addAttribute("doctorsOnDuty", doctorsOnDuty);
        model.addAttribute("doctorWorkloads", doctorWorkloads);
        model.addAttribute("timeSlots", buildTimeSlots());
        model.addAttribute("defaultTimeSlot", roundUpToSlot(LocalTime.now()));
        model.addAttribute("todayCount", todayAppointments.size());
        model.addAttribute("recentAppointments", recentTodayAppointments);
        model.addAttribute("today", LocalDate.now());
        model.addAttribute("clinicOpenHour", scheduleOpen().getHour());
        model.addAttribute("clinicCloseHour", scheduleClose().getHour());
        model.addAttribute("slotMinutes", scheduleSlotMinutes());
        model.addAttribute("maxFutureBookingDays", clinicSettingsService.getMaxFutureBookingDays());
        return "receptionist/new-appointment";
    }

    /**
     * Fixed 30-minute booking slots (06:00, 06:30, ... 20:30) so walk-ins
     * always land on the same grid the doctor-conflict check uses, instead
     * of letting the receptionist type an arbitrary time like 09:07.
     */
    private List<LocalTime> buildTimeSlots() {
        List<LocalTime> slots = new ArrayList<>();
        for (LocalTime t = scheduleOpen(); t.isBefore(scheduleClose()); t = t.plusMinutes(scheduleSlotMinutes())) {
            slots.add(t);
        }
        return slots;
    }

    private LocalTime roundUpToSlot(LocalTime time) {
        if (time.isBefore(scheduleOpen())) {
            return scheduleOpen();
        }
        long minutesFromOpen = Duration.between(scheduleOpen(), time).toMinutes();
        long roundedUp = ((minutesFromOpen + scheduleSlotMinutes() - 1) / scheduleSlotMinutes()) * scheduleSlotMinutes();
        LocalTime slot = scheduleOpen().plusMinutes(roundedUp);
        return slot.isAfter(scheduleClose().minusMinutes(scheduleSlotMinutes()))
                ? scheduleClose().minusMinutes(scheduleSlotMinutes())
                : slot;
    }
//Xu ly POST
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
        model.addAttribute("hourMarks", IntStream.rangeClosed(scheduleOpen().getHour(), scheduleClose().getHour()).boxed().toList());
        return "receptionist/schedule";
    }

    private ScheduleSlot toScheduleSlot(Appointment appointment) {
        LocalTime time = appointment.getScheduledTime().toLocalTime();
        int minutesFromOpen = (int) Duration.between(scheduleOpen(), time).toMinutes();
        double leftPercent = Math.max(0, Math.min(100, minutesFromOpen * 100.0 / scheduleTotalMinutes()));
        double widthPercent = Math.min(100 - leftPercent, scheduleSlotMinutes() * 100.0 / scheduleTotalMinutes());
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

    public static class DoctorWorkloadDto {
        private final User doctor;
        private final String specialty;
        private final boolean busy;
        private final long totalAppointments;
        private final long completedAppointments;

        public DoctorWorkloadDto(User doctor, String specialty, boolean busy,
                                  long totalAppointments, long completedAppointments) {
            this.doctor = doctor;
            this.specialty = specialty;
            this.busy = busy;
            this.totalAppointments = totalAppointments;
            this.completedAppointments = completedAppointments;
        }

        public User getDoctor() {
            return doctor;
        }

        public String getSpecialty() {
            return specialty;
        }

        public boolean isBusy() {
            return busy;
        }

        public long getTotalAppointments() {
            return totalAppointments;
        }

        public long getCompletedAppointments() {
            return completedAppointments;
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
