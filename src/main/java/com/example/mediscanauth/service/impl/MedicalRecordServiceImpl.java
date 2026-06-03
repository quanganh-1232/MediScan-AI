package com.example.mediscanauth.service.impl;

import com.example.mediscanauth.model.MedicalRecord;
import com.example.mediscanauth.model.Patient;
import com.example.mediscanauth.model.User;
import com.example.mediscanauth.model.XrayImage;
import com.example.mediscanauth.repository.MedicalRecordRepository;
import com.example.mediscanauth.repository.PatientRepository;
import com.example.mediscanauth.repository.XrayImageRepository;
import com.example.mediscanauth.service.MedicalRecordService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class MedicalRecordServiceImpl implements MedicalRecordService {

    private final MedicalRecordRepository medicalRecordRepository;
    private final XrayImageRepository xrayImageRepository;
    private final PatientRepository patientRepository;

    private final String CLOUD_STORAGE_DIR = "uploads/xray/";

    public MedicalRecordServiceImpl(MedicalRecordRepository medicalRecordRepository,
                                    XrayImageRepository xrayImageRepository,
                                    PatientRepository patientRepository) {
        this.medicalRecordRepository = medicalRecordRepository;
        this.xrayImageRepository = xrayImageRepository;
        this.patientRepository = patientRepository;
    }

    @Override
    @Transactional
    public MedicalRecord createPatientSelfCheck(User currentUser, String bodyPart, String symptoms, MultipartFile file) throws IOException {
        Patient patient = patientRepository.findByUser(currentUser).orElse(null);
        if (patient == null) {
            throw new RuntimeException("Không tìm thấy hồ sơ bệnh nhân hợp lệ.");
        }

        String imageUrl = saveToCloudStorage(file);

        MedicalRecord record = new MedicalRecord();
        record.setPatient(patient);
        record.setRecordCode("AI-" + LocalDateTime.now().getYear() + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase());
        record.setBodyPart(bodyPart);
        record.setSymptomDescription(symptoms);
        record.setStatus("UPLOADED");
        MedicalRecord savedRecord = medicalRecordRepository.save(record);

        XrayImage xrayImage = new XrayImage();
        xrayImage.setRecord(savedRecord);
        xrayImage.setUploadedBy(currentUser);
        xrayImage.setOriginalImagePath(imageUrl);
        xrayImage.setBodyPart(bodyPart);
        xrayImage.setUploadSource("PATIENT");
        xrayImage.setStatus("UPLOADED");
        xrayImageRepository.save(xrayImage);

        return savedRecord;
    }

    private String saveToCloudStorage(MultipartFile file) throws IOException {
        Path uploadPath = Paths.get(CLOUD_STORAGE_DIR);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }
        String fileName = UUID.randomUUID().toString() + "_" + file.getOriginalFilename();
        Path filePath = uploadPath.resolve(fileName);
        Files.copy(file.getInputStream(), filePath);

        return "/" + CLOUD_STORAGE_DIR + fileName;
    }

    @Override
    public java.util.List<MedicalRecord> findPatientRecords(User currentUser) {
        Patient patient = patientRepository.findByUser(currentUser).orElse(null);
        if (patient != null) {
            // Cập nhật lại tên hàm ở đây
            return medicalRecordRepository.findByPatientOrderByCreatedAtDesc(patient);
        }
        return java.util.Collections.emptyList();
    }

    @Override
    public MedicalRecord getRecordDetail(Long id, User currentUser) {
        Patient patient = patientRepository.findByUser(currentUser)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy bệnh nhân"));

        // ĐÃ SỬA: Gọi đúng tên hàm mới là findByRecordIdAndPatient
        return medicalRecordRepository.findByRecordIdAndPatient(id, patient)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy hồ sơ hoặc bạn không có quyền truy cập"));
    }

    @Override
    @Transactional
    public void deleteRecord(Long id, User currentUser) {
        MedicalRecord record = getRecordDetail(id, currentUser);
        // Chỉ cho phép xóa nếu AI chưa phân tích
        if (!"UPLOADED".equals(record.getStatus())) {
            throw new RuntimeException("Chỉ có thể xóa hồ sơ đang ở trạng thái chờ.");
        }

        // Xóa ảnh X-ray liên kết trước
        xrayImageRepository.deleteByRecord(record);
        // Sau đó xóa Medical Record
        medicalRecordRepository.delete(record);
    }

    @Override
    @Transactional
    public void simulateAiProcessing(Long id, User currentUser) {
        MedicalRecord record = getRecordDetail(id, currentUser);
        // ĐÃ SỬA: Đổi từ AI_DONE thành AI_ANALYZED cho đúng với Database
        record.setStatus("AI_ANALYZED");
        record.setAiPrediction("Nghi ngờ gãy xương / nứt xương tại vị trí " + record.getBodyPart());
        record.setAiConfidence((double) (Math.random() * (98 - 80) + 80));
        medicalRecordRepository.save(record);
    }
}