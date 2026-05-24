package com.example.mediscanauth.repository;

import com.example.mediscanauth.model.ImagingRecord;
import com.example.mediscanauth.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
