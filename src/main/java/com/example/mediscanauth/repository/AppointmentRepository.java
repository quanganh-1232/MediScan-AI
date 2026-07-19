package com.example.mediscanauth.repository;

import com.example.mediscanauth.model.Appointment;
import com.example.mediscanauth.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {
    List<Appointment> findTop10ByOrderByScheduledTimeDesc();

    List<Appointment> findByStatusOrderByScheduledTimeAsc(String status);

    List<Appointment> findByDoctorUserIdOrderByScheduledTimeDesc(Long doctorId);

    List<Appointment> findByDoctorUserIdAndStatusOrderByScheduledTimeDesc(Long doctorId, String status);

    long countByStatus(String status);

    List<Appointment> findByPatientUserOrderByScheduledTimeDesc(User user);

    List<Appointment> findByScheduledTimeBetweenOrderByScheduledTimeAsc(LocalDateTime from, LocalDateTime to);

    long countByScheduledTimeBetween(LocalDateTime from, LocalDateTime to);

    long countByStatusIn(List<String> statuses);

    long countByTechnicianUserIdAndStatus(Long technicianId, String status);

    boolean existsByTechnicianUserId(Long technicianId);

    List<Appointment> findByDoctorAndScheduledTimeBetween(User doctor, LocalDateTime from, LocalDateTime to);

    /**
     * Atomically claims an appointment only if it's still in the expected
     * status, so two receptionists racing to "call next patient" can't both
     * succeed on the same row — the loser gets 0 rows affected instead of
     * silently overwriting the winner's claim.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Appointment a set a.status = :newStatus, a.receptionist = :receptionist " +
           "where a.appointmentId = :id and a.status = :expectedStatus")
    int claimAppointment(@Param("id") Long id,
                         @Param("expectedStatus") String expectedStatus,
                         @Param("newStatus") String newStatus,
                         @Param("receptionist") User receptionist);

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
