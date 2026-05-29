package com.example.mediscanauth.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "imaging_records")
public class ImagingRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "record_id")
    private Long recordId;

    @Column(name = "record_code", nullable = false, unique = true, length = 40)
    private String recordCode;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "patient_id", nullable = false)
    private User patient;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "doctor_id")
    private User doctor;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "technician_id")
    private User technician;

    @Column(name = "body_part", nullable = false, length = 120)
    private String bodyPart;

    @Column(name = "file_name", length = 180)
    private String fileName;

    @Column(name = "ai_prediction", length = 180)
    private String aiPrediction;

    @Column(name = "ai_confidence")
    private Integer aiConfidence;

    @Column(name = "doctor_conclusion", length = 500)
    private String doctorConclusion;

    @Column(name = "recommendation", length = 500)
    private String recommendation;

    @Column(name = "status", nullable = false, length = 40)
    private String status = "PENDING_AI";

    @Column(name = "captured_at")
    private LocalDate capturedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (capturedAt == null) {
            capturedAt = LocalDate.now();
        }
    }
}
