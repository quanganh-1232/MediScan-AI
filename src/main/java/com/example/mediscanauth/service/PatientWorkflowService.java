package com.example.mediscanauth.service;

import com.example.mediscanauth.model.Appointment;
import com.example.mediscanauth.model.ImagingRecord;
import com.example.mediscanauth.model.Patient;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;

public interface PatientWorkflowService {
    
    ImagingRecord uploadImageAndAnalyze(String patientEmail, String bodyPart, MultipartFile file);
    
    Appointment bookAppointment(String patientEmail, Long doctorId, String date, String time);

    void cancelAppointment(String patientEmail, Long appointmentId);

    Patient updatePatientProfile(String patientEmail, String fullName, String phone,
                                 String gender, LocalDate dateOfBirth,
                                 String address, String medicalHistory);
}
