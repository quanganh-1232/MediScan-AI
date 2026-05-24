package com.example.mediscanauth.service;

import com.example.mediscanauth.model.ImagingRecord;
import com.example.mediscanauth.model.User;

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
}
