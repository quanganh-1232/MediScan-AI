package com.example.mediscanauth.service;

import com.example.mediscanauth.model.ImagingRecord;
import com.example.mediscanauth.model.Patient;
import com.example.mediscanauth.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface ImagingRecordService {

    List<ImagingRecord> findForPatient(User patient);

    ImagingRecord findLatestForPatient(User patient);

    long countForPatient(User patient);

    long countQueue();

    long countToday();

    long countAll();

    List<ImagingRecord> findQueue();

    List<ImagingRecord> findRecent();

    ImagingRecord createFromTechnician(String technicianEmail, String patientEmail, String bodyPart, String fileName);

    ImagingRecord captureAndAnalyzeFromTechnician(
            String technicianEmail,
            String patientEmail,
            String doctorEmail,
            MultipartFile image);

    ImagingRecord confirmDoctorReview(Long recordId, String doctorEmail, String conclusion, String recommendation);

    ImagingRecord rejectDoctorReview(Long recordId, String doctorEmail, String conclusion, String recommendation);

    ImagingRecord updateRecordCoordinates(Long recordId, Integer bboxX, Integer bboxY, Integer bboxWidth, Integer bboxHeight);

    ImagingRecord getRecordById(Long recordId);

    List<ImagingRecord> findRecordsUploadedByTechnician(String technicianEmail);

    Page<ImagingRecord> searchConfirmedLibrary(String keyword, String bodyPart, Pageable pageable);

    Page<ImagingRecord> searchForPatient(User patient, String keyword, String bodyPart, Pageable pageable);

    void deleteRecordForPatient(Long recordId, User patient);

    void clearNonConfirmedRecords();
}
