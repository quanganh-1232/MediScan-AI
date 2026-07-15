package com.example.mediscanauth.service;

import com.example.mediscanauth.model.ImagingRecord;
import com.example.mediscanauth.model.Patient;
import com.example.mediscanauth.model.User;
import com.example.mediscanauth.model.dto.DashboardDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface ImagingRecordService {

    // ==================== Patient related ====================
    List<ImagingRecord> findForPatient(User patient);
    ImagingRecord findLatestForPatient(User patient);
    long countForPatient(User patient);
    Patient getPatientProfile(User user);
    List<Patient> getAllPatients();
    Patient getPatientById(Long patientId);

    // ==================== Dashboard & Doctor specific ====================
    DashboardDTO getDoctorDashboardStats(Long doctorId);
    Long getDoctorIdByEmail(String email);
    List<DashboardDTO.QueueItemDTO> getPendingDTOsForDoctor(Long doctorId);
    List<DashboardDTO.QueueItemDTO> getCompletedDTOsForDoctor(Long doctorId);

    // ==================== Queue & Stats ====================
    long countQueue();
    long countToday();
    long countAll();
    List<ImagingRecord> findQueue();
    List<ImagingRecord> findRecent();

    // ==================== Record Operations ====================
    ImagingRecord createFromTechnician(String technicianEmail, String patientEmail, String bodyPart, String fileName);

    ImagingRecord captureAndAnalyzeFromTechnician(
            String technicianEmail,
            String patientEmail,
            String doctorEmail,
            MultipartFile image);

    ImagingRecord getRecordById(Long recordId);
    ImagingRecord getRecordDetail(Long recordId);

    ImagingRecord confirmDoctorReview(Long recordId, String doctorEmail, String conclusion, String recommendation);
    ImagingRecord rejectDoctorReview(Long recordId, String doctorEmail, String conclusion, String recommendation);
    ImagingRecord updateRecordCoordinates(Long recordId, Integer bboxX, Integer bboxY, Integer bboxWidth, Integer bboxHeight);

    // ==================== Search & AI ====================
    Page<ImagingRecord> searchConfirmedLibrary(String keyword, String bodyPart, Pageable pageable);
    Page<ImagingRecord> searchForPatient(User patient, String keyword, String bodyPart, Pageable pageable);
    
    List<com.example.mediscanauth.model.dto.AiRegionProjection> getAiRegionsByRecordId(Long recordId);

    // ==================== Others ====================
    List<ImagingRecord> findRecordsUploadedByTechnician(String technicianEmail);
    void deleteRecordForPatient(Long recordId, User patient);
    void clearNonConfirmedRecords();
}