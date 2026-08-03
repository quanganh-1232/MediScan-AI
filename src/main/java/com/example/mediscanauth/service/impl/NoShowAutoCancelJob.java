package com.example.mediscanauth.service.impl;

import com.example.mediscanauth.constant.OperationalConfig;
import com.example.mediscanauth.model.Appointment;
import com.example.mediscanauth.model.AppointmentStatusHistory;
import com.example.mediscanauth.repository.AppointmentRepository;
import com.example.mediscanauth.repository.AppointmentStatusHistoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Auto-cancels appointments reception never acted on: still PENDING or
 * CONFIRMED (i.e. never checked in) once their scheduled time is more than
 * {@link OperationalConfig#NO_SHOW_GRACE_MINUTES} minutes in the past. Runs
 * every minute; the grace window keeps it tolerant of normal front-desk
 * delays instead of cancelling the instant the clock ticks past the slot.
 */
@Component
public class NoShowAutoCancelJob {

    private static final Logger log = LoggerFactory.getLogger(NoShowAutoCancelJob.class);
    private static final List<String> AUTO_CANCELABLE_STATUSES = List.of("PENDING", "CONFIRMED");

    private final AppointmentRepository appointmentRepository;
    private final AppointmentStatusHistoryRepository historyRepository;

    public NoShowAutoCancelJob(AppointmentRepository appointmentRepository,
                               AppointmentStatusHistoryRepository historyRepository) {
        this.appointmentRepository = appointmentRepository;
        this.historyRepository = historyRepository;
    }

    @Scheduled(fixedRate = 60_000)
    @Transactional
    public void cancelNoShowAppointments() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(OperationalConfig.NO_SHOW_GRACE_MINUTES);
        List<Appointment> overdue = appointmentRepository
                .findByStatusInAndScheduledTimeBefore(AUTO_CANCELABLE_STATUSES, cutoff);

        for (Appointment appointment : overdue) {
            appointment.setStatus("CANCELLED");
            appointmentRepository.save(appointment);

            AppointmentStatusHistory history = new AppointmentStatusHistory();
            history.setAppointment(appointment);
            history.setStatus("CANCELLED");
            history.setNote("Tự động hủy: quá " + OperationalConfig.NO_SHOW_GRACE_MINUTES
                    + " phút so với giờ hẹn mà chưa được lễ tân xác nhận/check-in.");
            historyRepository.save(history);

            log.info("Auto-cancelled no-show appointment {} (scheduled {})",
                    appointment.getAppointmentCode(), appointment.getScheduledTime());
        }
    }
}
