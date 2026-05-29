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
@Table(name = "medical_records")
public class MedicalRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "record_id")
    private Long recordId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "appointment_id")
    private Appointment appointment;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "technician_id")
    private User technician;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "doctor_id")
    private User doctor;

    @Column(name = "record_code", nullable = false, unique = true, length = 50)
    private String recordCode;

    @Column(name = "symptom_description", columnDefinition = "text")
    private String symptomDescription;

    @Column(name = "body_part", length = 100)
    private String bodyPart;

    @Column(name = "status", columnDefinition = "enum('DRAFT','UPLOADED','AI_PROCESSING','AI_ANALYZED','DOCTOR_REVIEWING','COMPLETED','REJECTED','NEED_REUPLOAD','AI_FAILED')")
    private String status = "DRAFT";

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;
}
