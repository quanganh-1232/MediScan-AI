package com.example.mediscanauth.service.impl;

import com.example.mediscanauth.model.ImagingRecord;
import com.example.mediscanauth.model.User;
import com.example.mediscanauth.repository.ImagingRecordRepository;
import com.example.mediscanauth.service.ImagingRecordService;
import com.example.mediscanauth.service.UserAccountService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class ImagingRecordServiceImpl implements ImagingRecordService {

    private static final List<String> ACTIVE_QUEUE_STATUSES = List.of("PENDING_AI", "AI_DONE", "PENDING_DOCTOR");

    private final ImagingRecordRepository imagingRecordRepository;
    private final UserAccountService userAccountService;

    public ImagingRecordServiceImpl(ImagingRecordRepository imagingRecordRepository,
                                    UserAccountService userAccountService) {
        this.imagingRecordRepository = imagingRecordRepository;
        this.userAccountService = userAccountService;
    }

    @Override
    public List<ImagingRecord> findForPatient(User patient) {
        return imagingRecordRepository.findByPatientOrderByCapturedAtDescCreatedAtDesc(patient);
    }

    @Override
    public ImagingRecord findLatestForPatient(User patient) {
        return imagingRecordRepository.findFirstByPatientOrderByCapturedAtDescCreatedAtDesc(patient).orElse(null);
    }

    @Override
    public long countForPatient(User patient) {
        return imagingRecordRepository.countByPatient(patient);
    }

    @Override
    public long countQueue() {
        return imagingRecordRepository.countByStatusIn(ACTIVE_QUEUE_STATUSES);
    }

    @Override
    public long countToday() {
        return imagingRecordRepository.countByCapturedAt(LocalDate.now());
    }

    @Override
    public long countAll() {
        return imagingRecordRepository.count();
    }

    @Override
    public List<ImagingRecord> findQueue() {
        return imagingRecordRepository.findByStatusInOrderByCreatedAtDesc(ACTIVE_QUEUE_STATUSES);
    }

    @Override
    public List<ImagingRecord> findRecent() {
        return imagingRecordRepository.findTop10ByOrderByCreatedAtDesc();
    }

    @Override
    @Transactional
    public ImagingRecord createFromTechnician(String technicianEmail,
                                              String patientEmail,
                                              String bodyPart,
                                              String fileName) {
        User technician = userAccountService.findByEmail(technicianEmail);
        User patient = userAccountService.findByEmail(patientEmail);

        ImagingRecord record = new ImagingRecord();
        record.setRecordCode(nextRecordCode());
        record.setPatient(patient);
        record.setTechnician(technician);
        record.setBodyPart(bodyPart);
        record.setFileName(fileName);
        record.setAiPrediction("Chờ AI phân tích");
        record.setAiConfidence(0);
        record.setStatus("PENDING_AI");
        return imagingRecordRepository.save(record);
    }

    private String nextRecordCode() {
        long next = imagingRecordRepository.count() + 1;
        return "XR-" + LocalDate.now().getYear() + "-" + String.format("%04d", next);
    }
}