package com.example.mediscanauth.service.impl;

import com.example.mediscanauth.model.ImagingRecord;
import com.example.mediscanauth.model.Patient;
import com.example.mediscanauth.model.User;
import com.example.mediscanauth.model.dto.DashboardDTO;
import com.example.mediscanauth.repository.ImagingRecordRepository;
import com.example.mediscanauth.repository.PatientRepository;
import com.example.mediscanauth.repository.UserRepository;
import com.example.mediscanauth.service.ImagingRecordService;
import com.example.mediscanauth.service.UserAccountService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class ImagingRecordServiceImpl implements ImagingRecordService {

    private static final List<String> ACTIVE_QUEUE_STATUSES = List.of("AI_ANALYZED", "DOCTOR_REVIEWING", "PENDING_DOCTOR");

    private final ImagingRecordRepository imagingRecordRepository;
    private final UserAccountService userAccountService;
    private final PatientRepository patientRepository;
    private final UserRepository userRepository;

    public ImagingRecordServiceImpl(ImagingRecordRepository imagingRecordRepository,
                                    UserAccountService userAccountService,
                                    PatientRepository patientRepository,
                                    UserRepository userRepository) {
        this.imagingRecordRepository = imagingRecordRepository;
        this.userAccountService = userAccountService;
        this.patientRepository = patientRepository;
        this.userRepository = userRepository;
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

    @Override
    public DashboardDTO getDoctorDashboardStats(Long doctorId) {
        List<ImagingRecord> records = imagingRecordRepository
                .findByStatusInAndDoctorUserIdOrderByCreatedAtDesc(ACTIVE_QUEUE_STATUSES, doctorId);

        List<DashboardDTO.QueueItemDTO> queueItems = records.stream()
                .map(record -> DashboardDTO.QueueItemDTO.builder()
                        .recordId(record.getRecordId())
                        .recordCode(record.getRecordCode())
                        .capturedAt(record.getCreatedAt())
                        .patient(DashboardDTO.PatientDTO.builder()
                                .fullName(record.getPatient() != null ? record.getPatient().getFullName() : "N/A")
                                .build())
                        .bodyPart(record.getBodyPart())
                        .aiPrediction(record.getAiPrediction())
                        .aiConfidence(record.getAiConfidence() != null ? Double.valueOf(record.getAiConfidence()) : 0.0)
                        .status(record.getStatus())
                        .build())
                .toList();

        return DashboardDTO.builder()
                .queueCount(queueItems.size())
                .queueRecords(queueItems)
                .build();
    }

    @Override
    public Long getDoctorIdByEmail(String email) {
        return userRepository.findByEmail(email)
                .map(User::getUserId)
                .orElse(null);
    }

    @Override
    public List<DashboardDTO.QueueItemDTO> getPendingDTOsForDoctor(Long doctorId) {
        List<String> pendingStatuses = List.of("PENDING_DOCTOR");

        List<ImagingRecord> records = imagingRecordRepository
                .findByDoctorUserIdAndStatusInOrderByCreatedAtDesc(doctorId, pendingStatuses);

        return records.stream()
                .map(record -> DashboardDTO.QueueItemDTO.builder()
                        .recordId(record.getRecordId())
                        .recordCode(record.getRecordCode())
                        .capturedAt(record.getCreatedAt())
                        .patient(DashboardDTO.PatientDTO.builder()
                                .fullName(record.getPatient() != null ? record.getPatient().getFullName() : "N/A")
                                .build())
                        .bodyPart(record.getBodyPart())
                        .aiPrediction(record.getAiPrediction())
                        .aiConfidence(record.getAiConfidence() != null ? Double.valueOf(record.getAiConfidence()) : 0.0)
                        .status(record.getStatus())
                        .build())
                .toList();
    }

    @Override
    public ImagingRecord getRecordDetail(Long recordId) {
        return imagingRecordRepository.findById(recordId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy hồ sơ ID: " + recordId));
    }

    @Override
    public Patient getPatientProfile(User user) {
        return patientRepository.findByUser(user).orElse(null);
    }

    private String nextRecordCode() {
        long next = imagingRecordRepository.count() + 1;
        return "XR-" + LocalDate.now().getYear() + "-" + String.format("%04d", next);
    }

    @Override
    public List<Patient> getAllPatients() {
        return patientRepository.findAll();
    }

    @Override
    public Patient getPatientById(Long patientId) {
        return patientRepository.findById(patientId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy bệnh nhân ID: " + patientId));
    }
}