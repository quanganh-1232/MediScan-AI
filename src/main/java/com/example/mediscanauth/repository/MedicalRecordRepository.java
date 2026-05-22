package com.example.mediscanauth.repository;

import com.example.mediscanauth.model.MedicalRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MedicalRecordRepository extends JpaRepository<MedicalRecord, Long> {
    List<MedicalRecord> findTop10ByOrderByCreatedAtDesc();

    long countByStatus(String status);
}
