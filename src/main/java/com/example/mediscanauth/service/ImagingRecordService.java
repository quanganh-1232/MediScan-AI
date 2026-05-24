package com.example.mediscanauth.service;

import com.example.mediscanauth.model.ImagingRecord;
import com.example.mediscanauth.model.MedicalRecord;
import com.example.mediscanauth.model.Patient;
import com.example.mediscanauth.model.User;
import com.example.mediscanauth.model.dto.DashboardDTO;
import com.example.mediscanauth.repository.ImagingRecordRepository;
import com.example.mediscanauth.repository.MedicalRecordRepository;
import com.example.mediscanauth.repository.PatientRepository;
import com.example.mediscanauth.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class ImagingRecordService {

    private static final List<String> ACTIVE_QUEUE_STATUSES = List.of("AI_ANALYZED", "DOCTOR_REVIEWING", "PENDING_DOCTOR");
    //private static final List<String> ACTIVE_QUEUE_STATUSES = List.of("PENDING_AI", "AI_DONE", "PENDING_DOCTOR");

    @Autowired
    private UserRepository userRepository;
    private final PatientRepository patientRepository;
    private final ImagingRecordRepository imagingRecordRepository;
    private final UserAccountService userAccountService;

    public ImagingRecordService(ImagingRecordRepository imagingRecordRepository,
                                UserAccountService userAccountService,
                                PatientRepository patientRepository) { // Thêm vào đây
        this.imagingRecordRepository = imagingRecordRepository;
        this.userAccountService = userAccountService;
        this.patientRepository = patientRepository; // Gán giá trị vào đây
    }

    public List<ImagingRecord> findForPatient(User patient) {
        return imagingRecordRepository.findByPatientOrderByCapturedAtDescCreatedAtDesc(patient);
    }

    public ImagingRecord findLatestForPatient(User patient) {
        return imagingRecordRepository.findFirstByPatientOrderByCapturedAtDescCreatedAtDesc(patient).orElse(null);
    }

    public long countForPatient(User patient) {
        return imagingRecordRepository.countByPatient(patient);
    }

    public long countQueue() {
        return imagingRecordRepository.countByStatusIn(ACTIVE_QUEUE_STATUSES);
    }

    public long countToday() {
        return imagingRecordRepository.countByCapturedAt(LocalDate.now());
    }

    public long countAll() {
        return imagingRecordRepository.count();
    }

    public List<ImagingRecord> findQueue() {
        return imagingRecordRepository.findByStatusInOrderByCreatedAtDesc(ACTIVE_QUEUE_STATUSES);
    }

    public List<ImagingRecord> findRecent() {
        return imagingRecordRepository.findTop10ByOrderByCreatedAtDesc();
    }

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

    public DashboardDTO getDoctorDashboardStats(Long doctorId) {
        List<ImagingRecord> records = imagingRecordRepository
                .findByStatusInAndDoctorUserIdOrderByCreatedAtDesc(ACTIVE_QUEUE_STATUSES, doctorId);
        long count = imagingRecordRepository.countByStatusInAndDoctorUserId(ACTIVE_QUEUE_STATUSES, doctorId);
        List<DashboardDTO.QueueItemDTO> queueItems = records.stream()
                .map(record -> DashboardDTO.QueueItemDTO.builder()
                        .recordId(record.getRecordId())
                        .recordCode(record.getRecordCode())
                        .capturedAt(record.getCreatedAt())
                        .patient(DashboardDTO.PatientDTO.builder()
                                .fullName(record.getPatient() != null ? record.getPatient().getFullName() : "N/A")
                                .build())                        .bodyPart(record.getBodyPart())
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
    public Long getDoctorIdByEmail(String email) {
        return userRepository.findByEmail(email)
                .map(User::getUserId)
                .orElse(null);
    }

    public List<DashboardDTO.QueueItemDTO> getPendingDTOsForDoctor(Long doctorId) {
        List<String> pendingStatuses = List.of("AI_ANALYZED", "DOCTOR_REVIEWING");

        List<ImagingRecord> records = imagingRecordRepository
                .findByDoctorUserIdAndStatusInOrderByCreatedAtDesc(doctorId, pendingStatuses);

        return records.stream()
                .map(record -> DashboardDTO.QueueItemDTO.builder()
                        .recordId(record.getRecordId())
                        .recordCode(record.getRecordCode())
                        .capturedAt(record.getCreatedAt())
                        .patient(DashboardDTO.PatientDTO.builder()
                                .fullName(record.getPatient() != null ? record.getPatient().getFullName() : "N/A")
                                // Không gán gender ở đây vì DTO bác hiện tại không có trường này
                                .build())
                        .bodyPart(record.getBodyPart())
                        .aiPrediction(record.getAiPrediction())
                        .aiConfidence(record.getAiConfidence() != null ? Double.valueOf(record.getAiConfidence()) : 0.0)
                        .status(record.getStatus())
                        .build())
                .toList();
    }

    public ImagingRecord getRecordDetail(Long recordId) {
        return imagingRecordRepository.findById(recordId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy hồ sơ ID: " + recordId));
    }

    public Patient getPatientProfile(User user) {
        return patientRepository.findByUser(user).orElse(null);
    }
}
