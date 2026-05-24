package com.example.mediscanauth.service;

import com.example.mediscanauth.model.Appointment;
import com.example.mediscanauth.model.MedicalRecord;
import com.example.mediscanauth.model.XrayImage;

import java.time.LocalDateTime;
import java.util.List;

public interface TechnicianWorkflowService {

    List<Appointment> findRecentAppointments();

    List<Appointment> findScheduledAppointments();

    List<MedicalRecord> findRecentRecords();

    List<XrayImage> findRecentImages();

    long countScheduledAppointments();

    long countUploadedRecords();

    long countUploadedImages();

    long countSuccessfulAiResults();

    long countApprovedReviews();

    Appointment createAppointment(String technicianEmail, String patientEmail, String doctorEmail, LocalDateTime scheduledTime, String bodyPart, String location, String note);

    MedicalRecord uploadImageAndCreateRecord(String technicianEmail, Long appointmentId, String patientEmail, String doctorEmail, String symptomDescription, String bodyPart, String originalImagePath, String viewPosition);
}
