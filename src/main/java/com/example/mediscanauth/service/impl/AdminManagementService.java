package com.example.mediscanauth.service.impl;

import com.example.mediscanauth.model.Appointment;
import com.example.mediscanauth.model.Patient;
import com.example.mediscanauth.model.User;
import com.example.mediscanauth.repository.AppointmentRepository;
import com.example.mediscanauth.repository.PatientRepository;
import com.example.mediscanauth.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
public class AdminManagementService {

    private static final List<String> APPOINTMENT_STATUSES =
            List.of("PENDING", "CONFIRMED", "COMPLETED", "CANCELLED");

    private final UserAdminService userAdminService;
    private final UserRepository userRepository;
    private final PatientRepository patientRepository;
    private final AppointmentRepository appointmentRepository;
    private final AuditLogService auditLogService;

    public AdminManagementService(UserAdminService userAdminService,
                                  UserRepository userRepository,
                                  PatientRepository patientRepository,
                                  AppointmentRepository appointmentRepository,
                                  AuditLogService auditLogService) {
        this.userAdminService = userAdminService;
        this.userRepository = userRepository;
        this.patientRepository = patientRepository;
        this.appointmentRepository = appointmentRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public List<Patient> findPatients(String keyword) {
        synchronizeMissingPatientProfiles();
        String query = normalize(keyword).toLowerCase(Locale.ROOT);
        return patientRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(patient -> query.isBlank()
                        || contains(patient.getFullName(), query)
                        || contains(patient.getPhone(), query)
                        || (patient.getUser() != null && contains(patient.getUser().getEmail(), query)))
                .toList();
    }

    @Transactional
    public Patient updatePatient(Long patientId, String fullName, String phone, String gender,
                                 String address, String medicalHistory) {
        Patient patient = requirePatient(patientId);
        if (normalize(fullName).isBlank()) {
            throw new IllegalArgumentException("Họ tên bệnh nhân không được để trống.");
        }
        String normalizedGender = normalize(gender).toUpperCase(Locale.ROOT);
        if (!List.of("MALE", "FEMALE", "OTHER").contains(normalizedGender)) {
            throw new IllegalArgumentException("Giới tính không hợp lệ.");
        }
        patient.setFullName(normalize(fullName));
        patient.setPhone(normalize(phone));
        patient.setGender(normalizedGender);
        patient.setAddress(normalize(address));
        patient.setMedicalHistory(normalize(medicalHistory));
        if (patient.getUser() != null) {
            patient.getUser().setFullName(patient.getFullName());
            patient.getUser().setPhone(patient.getPhone());
            userRepository.save(patient.getUser());
        }
        return patientRepository.save(patient);
    }

    @Transactional
    public void setPatientLocked(Long patientId, boolean locked, String currentAdminEmail) {
        Patient patient = requirePatient(patientId);
        if (patient.getUser() == null) {
            throw new IllegalArgumentException("Bệnh nhân này chưa có tài khoản để khóa.");
        }
        patient.getUser().setStatus(locked ? "LOCKED" : "ACTIVE");
        userRepository.save(patient.getUser());
        auditLogService.log(currentAdminEmail, locked ? "ACCOUNT_LOCKED" : "ACCOUNT_UNLOCKED",
                "Patient", String.valueOf(patientId),
                (locked ? "Khóa" : "Mở khóa") + " hồ sơ bệnh nhân " + patient.getFullName() + ".");
    }

    @Transactional(readOnly = true)
    public List<Appointment> findAppointments(String keyword, String status) {
        String query = normalize(keyword).toLowerCase(Locale.ROOT);
        String normalizedStatus = normalize(status).toUpperCase(Locale.ROOT);
        return appointmentRepository.findAll().stream()
                .filter(item -> normalizedStatus.isBlank() || statusMatches(item.getStatus(), normalizedStatus))
                .filter(item -> query.isBlank()
                        || contains(item.getAppointmentCode(), query)
                        || (item.getPatient() != null && contains(item.getPatient().getFullName(), query)))
                .sorted((left, right) -> right.getScheduledTime().compareTo(left.getScheduledTime()))
                .toList();
    }

    @Transactional
    public Appointment updateAppointment(Long appointmentId, LocalDateTime scheduledTime, String location,
                                         String note, String status, Long technicianId, Long doctorId,
                                         String currentAdminEmail) {
        Appointment appointment = requireAppointment(appointmentId);
        if (scheduledTime == null) {
            throw new IllegalArgumentException("Thời gian hẹn không được để trống.");
        }
        String previousStatus = appointment.getStatus();
        appointment.setScheduledTime(scheduledTime);
        appointment.setLocation(normalize(location));
        appointment.setNote(normalize(note));
        appointment.setStatus(normalizeAppointmentStatus(status));
        appointment.setTechnician(optionalActiveStaff(technicianId, "TECHNICIAN"));
        appointment.setDoctor(optionalActiveStaff(doctorId, "DOCTOR"));
        Appointment saved = appointmentRepository.save(appointment);
        auditLogService.log(currentAdminEmail, "APPOINTMENT_STATUS_CHANGED", "Appointment",
                String.valueOf(appointmentId),
                "Lịch hẹn " + saved.getAppointmentCode() + ": " + previousStatus + " -> " + saved.getStatus() + ".");
        return saved;
    }

    @Transactional
    public void assignTechnician(Long appointmentId, Long technicianId) {
        Appointment appointment = requireAppointment(appointmentId);
        appointment.setTechnician(optionalActiveStaff(technicianId, "TECHNICIAN"));
        appointmentRepository.save(appointment);
    }

    @Transactional
    public void cancelAppointment(Long appointmentId, String currentAdminEmail) {
        Appointment appointment = requireAppointment(appointmentId);
        appointment.setStatus("CANCELLED");
        appointmentRepository.save(appointment);
        auditLogService.log(currentAdminEmail, "APPOINTMENT_STATUS_CHANGED", "Appointment",
                String.valueOf(appointmentId),
                "Hủy lịch hẹn " + appointment.getAppointmentCode() + ".");
    }

    @Transactional(readOnly = true)
    public long completedCases(Long technicianId) {
        return appointmentRepository.countByTechnicianUserIdAndStatus(technicianId, "COMPLETED");
    }

    @Transactional
    public void deleteTechnician(Long technicianId, String currentAdminEmail) {
        if (appointmentRepository.existsByTechnicianUserId(technicianId)) {
            throw new IllegalArgumentException("Không thể xóa kỹ thuật viên đã được phân công. Hãy khóa tài khoản thay thế.");
        }
        userAdminService.deleteStaff(technicianId, "TECHNICIAN");
        auditLogService.log(currentAdminEmail, "STAFF_DELETED", "User", String.valueOf(technicianId),
                "Xóa tài khoản kỹ thuật viên.");
    }

    public List<String> appointmentStatuses() {
        return APPOINTMENT_STATUSES;
    }

    private void synchronizeMissingPatientProfiles() {
        userRepository.findByRoleRoleNameInOrderByFullNameAsc(List.of("PATIENT", "ROLE_PATIENT")).stream()
                .filter(user -> patientRepository.findByUser(user).isEmpty())
                .forEach(user -> {
                    Patient patient = new Patient();
                    patient.setUser(user);
                    patient.setFullName(user.getFullName());
                    patient.setPhone(user.getPhone());
                    patient.setGender("OTHER");
                    patientRepository.save(patient);
                });
    }

    private Patient requirePatient(Long patientId) {
        return patientRepository.findById(patientId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy bệnh nhân."));
    }

    private Appointment requireAppointment(Long appointmentId) {
        return appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy lịch hẹn."));
    }

    private User optionalActiveStaff(Long userId, String role) {
        if (userId == null) return null;
        User user = userAdminService.getUserDetail(userId);
        if (user.getRole() == null || !role.equals(user.getRole().getRoleName())) {
            throw new IllegalArgumentException("Nhân sự được chọn không đúng vai trò.");
        }
        if (!"ACTIVE".equals(user.getStatus())) {
            throw new IllegalArgumentException("Chỉ có thể phân công nhân sự đang hoạt động.");
        }
        return user;
    }

    private String normalizeAppointmentStatus(String status) {
        String normalized = normalize(status).toUpperCase(Locale.ROOT);
        if (!APPOINTMENT_STATUSES.contains(normalized)) {
            throw new IllegalArgumentException("Trạng thái lịch hẹn không hợp lệ.");
        }
        return normalized;
    }

    private boolean statusMatches(String actualStatus, String requestedStatus) {
        if (actualStatus == null) return false;
        return switch (requestedStatus) {
            case "PENDING" -> List.of("PENDING", "SCHEDULED").contains(actualStatus);
            case "CONFIRMED" -> List.of("CONFIRMED", "CHECKED_IN", "TRIAGED", "IN_PROGRESS").contains(actualStatus);
            case "COMPLETED" -> "COMPLETED".equals(actualStatus);
            case "CANCELLED" -> List.of("CANCELLED", "MISSED").contains(actualStatus);
            default -> false;
        };
    }

    private boolean contains(String value, String query) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(query);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
