package com.example.mediscanauth.repository;

import com.example.mediscanauth.model.Appointment;
import com.example.mediscanauth.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {
    List<Appointment> findTop10ByOrderByScheduledTimeDesc();

    List<Appointment> findByStatusOrderByScheduledTimeAsc(String status);

    long countByStatus(String status);

    List<Appointment> findByPatientUserOrderByScheduledTimeDesc(User user);

    List<Appointment> findByScheduledTimeBetweenOrderByScheduledTimeAsc(LocalDateTime from, LocalDateTime to);

    long countByScheduledTimeBetween(LocalDateTime from, LocalDateTime to);

    long countByStatusIn(List<String> statuses);

    long countByTechnicianUserIdAndStatus(Long technicianId, String status);

    boolean existsByTechnicianUserId(Long technicianId);
}
