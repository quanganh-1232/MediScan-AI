package com.example.mediscanauth.service;

import com.example.mediscanauth.model.ImagingRecord;
import com.example.mediscanauth.model.Patient;
import com.example.mediscanauth.model.User;
import com.example.mediscanauth.model.dto.DashboardDTO;

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

    DashboardDTO getDoctorDashboardStats(Long doctorId);

    Long getDoctorIdByEmail(String email);

    List<DashboardDTO.QueueItemDTO> getPendingDTOsForDoctor(Long doctorId);

    ImagingRecord getRecordDetail(Long recordId);

    Patient getPatientProfile(User user);

    List<Patient> getAllPatients();

    Patient getPatientById(Long patientId);

    List<com.example.mediscanauth.model.dto.AiRegionProjection> getAiRegionsByRecordId(Long recordId);

    List<DashboardDTO.QueueItemDTO> getCompletedDTOsForDoctor(Long doctorId);
}