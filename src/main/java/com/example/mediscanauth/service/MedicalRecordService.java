package com.example.mediscanauth.service;

import com.example.mediscanauth.model.MedicalRecord;
import com.example.mediscanauth.model.User;
import org.springframework.data.domain.Page; // Đảm bảo import Page
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;

public interface MedicalRecordService {
    MedicalRecord createPatientSelfCheck(User currentUser, String bodyPart, String symptoms, MultipartFile file) throws IOException;

    Page<MedicalRecord> findPatientRecords(User currentUser, String bodyPart, int page, int size);

    MedicalRecord getRecordDetail(Long id, User currentUser);
    void deleteRecord(Long id, User currentUser);
    void simulateAiProcessing(Long id, User currentUser);
}