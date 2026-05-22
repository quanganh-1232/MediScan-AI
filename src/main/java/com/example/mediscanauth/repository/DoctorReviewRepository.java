package com.example.mediscanauth.repository;

import com.example.mediscanauth.model.DoctorReview;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DoctorReviewRepository extends JpaRepository<DoctorReview, Long> {
    long countByApprovalStatus(String approvalStatus);
}
