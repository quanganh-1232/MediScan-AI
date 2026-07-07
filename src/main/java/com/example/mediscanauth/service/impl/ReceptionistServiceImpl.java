package com.example.mediscanauth.service.impl;

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
import java.util.Set;

@Service
public class ReceptionistServiceImpl implements ReceptionistService {

    private static final Set<String> CONFIRMABLE_STATUSES = Set.of("PENDING", "SCHEDULED");
    private static final Set<String> TERMINAL_STATUSES = Set.of("COMPLETED", "CANCELLED", "MISSED");

    private final AppointmentRepository appointmentRepository;
    private final AppointmentStatusHistoryRepository historyRepository;
    private final UserRepository userRepository;
    private final PatientRepository patientRepository;

    public ReceptionistServiceImpl(AppointmentRepository appointmentRepository,
                                   AppointmentStatusHistoryRepository historyRepository,
                                   UserRepository userRepository,
                                   PatientRepository patientRepository) {
        this.appointmentRepository = appointmentRepository;
        this.historyRepository = historyRepository;
        this.userRepository = userRepository;
        this.patientRepository = patientRepository;
    }

    @Override
    @Transactional
    public Appointment confirmAppointment(Long appointmentId, String receptionistEmail) {
        Appointment appointment = getAppointmentOrThrow(appointmentId);
        if (!CONFIRMABLE_STATUSES.contains(appointment.getStatus())) {
            throw new IllegalStateException("Lịch hẹn không ở trạng thái chờ xác nhận.");
        }
        User receptionist = findReceptionist(receptionistEmail);
        appointment.setReceptionist(receptionist);
        appointment.setStatus("CONFIRMED");
        appointmentRepository.save(appointment);
        logStatusChange(appointment, "CONFIRMED", receptionist, "Lễ tân xác nhận lịch hẹn.");
        return appointment;
    }

    @Override
    @Transactional
    public Appointment checkInAppointment(Long appointmentId, String receptionistEmail) {
        Appointment appointment = getAppointmentOrThrow(appointmentId);
        if (!"CONFIRMED".equals(appointment.getStatus())) {
            throw new IllegalStateException("Chỉ có thể check-in lịch hẹn đã được xác nhận.");
        }
        User receptionist = findReceptionist(receptionistEmail);
        appointment.setReceptionist(receptionist);
        appointment.setStatus("CHECKED_IN");
        appointmentRepository.save(appointment);
        logStatusChange(appointment, "CHECKED_IN", receptionist, "Bệnh nhân đã check-in tại quầy lễ tân.");
        return appointment;
    }

    @Override
    @Transactional
    public Appointment assignDoctor(Long appointmentId, Long doctorId, String note, String receptionistEmail) {
        Appointment appointment = getAppointmentOrThrow(appointmentId);
        if (TERMINAL_STATUSES.contains(appointment.getStatus())) {
            throw new IllegalStateException("Không thể đổi bác sĩ cho lịch hẹn đã kết thúc.");
        }
        User receptionist = findReceptionist(receptionistEmail);
        User doctor = userRepository.findById(doctorId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy bác sĩ."));

        User previousDoctor = appointment.getDoctor();
        appointment.setDoctor(doctor);
        appointment.setReceptionist(receptionist);
        appointmentRepository.save(appointment);

        String historyNote = previousDoctor != null
                ? "Chuyển từ BS. " + previousDoctor.getFullName() + " sang BS. " + doctor.getFullName() + "."
                : "Điều hướng đến BS. " + doctor.getFullName() + ".";
        if (note != null && !note.isBlank()) {
            historyNote += " Ghi chú: " + note.trim();
        }
        logStatusChange(appointment, appointment.getStatus(), receptionist, historyNote);
        return appointment;
    }

    @Override
    @Transactional
    public Appointment createWalkInAppointment(String fullName,
                                               String phone,
                                               String symptom,
                                               Long doctorId,
                                               LocalTime scheduledTime,
                                               String receptionistEmail) {
        if (fullName == null || fullName.isBlank()) {
            throw new IllegalArgumentException("Vui lòng nhập họ và tên bệnh nhân.");
        }
        if (phone == null || phone.isBlank()) {
            throw new IllegalArgumentException("Vui lòng nhập số điện thoại.");
        }
        User receptionist = findReceptionist(receptionistEmail);
        User doctor = doctorId != null
                ? userRepository.findById(doctorId).orElseThrow(() -> new IllegalArgumentException("Không tìm thấy bác sĩ."))
                : null;

        Patient patient = new Patient();
        patient.setFullName(fullName.trim());
        patient.setPhone(phone.trim());
        patient = patientRepository.save(patient);

        LocalTime time = scheduledTime != null ? scheduledTime : LocalTime.now();

        Appointment appointment = new Appointment();
        appointment.setAppointmentCode(nextCode("APT", appointmentRepository.count() + 1));
        appointment.setPatient(patient);
        appointment.setDoctor(doctor);
        appointment.setReceptionist(receptionist);
        appointment.setAppointmentType("DOCTOR_CONSULTATION");
        appointment.setScheduledTime(LocalDateTime.of(LocalDate.now(), time));
        appointment.setBodyPart(symptom != null && !symptom.isBlank() ? symptom.trim() : null);
        appointment.setStatus("CONFIRMED");
        appointmentRepository.save(appointment);

        logStatusChange(appointment, "CONFIRMED", receptionist, "Đăng ký nhanh tại quầy lễ tân cho khách vãng lai.");
        return appointment;
    }

    private String nextCode(String prefix, long next) {
        return prefix + "-" + LocalDate.now().getYear() + "-" + String.format("%05d", next);
    }

    private Appointment getAppointmentOrThrow(Long appointmentId) {
        return appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy lịch hẹn."));
    }

    private User findReceptionist(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài khoản lễ tân."));
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
