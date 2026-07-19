package com.example.mediscanauth.repository;

import com.example.mediscanauth.model.Appointment;
import com.example.mediscanauth.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {
    List<Appointment> findTop10ByOrderByScheduledTimeDesc();

    List<Appointment> findByStatusOrderByScheduledTimeAsc(String status);

    long countByStatus(String status);

    List<Appointment> findByPatientUserOrderByScheduledTimeDesc(User user);

    Page<Appointment> findByPatientUserOrderByScheduledTimeDesc(User user, Pageable pageable);

    List<Appointment> findByScheduledTimeBetweenOrderByScheduledTimeAsc(LocalDateTime from, LocalDateTime to);

    long countByScheduledTimeBetween(LocalDateTime from, LocalDateTime to);

    long countByStatusIn(List<String> statuses);

    /**
     * Kiểm tra bác sĩ có lịch trùng trong khoảng [from, to) không (bỏ qua lịch đã hủy/bỏ lỡ).
     */
    @Query("""
            select count(a) from Appointment a
            where a.doctor = :doctor
              and a.scheduledTime >= :from
              and a.scheduledTime < :to
              and a.status not in ('CANCELLED', 'MISSED')
            """)
    long countDoctorConflicts(@Param("doctor") User doctor,
                              @Param("from") LocalDateTime from,
                              @Param("to") LocalDateTime to);

    @Query(value = """
            select a from Appointment a
            where (:keyword is null or :keyword = ''
                or lower(a.appointmentCode) like lower(concat('%', :keyword, '%'))
                or lower(a.patient.fullName) like lower(concat('%', :keyword, '%'))
                or a.patient.phone like concat('%', :keyword, '%'))
              and (:dateFrom is null or a.scheduledTime >= :dateFrom)
              and (:dateTo is null or a.scheduledTime < :dateTo)
              and (:status is null or :status = '' or a.status = :status)
            order by a.scheduledTime desc
            """,
            countQuery = """
            select count(a) from Appointment a
            where (:keyword is null or :keyword = ''
                or lower(a.appointmentCode) like lower(concat('%', :keyword, '%'))
                or lower(a.patient.fullName) like lower(concat('%', :keyword, '%'))
                or a.patient.phone like concat('%', :keyword, '%'))
              and (:dateFrom is null or a.scheduledTime >= :dateFrom)
              and (:dateTo is null or a.scheduledTime < :dateTo)
              and (:status is null or :status = '' or a.status = :status)
            """)
    Page<Appointment> searchAppointments(@Param("keyword") String keyword,
                                         @Param("dateFrom") LocalDateTime dateFrom,
                                         @Param("dateTo") LocalDateTime dateTo,
                                         @Param("status") String status,
                                         Pageable pageable);
}
