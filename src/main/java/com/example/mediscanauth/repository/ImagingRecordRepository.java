package com.example.mediscanauth.repository;

import com.example.mediscanauth.model.ImagingRecord;
import com.example.mediscanauth.model.User;
import com.example.mediscanauth.model.dto.AiRegionProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ImagingRecordRepository extends JpaRepository<ImagingRecord, Long> {

    // ==================== Các method cũ ====================
    List<ImagingRecord> findByPatientOrderByCapturedAtDescCreatedAtDesc(User patient);
    Optional<ImagingRecord> findFirstByPatientOrderByCapturedAtDescCreatedAtDesc(User patient);
    long countByPatient(User patient);
    long countByStatus(String status);
    long countByStatusIn(List<String> statuses);
    long countByCapturedAt(LocalDate capturedAt);
    List<ImagingRecord> findByStatusInOrderByCreatedAtDesc(List<String> statuses);
    List<ImagingRecord> findTop10ByOrderByCreatedAtDesc();
    List<ImagingRecord> findByTechnicianEmailOrderByCreatedAtDesc(String technicianEmail);
    void deleteByStatusIn(List<String> statuses);

    // ==================== Doctor specific ====================
    List<ImagingRecord> findByStatusInAndDoctorUserIdOrderByCreatedAtDesc(List<String> statuses, Long userId);
    long countByStatusInAndDoctorUserId(List<String> statuses, Long userId);
    List<ImagingRecord> findByDoctorUserIdAndStatusInOrderByCreatedAtDesc(Long userId, List<String> statuses);

    // ==================== AI Diagnosis Monitoring ====================
    List<ImagingRecord> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);
    long countByDoctorOverrodeAiTrue();
    long countByDoctorOverrodeAiFalse();
    long countByStatusInAndDoctorOverrodeAiIsNull(List<String> statuses);

    @Query("select r.riskLevel, count(r) from ImagingRecord r where r.riskLevel is not null group by r.riskLevel")
    List<Object[]> countGroupByRiskLevel();

    @Query("select r.capturedAt, avg(r.aiConfidence) from ImagingRecord r " +
           "where r.aiConfidence is not null and r.capturedAt >= :since " +
           "group by r.capturedAt order by r.capturedAt")
    List<Object[]> avgConfidenceByDateSince(@Param("since") LocalDate since);

    // ==================== Search & Pagination ====================
    @Query(value = """
            select r from ImagingRecord r
            where r.status = 'DOCTOR_CONFIRMED'
              and (:bodyPart is null or :bodyPart = '' or lower(r.bodyPart) like lower(concat('%', :bodyPart, '%')))
              and (:keyword is null or :keyword = ''
                or lower(r.recordCode) like lower(concat('%', :keyword, '%'))
                or lower(r.bodyPart) like lower(concat('%', :keyword, '%'))
                or lower(r.aiPrediction) like lower(concat('%', :keyword, '%'))
                or lower(r.doctorConclusion) like lower(concat('%', :keyword, '%'))
                or lower(r.patient.fullName) like lower(concat('%', :keyword, '%'))
                or lower(r.patient.email) like lower(concat('%', :keyword, '%')))
            """,
            countQuery = """
            select count(r) from ImagingRecord r
            where r.status = 'DOCTOR_CONFIRMED'
              and (:bodyPart is null or :bodyPart = '' or lower(r.bodyPart) like lower(concat('%', :bodyPart, '%')))
              and (:keyword is null or :keyword = ''
                or lower(r.recordCode) like lower(concat('%', :keyword, '%'))
                or lower(r.bodyPart) like lower(concat('%', :keyword, '%'))
                or lower(r.aiPrediction) like lower(concat('%', :keyword, '%'))
                or lower(r.doctorConclusion) like lower(concat('%', :keyword, '%'))
                or lower(r.patient.fullName) like lower(concat('%', :keyword, '%'))
                or lower(r.patient.email) like lower(concat('%', :keyword, '%')))
            """)
    Page<ImagingRecord> searchConfirmedLibrary(@Param("keyword") String keyword,
                                               @Param("bodyPart") String bodyPart,
                                               Pageable pageable);

    @Query(value = """
            select r from ImagingRecord r
            where r.patient = :patient
              and (:bodyPart is null or :bodyPart = '' or r.bodyPart = :bodyPart)
              and (:keyword is null or :keyword = ''
                or lower(r.recordCode) like lower(concat('%', :keyword, '%'))
                or lower(r.aiPrediction) like lower(concat('%', :keyword, '%'))
                or lower(r.doctorConclusion) like lower(concat('%', :keyword, '%')))
            ORDER BY r.capturedAt DESC, r.createdAt DESC
            """,
            countQuery = """
            select count(r) from ImagingRecord r
            where r.patient = :patient
              and (:bodyPart is null or :bodyPart = '' or r.bodyPart = :bodyPart)
              and (:keyword is null or :keyword = ''
                or lower(r.recordCode) like lower(concat('%', :keyword, '%'))
                or lower(r.aiPrediction) like lower(concat('%', :keyword, '%'))
                or lower(r.doctorConclusion) like lower(concat('%', :keyword, '%')))
            """)
    Page<ImagingRecord> searchForPatient(@Param("patient") User patient,
                                         @Param("keyword") String keyword,
                                         @Param("bodyPart") String bodyPart,
                                         Pageable pageable);

    // ==================== AI Regions ====================
    @Query(value = "SELECT r.x_coordinate as xCoordinate, r.y_coordinate as yCoordinate, " +
                   "r.width as width, r.height as height, r.label as label " +
                   "FROM ai_detected_regions r " +
                   "JOIN ai_analysis_results res ON r.ai_result_id = res.ai_result_id " +
                   "JOIN xray_images img ON res.image_id = img.image_id " +
                   "WHERE img.record_id = :recordId", nativeQuery = true)
    List<AiRegionProjection> findAiRegionsByRecordId(@Param("recordId") Long recordId);

    // Các method khác nếu cần...
}