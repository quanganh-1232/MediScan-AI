package com.example.mediscanauth.repository;

import com.example.mediscanauth.model.Appointment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {
    List<Appointment> findTop10ByOrderByScheduledTimeDesc();

    List<Appointment> findByStatusOrderByScheduledTimeAsc(String status);

    long countByStatus(String status);
}
