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

    @Query("select a from Appointment a left join fetch a.patient left join fetch a.doctor " +
           "where a.status = :status order by a.scheduledTime asc")
    List<Appointment> findByStatusOrderByScheduledTimeAsc(@Param("status") String status);

    List<Appointment> findByStatusOrderByQueueNumberAsc(String status);

    List<Appointment> findByDoctorUserIdOrderByScheduledTimeDesc(Long doctorId);

    List<Appointment> findByDoctorUserIdAndStatusOrderByScheduledTimeDesc(Long doctorId, String status);

    long countByStatus(String status);

    List<Appointment> findByPatientUserOrderByScheduledTimeDesc(User user);

    Page<Appointment> findByPatientUserOrderByScheduledTimeDesc(User user, Pageable pageable);

    Page<Appointment> findByPatientUserAndStatusInOrderByScheduledTimeDesc(User user, List<String> statuses, Pageable pageable);

    @Query("select a from Appointment a left join fetch a.patient left join fetch a.doctor " +
           "where a.scheduledTime between :from and :to order by a.scheduledTime asc")
    List<Appointment> findByScheduledTimeBetweenOrderByScheduledTimeAsc(@Param("from") LocalDateTime from,
                                                                        @Param("to") LocalDateTime to);

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

    /**
     * Kiểm tra bệnh nhân đã có lịch hẹn trong khoảng [from, to) chưa (dành cho bệnh nhân có tài khoản).
     */
    @Query("""
            select count(a) from Appointment a
            where a.patient.user = :patientUser
              and a.scheduledTime >= :from
              and a.scheduledTime < :to
              and a.status not in ('CANCELLED', 'MISSED')
            """)
    long countPatientConflicts(@Param("patientUser") User patientUser,
                               @Param("from") LocalDateTime from,
                               @Param("to") LocalDateTime to);

    /**
     * Kiểm tra bệnh nhân đã có lịch hẹn trong khoảng [from, to) chưa (dành cho thực thể Patient nói chung).
     */
    @Query("""
            select count(a) from Appointment a
            where a.patient = :patient
              and a.scheduledTime >= :from
              and a.scheduledTime < :to
              and a.status not in ('CANCELLED', 'MISSED')
            """)
    long countPatientConflictsByPatient(@Param("patient") com.example.mediscanauth.model.Patient patient,
                                        @Param("from") LocalDateTime from,
                                        @Param("to") LocalDateTime to);

    long countByTechnicianUserIdAndStatus(Long technicianId, String status);

    @Query("SELECT COALESCE(MAX(a.queueNumber), 0) FROM Appointment a WHERE a.scheduledTime >= :startOfDay AND a.scheduledTime < :nextDay")
    Integer findMaxQueueNumberForDate(@Param("startOfDay") LocalDateTime startOfDay, @Param("nextDay") LocalDateTime nextDay);

    boolean existsByTechnicianUserId(Long technicianId);

    /**
     * Appointments ready for a technician to capture an X-ray for: a doctor
     * has already been assigned (by reception at booking time) and the
     * patient has a real login account (imaging records require a User, not
     * just a bare walk-in Patient profile).
     */
    @Query("select a from Appointment a left join fetch a.patient p left join fetch p.user left join fetch a.doctor " +
           "where a.status in :statuses and a.doctor is not null and p.user is not null " +
           "order by a.scheduledTime asc")
    List<Appointment> findEligibleForImagingCapture(@Param("statuses") List<String> statuses);

    List<Appointment> findByDoctorAndScheduledTimeBetween(User doctor, LocalDateTime from, LocalDateTime to);

    /**
     * Appointments reception never acted on in time — still PENDING/CONFIRMED
     * (never checked in) with a scheduled time already past the no-show grace
     * cutoff. Used by NoShowAutoCancelJob.
     */
    List<Appointment> findByStatusInAndScheduledTimeBefore(List<String> statuses, LocalDateTime cutoff);

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
            left join fetch a.patient p
            left join fetch a.doctor
            where (:keyword is null or :keyword = ''
                or lower(a.appointmentCode) like lower(concat('%', :keyword, '%'))
                or lower(p.fullName) like lower(concat('%', :keyword, '%'))
                or p.phone like concat('%', :keyword, '%'))
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
