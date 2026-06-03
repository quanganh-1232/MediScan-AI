package com.example.mediscanauth.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "medical_records")
public class MedicalRecord {
    private String aiPrediction;
    private Double aiConfidence;

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

    public Long getRecordId() {
        return recordId;
    }

    public Patient getPatient() {
        return patient;
    }

    public void setPatient(Patient patient) {
        this.patient = patient;
    }

    public Appointment getAppointment() {
        return appointment;
    }

    public void setAppointment(Appointment appointment) {
        this.appointment = appointment;
    }

    public User getTechnician() {
        return technician;
    }

    public void setTechnician(User technician) {
        this.technician = technician;
    }

    public User getDoctor() {
        return doctor;
    }

    public void setDoctor(User doctor) {
        this.doctor = doctor;
    }

    public String getRecordCode() {
        return recordCode;
    }

    public void setRecordCode(String recordCode) {
        this.recordCode = recordCode;
    }

    public String getSymptomDescription() {
        return symptomDescription;
    }

    public void setSymptomDescription(String symptomDescription) {
        this.symptomDescription = symptomDescription;
    }

    public String getBodyPart() {
        return bodyPart;
    }

    public void setBodyPart(String bodyPart) {
        this.bodyPart = bodyPart;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    public String getAiPrediction() {
        return aiPrediction;
    }

    public void setAiPrediction(String aiPrediction) {
        this.aiPrediction = aiPrediction;
    }

    public Double getAiConfidence() {
        return aiConfidence;
    }

    public void setAiConfidence(Double aiConfidence) {
        this.aiConfidence = aiConfidence;
    }
    @OneToMany(mappedBy = "record")
    private java.util.List<XrayImage> xrayImages = new java.util.ArrayList<>();
    public java.util.List<XrayImage> getXrayImages() {
        return xrayImages;
    }
}
