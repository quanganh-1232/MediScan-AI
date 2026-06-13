package com.example.mediscanauth.service;

import com.example.mediscanauth.model.ImagingRecord;
import org.springframework.web.multipart.MultipartFile;

public interface PatientWorkflowService {
    
    ImagingRecord uploadImageAndAnalyze(String patientEmail, String bodyPart, MultipartFile file);
}
