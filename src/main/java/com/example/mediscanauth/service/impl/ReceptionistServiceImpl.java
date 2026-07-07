package com.example.mediscanauth.service.impl;

import com.example.mediscanauth.model.Appointment;
import com.example.mediscanauth.model.AppointmentStatusHistory;
import com.example.mediscanauth.model.User;
import com.example.mediscanauth.repository.AppointmentRepository;
import com.example.mediscanauth.repository.AppointmentStatusHistoryRepository;
import com.example.mediscanauth.repository.UserRepository;
import com.example.mediscanauth.service.ReceptionistService;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class ReceptionistServiceImpl implements ReceptionistService {

    private static final Set<String> CONFIRMABLE_STATUSES = Set.of("PENDING", "SCHEDULED");

    private final AppointmentRepository appointmentRepository;
    private final AppointmentStatusHistoryRepository historyRepository;
    private final UserRepository userRepository;

    public ReceptionistServiceImpl(AppointmentRepository appointmentRepository,
                                   AppointmentStatusHistoryRepository historyRepository,
                                   UserRepository userRepository) {
        this.appointmentRepository = appointmentRepository;
        this.historyRepository = historyRepository;
        this.userRepository = userRepository;
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
