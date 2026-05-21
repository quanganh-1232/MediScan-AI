package com.example.mediscanauth.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "appointments")
public class Appointment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "appointment_id")
    private Long appointmentId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "technician_id")
    private User technician;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "doctor_id")
    private User doctor;

    @Column(name = "appointment_code", nullable = false, unique = true, length = 50)
    private String appointmentCode;

    @Column(name = "appointment_type", columnDefinition = "enum('XRAY_SCAN','DOCTOR_CONSULTATION','FOLLOW_UP')")
    private String appointmentType = "XRAY_SCAN";

    @Column(name = "scheduled_time", nullable = false)
    private LocalDateTime scheduledTime;

    @Column(name = "location")
    private String location;

    @Column(name = "body_part", length = 100)
    private String bodyPart;

    @Column(name = "status", columnDefinition = "enum('SCHEDULED','COMPLETED','CANCELLED','MISSED')")
    private String status = "SCHEDULED";

    @Column(name = "note", columnDefinition = "text")
    private String note;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    public Long getAppointmentId() {
        return appointmentId;
    }

    public Patient getPatient() {
        return patient;
    }

    public void setPatient(Patient patient) {
        this.patient = patient;
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

    public String getAppointmentCode() {
        return appointmentCode;
    }

    public void setAppointmentCode(String appointmentCode) {
        this.appointmentCode = appointmentCode;
    }

    public String getAppointmentType() {
        return appointmentType;
    }

    public void setAppointmentType(String appointmentType) {
        this.appointmentType = appointmentType;
    }

    public LocalDateTime getScheduledTime() {
        return scheduledTime;
    }

    public void setScheduledTime(LocalDateTime scheduledTime) {
        this.scheduledTime = scheduledTime;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
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

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
