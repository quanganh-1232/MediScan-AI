package com.example.mediscanauth.service;

import com.example.mediscanauth.model.MedicalRecord;
import com.example.mediscanauth.model.User;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface MedicalRecordService {
    MedicalRecord createPatientSelfCheck(User currentUser, String bodyPart, String symptoms, MultipartFile file) throws IOException;
    java.util.List<MedicalRecord> findPatientRecords(User currentUser);
    MedicalRecord getRecordDetail(Long id, User currentUser);
    void deleteRecord(Long id, User currentUser);
    void simulateAiProcessing(Long id, User currentUser); // Hàm giả lập AI
}