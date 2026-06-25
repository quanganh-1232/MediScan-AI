package com.example.mediscanauth.repository;

import com.example.mediscanauth.model.MedicalRecord;
import com.example.mediscanauth.model.Patient;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MedicalRecordRepository extends JpaRepository<MedicalRecord, Long> {
    List<MedicalRecord> findTop10ByOrderByCreatedAtDesc();

    long countByStatus(String status);

    @Query("SELECT r FROM MedicalRecord r WHERE r.patient = :patient " +
           "AND (:bodyPart IS NULL OR r.bodyPart = :bodyPart) " +
           "ORDER BY r.createdAt DESC")
    Page<MedicalRecord> findByPatientAndFilter(@Param("patient") Patient patient,
                                               @Param("bodyPart") String bodyPart,
                                               Pageable pageable);

    Optional<MedicalRecord> findByRecordIdAndPatient(Long recordId, Patient patient);
}