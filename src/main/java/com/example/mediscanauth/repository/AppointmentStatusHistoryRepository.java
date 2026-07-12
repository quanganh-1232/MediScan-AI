package com.example.mediscanauth.repository;

import com.example.mediscanauth.model.Appointment;
import com.example.mediscanauth.model.AppointmentStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AppointmentStatusHistoryRepository extends JpaRepository<AppointmentStatusHistory, Long> {
    List<AppointmentStatusHistory> findByAppointmentOrderByCreatedAtAsc(Appointment appointment);
}
