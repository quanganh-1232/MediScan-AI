package com.example.mediscanauth.repository;

import com.example.mediscanauth.model.ImagingRecord;
import com.example.mediscanauth.model.User;
import com.example.mediscanauth.model.dto.AiRegionProjection;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ImagingRecordRepository extends JpaRepository<ImagingRecord, Long> {
    List<ImagingRecord> findByPatientOrderByCapturedAtDescCreatedAtDesc(User patient);

    Optional<ImagingRecord> findFirstByPatientOrderByCapturedAtDescCreatedAtDesc(User patient);

    long countByPatient(User patient);

    long countByStatus(String status);

    long countByStatusIn(List<String> statuses);

    long countByCapturedAt(LocalDate capturedAt);

    List<ImagingRecord> findByStatusInOrderByCreatedAtDesc(List<String> statuses);

    List<ImagingRecord> findTop10ByOrderByCreatedAtDesc();

    List<ImagingRecord> findByStatusInAndDoctorUserIdOrderByCreatedAtDesc(List<String> statuses, Long userId);

    long countByStatusInAndDoctorUserId(List<String> statuses, Long userId);

    List<ImagingRecord> findByDoctorUserIdAndStatusInOrderByCreatedAtDesc(Long userId, List<String> statuses);

    // Câu truy vấn kết nối các bảng để lấy đúng tọa độ dựa vào record_id của bác
    @Query(value = "SELECT r.x_coordinate as xCoordinate, r.y_coordinate as yCoordinate, " +
                   "r.width as width, r.height as height, r.label as label " +
                   "FROM ai_detected_regions r " +
                   "JOIN ai_analysis_results res ON r.ai_result_id = res.ai_result_id " +
                   "JOIN xray_images img ON res.image_id = img.image_id " +
                   "WHERE img.record_id = :recordId", nativeQuery = true)
    List<AiRegionProjection> findAiRegionsByRecordId(@Param("recordId") Long recordId);
}
