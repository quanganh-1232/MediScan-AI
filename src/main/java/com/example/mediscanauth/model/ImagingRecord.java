package com.example.mediscanauth.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

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

    public Long getRecordId() {
        return recordId;
    }

    public String getRecordCode() {
        return recordCode;
    }

    public void setRecordCode(String recordCode) {
        this.recordCode = recordCode;
    }

    public User getPatient() {
        return patient;
    }

    public void setPatient(User patient) {
        this.patient = patient;
    }

    public User getDoctor() {
        return doctor;
    }

    public void setDoctor(User doctor) {
        this.doctor = doctor;
    }

    public User getTechnician() {
        return technician;
    }

    public void setTechnician(User technician) {
        this.technician = technician;
    }

    public String getBodyPart() {
        return bodyPart;
    }

    public void setBodyPart(String bodyPart) {
        this.bodyPart = bodyPart;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getAiPrediction() {
        return aiPrediction;
    }

    public void setAiPrediction(String aiPrediction) {
        this.aiPrediction = aiPrediction;
    }

    public Integer getAiConfidence() {
        return aiConfidence;
    }

    public void setAiConfidence(Integer aiConfidence) {
        this.aiConfidence = aiConfidence;
    }

    public String getDoctorConclusion() {
        return doctorConclusion;
    }

    public void setDoctorConclusion(String doctorConclusion) {
        this.doctorConclusion = doctorConclusion;
    }

    public String getRecommendation() {
        return recommendation;
    }

    public void setRecommendation(String recommendation) {
        this.recommendation = recommendation;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDate getCapturedAt() {
        return capturedAt;
    }

    public void setCapturedAt(LocalDate capturedAt) {
        this.capturedAt = capturedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
