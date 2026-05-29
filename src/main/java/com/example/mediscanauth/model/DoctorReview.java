package com.example.mediscanauth.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
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
}
