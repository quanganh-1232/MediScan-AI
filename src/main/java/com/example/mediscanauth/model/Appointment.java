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

}
