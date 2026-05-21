package com.example.mediscanauth.repository;

import com.example.mediscanauth.model.AiAnalysisResult;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiAnalysisResultRepository extends JpaRepository<AiAnalysisResult, Long> {
    long countByStatus(String status);
}
