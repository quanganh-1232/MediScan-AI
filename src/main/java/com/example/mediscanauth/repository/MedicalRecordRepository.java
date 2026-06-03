package com.example.mediscanauth.repository;

import com.example.mediscanauth.model.MedicalRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import com.example.mediscanauth.model.Patient;

import java.util.List;

public interface MedicalRecordRepository extends JpaRepository<MedicalRecord, Long> {
    List<MedicalRecord> findTop10ByOrderByCreatedAtDesc();

    long countByStatus(String status);

    // Lấy danh sách hồ sơ, cái nào mới tải lên thì xếp lên đầu
    java.util.List<MedicalRecord> findByPatientOrderByCreatedAtDesc(Patient patient);

    // ĐÃ SỬA: Đổi từ findById... thành findByRecordId...
    java.util.Optional<MedicalRecord> findByRecordIdAndPatient(Long recordId, Patient patient);
}