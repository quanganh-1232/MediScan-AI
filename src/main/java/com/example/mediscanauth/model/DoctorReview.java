package com.example.mediscanauth.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "doctor_reviews")
public class DoctorReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "review_id")
    private Long reviewId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "record_id", nullable = false)
    private MedicalRecord record;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "doctor_id", nullable = false)
    private User doctor;

    @Column(name = "diagnosis", nullable = false, columnDefinition = "text")
    private String diagnosis;

    @Column(name = "conclusion", nullable = false, columnDefinition = "text")
    private String conclusion;

    @Column(name = "approval_status", nullable = false, columnDefinition = "enum('APPROVED','REJECTED','NEED_REUPLOAD')")
    private String approvalStatus;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    public Long getReviewId() {
        return reviewId;
    }

    public MedicalRecord getRecord() {
        return record;
    }

    public User getDoctor() {
        return doctor;
    }

    public String getDiagnosis() {
        return diagnosis;
    }

    public String getConclusion() {
        return conclusion;
    }

    public String getApprovalStatus() {
        return approvalStatus;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
