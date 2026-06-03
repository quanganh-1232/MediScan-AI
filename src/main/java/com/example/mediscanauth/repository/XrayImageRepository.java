package com.example.mediscanauth.repository;

import com.example.mediscanauth.model.XrayImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface XrayImageRepository extends JpaRepository<XrayImage, Long> {
    List<XrayImage> findTop10ByOrderByUploadedAtDesc();

    long countByStatus(String status);
    void deleteByRecord(com.example.mediscanauth.model.MedicalRecord record);
}
