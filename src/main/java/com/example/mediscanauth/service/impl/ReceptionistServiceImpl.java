package com.example.mediscanauth.service.impl;

import com.example.mediscanauth.exception.customize.DoctorScheduleConflictException;
import com.example.mediscanauth.exception.customize.InvalidFieldException;
import com.example.mediscanauth.model.Appointment;
import com.example.mediscanauth.model.AppointmentStatusHistory;
import com.example.mediscanauth.model.Patient;
import com.example.mediscanauth.model.User;
import com.example.mediscanauth.repository.AppointmentRepository;
import com.example.mediscanauth.repository.AppointmentStatusHistoryRepository;
import com.example.mediscanauth.repository.PatientRepository;
import com.example.mediscanauth.repository.UserRepository;
import com.example.mediscanauth.service.ReceptionistService;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import com.example.mediscanauth.service.NotificationService;

@Service
public class ReceptionistServiceImpl implements ReceptionistService {

    private static final Set<String> CONFIRMABLE_STATUSES = Set.of("PENDING", "SCHEDULED");
    private static final Set<String> TERMINAL_STATUSES = Set.of("COMPLETED", "CANCELLED", "MISSED");
    private static final Set<String> MISSABLE_STATUSES = Set.of("PENDING", "SCHEDULED", "CONFIRMED");
    // Appointments in these statuses no longer occupy the doctor's schedule,
    // so they're excluded from the double-booking check.
    private static final Set<String> CONFLICT_IGNORED_STATUSES = Set.of("CANCELLED", "MISSED");
    private static final List<String> RECEPTIONIST_ROLE_NAMES = List.of("RECEPTIONIST", "ROLE_RECEPTIONIST");

    private static final Pattern FULL_NAME_PATTERN =
            Pattern.compile("^[\\p{L} .'-]{2,100}$");
    private static final Pattern PHONE_PATTERN =
            Pattern.compile("^(0\\d{9}|\\+84\\d{9})$");
    private static final int MAX_SYMPTOM_LENGTH = 100; // matches appointments.body_part column width
    private static final int MAX_NOTE_LENGTH = 500;
    private static final int MAX_FUTURE_BOOKING_DAYS = 90;
    private static final LocalTime CLINIC_OPEN = LocalTime.of(6, 0);
    private static final LocalTime CLINIC_CLOSE = LocalTime.of(21, 0);
    // Two appointments for the same doctor within this many minutes of each
    // other are treated as a scheduling conflict.
    private static final long SLOT_MINUTES = 30;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");

    private final AppointmentRepository appointmentRepository;
    private final AppointmentStatusHistoryRepository historyRepository;
    private final UserRepository userRepository;
    private final PatientRepository patientRepository;
    private final NotificationService notificationService;

    public ReceptionistServiceImpl(AppointmentRepository appointmentRepository,
                                   AppointmentStatusHistoryRepository historyRepository,
                                   UserRepository userRepository,
                                   PatientRepository patientRepository,
                                   NotificationService notificationService) {
        this.appointmentRepository = appointmentRepository;
        this.historyRepository = historyRepository;
        this.userRepository = userRepository;
        this.patientRepository = patientRepository;
        this.notificationService = notificationService;
    }

    @Override
    @Transactional
    public Appointment confirmAppointment(Long appointmentId, String receptionistEmail) {
        Appointment appointment = getAppointmentOrThrow(appointmentId);
        if (!CONFIRMABLE_STATUSES.contains(appointment.getStatus())) {
            throw new InvalidFieldException("Lịch hẹn không ở trạng thái chờ xác nhận.");
        }
        if (appointment.getDoctor() != null) {
            User lockedDoctor = userRepository.findByIdForUpdate(appointment.getDoctor().getUserId())
                    .orElseThrow(() -> new InvalidFieldException("Không tìm thấy bác sĩ."));
            ensureDoctorAvailable(lockedDoctor, appointment.getScheduledTime(), appointment.getAppointmentId());
        }
        User receptionist = findReceptionist(receptionistEmail);
        appointment.setReceptionist(receptionist);
        appointment.setStatus("CONFIRMED");
        appointmentRepository.save(appointment);
        logStatusChange(appointment, "CONFIRMED", receptionist, "Lễ tân xác nhận lịch hẹn.");

        String formattedTime = appointment.getScheduledTime() != null ? appointment.getScheduledTime().format(TIME_FORMAT) : "";
        if (appointment.getDoctor() != null) {
            notificationService.sendNotification(appointment.getDoctor(), "Xác nhận lịch hẹn #" + appointment.getAppointmentCode(),
                    "Lịch hẹn khám vào " + formattedTime + " đã được xác nhận.", appointment.getAppointmentId());
        }
        if (appointment.getPatient() != null && appointment.getPatient().getUser() != null) {
            notificationService.sendNotification(appointment.getPatient().getUser(), "Lịch hẹn đã được xác nhận #" + appointment.getAppointmentCode(),
                    "Lịch hẹn khám vào " + formattedTime + " đã được lễ tân xác nhận.", appointment.getAppointmentId());
        }
        return appointment;
    }

    @Override
    @Transactional
    public Appointment checkInAppointment(Long appointmentId, String receptionistEmail) {
        Appointment appointment = getAppointmentOrThrow(appointmentId);
        if (!"CONFIRMED".equals(appointment.getStatus())) {
            throw new InvalidFieldException("Chỉ có thể check-in lịch hẹn đã được xác nhận.");
        }
        if (appointment.getScheduledTime() != null && appointment.getScheduledTime().toLocalDate().isAfter(LocalDate.now())) {
            throw new InvalidFieldException(
                    "Lịch hẹn này được đặt cho ngày " + appointment.getScheduledTime().toLocalDate()
                    + ", chưa thể check-in hôm nay.");
        }
        User receptionist = findReceptionist(receptionistEmail);
        appointment.setReceptionist(receptionist);
        appointment.setStatus("CHECKED_IN");
        appointmentRepository.save(appointment);
        logStatusChange(appointment, "CHECKED_IN", receptionist, "Bệnh nhân đã check-in tại quầy lễ tân.");

        String formattedTime = appointment.getScheduledTime() != null ? appointment.getScheduledTime().format(TIME_FORMAT) : "";
        String patientName = appointment.getPatient() != null ? appointment.getPatient().getFullName() : "bệnh nhân";
        if (appointment.getDoctor() != null) {
            notificationService.sendNotification(appointment.getDoctor(), "Bệnh nhân đã check-in #" + appointment.getAppointmentCode(),
                    "Bệnh nhân " + patientName + " đã check-in lúc " + formattedTime + ", đang trong danh sách chờ.", appointment.getAppointmentId());
        }
        notificationService.notifyRoleUsers(RECEPTIONIST_ROLE_NAMES, "Check-in bệnh nhân #" + appointment.getAppointmentCode(),
                "Bệnh nhân " + patientName + " đã check-in tại quầy.", appointment.getAppointmentId());
        return appointment;
    }

    @Override
    @Transactional
    public Appointment assignDoctor(Long appointmentId, Long doctorId, String note, String receptionistEmail) {
        Appointment appointment = getAppointmentOrThrow(appointmentId);
        if (TERMINAL_STATUSES.contains(appointment.getStatus())) {
            throw new InvalidFieldException("Không thể đổi bác sĩ cho lịch hẹn đã kết thúc.");
        }
        String cleanNote = validateNote(note, "Ghi chú");
        User receptionist = findReceptionist(receptionistEmail);
        User doctor = findDoctorOrThrow(doctorId);
        userRepository.findByIdForUpdate(doctor.getUserId());
        ensureDoctorAvailable(doctor, appointment.getScheduledTime(), appointment.getAppointmentId());

        User previousDoctor = appointment.getDoctor();
        appointment.setDoctor(doctor);
        appointment.setReceptionist(receptionist);
        appointmentRepository.save(appointment);

        String historyNote = previousDoctor != null
                ? "Chuyển từ BS. " + previousDoctor.getFullName() + " sang BS. " + doctor.getFullName() + "."
                : "Điều hướng đến BS. " + doctor.getFullName() + ".";
        if (cleanNote != null) {
            historyNote += " Ghi chú: " + cleanNote;
        }
        logStatusChange(appointment, appointment.getStatus(), receptionist, historyNote);

        String formattedTime = appointment.getScheduledTime() != null ? appointment.getScheduledTime().format(TIME_FORMAT) : "";
        String patientName = appointment.getPatient() != null ? appointment.getPatient().getFullName() : "bệnh nhân";
        notificationService.sendNotification(doctor, "Phân công ca khám mới #" + appointment.getAppointmentCode(),
                "Bạn đã được phân công phụ trách bệnh nhân " + patientName + " lúc " + formattedTime + ".", appointment.getAppointmentId());
        return appointment;
    }

    @Override
    @Transactional
    public Appointment createWalkInAppointment(String fullName,
                                               String phone,
                                               String symptom,
                                               Long doctorId,
                                               LocalDate scheduledDate,
                                               LocalTime scheduledTime,
                                               String receptionistEmail) {
        String cleanFullName = validateFullName(fullName);
        String cleanPhone = validatePhone(phone);
        String cleanSymptom = validateSymptom(symptom);
        LocalDate date = validateScheduledDate(scheduledDate);
        LocalTime time = validateScheduledTime(scheduledTime);

        User receptionist = findReceptionist(receptionistEmail);
        User doctor = doctorId != null ? findDoctorOrThrow(doctorId) : null;
        LocalDateTime scheduledAt = LocalDateTime.of(date, time);
        if (doctor != null) {
            userRepository.findByIdForUpdate(doctor.getUserId());
            ensureDoctorAvailable(doctor, scheduledAt, null);
        }

        Patient existing = patientRepository.findFirstByPhoneOrderByPatientIdDesc(cleanPhone).orElse(null);
        Patient patient;
        if (existing != null && existing.getUser() == null) {
            existing.setFullName(cleanFullName);
            patient = existing;
        } else {
            patient = new Patient();
            patient.setFullName(cleanFullName);
            patient.setPhone(cleanPhone);
        }
        patient = patientRepository.save(patient);

        Appointment appointment = new Appointment();
        appointment.setAppointmentCode("TMP-" + java.util.UUID.randomUUID());
        appointment.setPatient(patient);
        appointment.setDoctor(doctor);
        appointment.setReceptionist(receptionist);
        appointment.setAppointmentType("DOCTOR_CONSULTATION");
        appointment.setScheduledTime(scheduledAt);
        appointment.setBodyPart(cleanSymptom);
        appointment.setStatus("CONFIRMED");
        appointmentRepository.save(appointment);
        appointment.setAppointmentCode(nextCode("APT", appointment.getAppointmentId()));
        appointmentRepository.save(appointment);

        logStatusChange(appointment, "CONFIRMED", receptionist, "Đăng ký nhanh tại quầy lễ tân cho khách vãng lai.");

        String formattedTime = scheduledAt.format(TIME_FORMAT);
        notificationService.notifyRoleUsers(RECEPTIONIST_ROLE_NAMES, "Tiếp nhận Walk-in mới #" + appointment.getAppointmentCode(),
                "Đã đăng ký ca khám vãng lai cho bệnh nhân " + cleanFullName + " lúc " + formattedTime + ".", appointment.getAppointmentId());
        if (doctor != null) {
            notificationService.sendNotification(doctor, "Phân công ca khám vãng lai #" + appointment.getAppointmentCode(),
                    "Bạn có ca khám vãng lai mới với bệnh nhân " + cleanFullName + " lúc " + formattedTime + ".", appointment.getAppointmentId());
        }
        return appointment;
    }

    @Override
    @Transactional
    public Appointment cancelAppointment(Long appointmentId, String reason, String receptionistEmail) {
        Appointment appointment = getAppointmentOrThrow(appointmentId);
        if (TERMINAL_STATUSES.contains(appointment.getStatus())) {
            throw new InvalidFieldException("Lịch hẹn đã kết thúc, không thể hủy.");
        }
        String cleanReason = validateNote(reason, "Lý do hủy");
        User receptionist = findReceptionist(receptionistEmail);
        appointment.setReceptionist(receptionist);
        appointment.setStatus("CANCELLED");
        appointmentRepository.save(appointment);

        String note = "Lễ tân hủy lịch hẹn.";
        if (cleanReason != null) {
            note += " Lý do: " + cleanReason;
        }
        logStatusChange(appointment, "CANCELLED", receptionist, note);

        String formattedTime = appointment.getScheduledTime() != null ? appointment.getScheduledTime().format(TIME_FORMAT) : "";
        if (appointment.getDoctor() != null) {
            notificationService.sendNotification(appointment.getDoctor(), "Lịch hẹn đã bị hủy #" + appointment.getAppointmentCode(),
                    "Lịch hẹn khám lúc " + formattedTime + " đã bị hủy.", appointment.getAppointmentId());
        }
        if (appointment.getPatient() != null && appointment.getPatient().getUser() != null) {
            notificationService.sendNotification(appointment.getPatient().getUser(), "Lịch hẹn đã bị hủy #" + appointment.getAppointmentCode(),
                    "Lịch hẹn khám của bạn vào " + formattedTime + " đã bị hủy.", appointment.getAppointmentId());
        }
        return appointment;
    }

    @Override
    @Transactional
    public Appointment markMissed(Long appointmentId, String receptionistEmail) {
        Appointment appointment = getAppointmentOrThrow(appointmentId);
        if (!MISSABLE_STATUSES.contains(appointment.getStatus())) {
            throw new InvalidFieldException("Chỉ có thể đánh dấu vắng mặt cho lịch hẹn chưa check-in.");
        }
        User receptionist = findReceptionist(receptionistEmail);
        appointment.setReceptionist(receptionist);
        appointment.setStatus("MISSED");
        appointmentRepository.save(appointment);
        logStatusChange(appointment, "MISSED", receptionist, "Bệnh nhân không đến khám theo lịch hẹn.");
        return appointment;
    }

    @Override
    @Transactional
    public Appointment callNextPatient(String receptionistEmail) {
        User receptionist = findReceptionist(receptionistEmail);
        List<Appointment> waiting = appointmentRepository.findByStatusOrderByScheduledTimeAsc("CHECKED_IN");
        for (Appointment candidate : waiting) {
            int claimed = appointmentRepository.claimAppointment(
                    candidate.getAppointmentId(), "CHECKED_IN", "IN_PROGRESS", receptionist);
            if (claimed == 1) {
                Appointment appointment = getAppointmentOrThrow(candidate.getAppointmentId());
                logStatusChange(appointment, "IN_PROGRESS", receptionist, "Lễ tân gọi số, mời bệnh nhân vào phòng khám.");

                String patientName = appointment.getPatient() != null ? appointment.getPatient().getFullName() : "bệnh nhân";
                if (appointment.getDoctor() != null) {
                    notificationService.sendNotification(appointment.getDoctor(), "Mời bệnh nhân vào khám #" + appointment.getAppointmentCode(),
                            "Bệnh nhân " + patientName + " đang được mời vào phòng khám.", appointment.getAppointmentId());
                }
                if (appointment.getPatient() != null && appointment.getPatient().getUser() != null) {
                    notificationService.sendNotification(appointment.getPatient().getUser(), "Đến lượt khám #" + appointment.getAppointmentCode(),
                            "Mời bạn vào phòng khám.", appointment.getAppointmentId());
                }
                return appointment;
            }
        }
        throw new InvalidFieldException("Không có bệnh nhân nào đang chờ.");
    }

    @Override
    @Transactional
    public Appointment completeAppointment(Long appointmentId, String receptionistEmail) {
        Appointment appointment = getAppointmentOrThrow(appointmentId);
        if (!"IN_PROGRESS".equals(appointment.getStatus())) {
            throw new InvalidFieldException("Chỉ có thể hoàn tất lịch hẹn đang trong trạng thái khám.");
        }
        User receptionist = findReceptionist(receptionistEmail);
        appointment.setReceptionist(receptionist);
        appointment.setStatus("COMPLETED");
        appointmentRepository.save(appointment);
        logStatusChange(appointment, "COMPLETED", receptionist, "Hoàn tất buổi khám.");

        String patientName = appointment.getPatient() != null ? appointment.getPatient().getFullName() : "bệnh nhân";
        if (appointment.getPatient() != null && appointment.getPatient().getUser() != null) {
            notificationService.sendNotification(appointment.getPatient().getUser(), "Hoàn tất ca khám #" + appointment.getAppointmentCode(),
                    "Buổi khám của bạn đã hoàn tất. Cảm ơn bạn!", appointment.getAppointmentId());
        }
        notificationService.notifyRoleUsers(RECEPTIONIST_ROLE_NAMES, "Hoàn tất ca khám #" + appointment.getAppointmentCode(),
                "Ca khám của bệnh nhân " + patientName + " đã hoàn thành.", appointment.getAppointmentId());
        return appointment;
    }

    // ── Field validation ──────────────────────────────────────────────────

    private String validateFullName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            throw new InvalidFieldException("Vui lòng nhập họ và tên bệnh nhân.");
        }
        String trimmed = fullName.trim();
        if (!FULL_NAME_PATTERN.matcher(trimmed).matches()) {
            throw new InvalidFieldException("Họ và tên không hợp lệ (chỉ gồm chữ cái, 2-100 ký tự, không chứa số hoặc ký tự đặc biệt).");
        }
        return trimmed;
    }

    private String validatePhone(String phone) {
        if (phone == null || phone.isBlank()) {
            throw new InvalidFieldException("Vui lòng nhập số điện thoại.");
        }
        String trimmed = phone.trim().replaceAll("[\\s.-]", "");
        if (!PHONE_PATTERN.matcher(trimmed).matches()) {
            throw new InvalidFieldException("Số điện thoại không hợp lệ. Vui lòng nhập đúng định dạng số Việt Nam (VD: 0912345678).");
        }
        return trimmed;
    }

    private String validateSymptom(String symptom) {
        if (symptom == null || symptom.isBlank()) {
            return null;
        }
        String trimmed = symptom.trim();
        if (trimmed.length() > MAX_SYMPTOM_LENGTH) {
            throw new InvalidFieldException("Triệu chứng chính không được vượt quá " + MAX_SYMPTOM_LENGTH + " ký tự.");
        }
        return trimmed;
    }

    private String validateNote(String note, String fieldLabel) {
        if (note == null || note.isBlank()) {
            return null;
        }
        String trimmed = note.trim();
        if (trimmed.length() > MAX_NOTE_LENGTH) {
            throw new InvalidFieldException(fieldLabel + " không được vượt quá " + MAX_NOTE_LENGTH + " ký tự.");
        }
        return trimmed;
    }

    private LocalDate validateScheduledDate(LocalDate scheduledDate) {
        LocalDate date = scheduledDate != null ? scheduledDate : LocalDate.now();
        if (date.isBefore(LocalDate.now())) {
            throw new InvalidFieldException("Ngày khám không được ở trong quá khứ.");
        }
        if (date.isAfter(LocalDate.now().plusDays(MAX_FUTURE_BOOKING_DAYS))) {
            throw new InvalidFieldException(
                    "Chỉ có thể đặt lịch trong vòng " + MAX_FUTURE_BOOKING_DAYS + " ngày tới.");
        }
        return date;
    }

    private LocalTime validateScheduledTime(LocalTime scheduledTime) {
        LocalTime time = scheduledTime != null ? scheduledTime : roundUpToSlot(LocalTime.now());
        if (time.isBefore(CLINIC_OPEN) || time.isAfter(CLINIC_CLOSE)) {
            throw new InvalidFieldException(
                    "Giờ khám phải trong khung giờ hoạt động của phòng khám (" + CLINIC_OPEN + " - " + CLINIC_CLOSE + ").");
        }
        long minutesFromOpen = java.time.Duration.between(CLINIC_OPEN, time).toMinutes();
        if (minutesFromOpen % SLOT_MINUTES != 0) {
            throw new InvalidFieldException(
                    "Giờ khám phải chọn theo ca " + SLOT_MINUTES + " phút (VD: 08:00, 08:30), không nhập giờ lẻ.");
        }
        return time;
    }

    /** Rounds a raw clock time up to the next bookable slot boundary. */
    private LocalTime roundUpToSlot(LocalTime time) {
        if (time.isBefore(CLINIC_OPEN)) {
            return CLINIC_OPEN;
        }
        long minutesFromOpen = java.time.Duration.between(CLINIC_OPEN, time).toMinutes();
        long roundedUp = ((minutesFromOpen + SLOT_MINUTES - 1) / SLOT_MINUTES) * SLOT_MINUTES;
        LocalTime slot = CLINIC_OPEN.plusMinutes(roundedUp);
        return slot.isAfter(CLINIC_CLOSE) ? CLINIC_CLOSE : slot;
    }

    private User findDoctorOrThrow(Long doctorId) {
        User doctor = userRepository.findById(doctorId)
                .orElseThrow(() -> new InvalidFieldException("Không tìm thấy bác sĩ."));
        String roleName = doctor.getRole() != null ? doctor.getRole().getRoleName() : null;
        if (!"DOCTOR".equals(roleName) && !"ROLE_DOCTOR".equals(roleName)) {
            throw new InvalidFieldException(doctor.getFullName() + " không phải là bác sĩ.");
        }
        if (!"ACTIVE".equals(doctor.getStatus())) {
            throw new InvalidFieldException("BS. " + doctor.getFullName() + " hiện không hoạt động, không thể gán lịch hẹn.");
        }
        return doctor;
    }

    /**
     * Blocks scheduling a doctor within {@link #SLOT_MINUTES} minutes of
     * another active appointment they already have, so the receptionist
     * gets a clear conflict warning instead of silently double-booking.
     */
    private void ensureDoctorAvailable(User doctor, LocalDateTime scheduledTime, Long excludeAppointmentId) {
        if (doctor == null || scheduledTime == null) {
            return;
        }
        LocalDateTime from = scheduledTime.minusMinutes(SLOT_MINUTES);
        LocalDateTime to = scheduledTime.plusMinutes(SLOT_MINUTES);
        List<Appointment> nearby = appointmentRepository.findByDoctorAndScheduledTimeBetween(doctor, from, to);
        for (Appointment candidate : nearby) {
            if (excludeAppointmentId != null && candidate.getAppointmentId().equals(excludeAppointmentId)) {
                continue;
            }
            if (CONFLICT_IGNORED_STATUSES.contains(candidate.getStatus())) {
                continue;
            }
            String patientName = candidate.getPatient() != null ? candidate.getPatient().getFullName() : "một bệnh nhân khác";
            throw new DoctorScheduleConflictException(
                    "Trùng lịch: BS. " + doctor.getFullName() + " đã có lịch hẹn lúc "
                    + candidate.getScheduledTime().format(TIME_FORMAT) + " với " + patientName
                    + " (" + candidate.getAppointmentCode() + "). Vui lòng chọn giờ khác hoặc bác sĩ khác.");
        }
    }

    // ── Shared helpers ───────────────────────────────────────────────────

    private String nextCode(String prefix, long next) {
        return prefix + "-" + LocalDate.now().getYear() + "-" + String.format("%05d", next);
    }

    private Appointment getAppointmentOrThrow(Long appointmentId) {
        return appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new InvalidFieldException("Không tìm thấy lịch hẹn."));
    }

    private User findReceptionist(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidFieldException("Không tìm thấy tài khoản lễ tân."));
    }

    private void logStatusChange(Appointment appointment, String status, User actor, String note) {
        AppointmentStatusHistory history = new AppointmentStatusHistory();
        history.setAppointment(appointment);
        history.setStatus(status);
        history.setActor(actor);
        history.setNote(note);
        historyRepository.save(history);
    }
}
