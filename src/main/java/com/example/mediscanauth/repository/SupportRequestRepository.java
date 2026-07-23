package com.example.mediscanauth.repository;

import com.example.mediscanauth.model.SupportRequest;
import com.example.mediscanauth.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface SupportRequestRepository extends JpaRepository<SupportRequest, Long>,
        JpaSpecificationExecutor<SupportRequest> {
    List<SupportRequest> findByPatientOrderByCreatedAtDesc(User patient);
    long countByStatus(String status);
}
