package com.example.mediscanauth.service;

import com.example.mediscanauth.model.Appointment;
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

    // Patients this doctor actually has a case assigned to — scopes the
    // doctor's patient list instead of exposing every patient system-wide.
    List<Patient> getPatientsForDoctor(Long doctorId);

    // This doctor's own record history with one patient (excludes other
    // doctors' cases for the same patient).
    List<ImagingRecord> findForPatientAndDoctor(User patient, Long doctorId);

    Patient getPatientById(Long patientId);

    // ==================== Dashboard & Doctor specific ====================
    DashboardDTO getDoctorDashboardStats(Long doctorId);

    Long getDoctorIdByEmail(String email);

    List<DashboardDTO.QueueItemDTO> getPendingDTOsForDoctor(Long doctorId);

    List<DashboardDTO.QueueItemDTO> getCompletedDTOsForDoctor(Long doctorId);

    List<DashboardDTO.QueueItemDTO> getAllCompletedDTOs();

    // ==================== Queue & Stats ====================
    long countQueue();

    long countToday();

    long countAll();

    // Same as countToday()/countAll() but scoped to one doctor's own assigned
    // cases — used on the doctor's pending-queue page instead of system-wide totals.
    long countTodayForDoctor(Long doctorId);

    long countAllForDoctor(Long doctorId);

    List<ImagingRecord> findQueue();

    // Same as findQueue() but scoped to one doctor's assigned cases (plus
    // any legacy/unassigned ones) — used for the doctor's own pending list.
    List<ImagingRecord> findQueueForDoctor(Long doctorId);

    List<ImagingRecord> findRecent();

    // ==================== Record Operations ====================
    ImagingRecord createFromTechnician(String technicianEmail, String patientEmail, String bodyPart, String fileName);

    ImagingRecord captureAndAnalyzeFromTechnician(
            String technicianEmail,
            Long appointmentId,
            MultipartFile image);

    // Appointments already assigned a doctor by reception, ready for a
    // technician to capture — replaces manual doctor selection at capture time.
    List<Appointment> findAppointmentsEligibleForCapture();

    ImagingRecord getRecordById(Long recordId);

    // Like getRecordById, but throws if the record is already assigned to a
    // different doctor than doctorEmail — enforces "a doctor may only
    // view/act on cases assigned to them" at the service layer.
    ImagingRecord getRecordForDoctor(Long recordId, String doctorEmail);

    ImagingRecord getRecordDetail(Long recordId);

    ImagingRecord confirmDoctorReview(Long recordId, String doctorEmail, String conclusion, String recommendation,
            String screenshotData, String visibility);

    ImagingRecord rejectDoctorReview(Long recordId, String doctorEmail, String conclusion, String recommendation);

    ImagingRecord updateRecordCoordinates(Long recordId, Integer bboxX, Integer bboxY, Integer bboxWidth,
            Integer bboxHeight);

    // Lets the assigned doctor flip an already-COMPLETED record between
    // Private/Public from the diagnosis library, after the fact.
    ImagingRecord updateRecordVisibility(Long recordId, String doctorEmail, String visibility);

    // ==================== Search & AI ====================
    Page<ImagingRecord> searchConfirmedLibrary(String keyword, String bodyPart, Long doctorId, Pageable pageable);

    Page<ImagingRecord> searchForPatient(User patient, String keyword, String doctorName, String status, java.time.LocalDate fromDate, java.time.LocalDate toDate, Pageable pageable);

    List<com.example.mediscanauth.model.dto.AiRegionProjection> getAiRegionsByRecordId(Long recordId);

    // ==================== Others ====================
    List<ImagingRecord> findRecordsUploadedByTechnician(String technicianEmail);

    void deleteRecordForPatient(Long recordId, User patient);

    void clearNonConfirmedRecords();
}