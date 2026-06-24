package com.example.mediscanauth.service.impl;

import com.example.mediscanauth.model.ImagingRecord;
import com.example.mediscanauth.model.User;
import com.example.mediscanauth.repository.ImagingRecordRepository;
import com.example.mediscanauth.repository.PatientRepository;
import com.example.mediscanauth.repository.UserRepository;
import com.example.mediscanauth.service.ImagingRecordService;
import com.example.mediscanauth.service.UserAccountService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Service
public class ImagingRecordServiceImpl implements ImagingRecordService {

    private static final List<String> ACTIVE_QUEUE_STATUSES = List.of("PENDING_AI", "AI_DONE", "AI_ANALYZED", "PENDING_DOCTOR");
    private static final String UPLOAD_DIR = "src/main/resources/static/uploads/";
    private static final String AI_SERVICE_URL = "http://localhost:8000/predict";
    private static final int LEGACY_TEXT_COLUMN_LIMIT = 490;

    private final ImagingRecordRepository imagingRecordRepository;
    private final UserAccountService userAccountService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public ImagingRecordServiceImpl(ImagingRecordRepository imagingRecordRepository,
                                    UserAccountService userAccountService,
                                    PatientRepository patientRepository,
                                    UserRepository userRepository) {
        this.imagingRecordRepository = imagingRecordRepository;
        this.userAccountService = userAccountService;
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
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
    @Transactional
    public ImagingRecord captureAndAnalyzeFromTechnician(String technicianEmail,
                                                         String patientEmail,
                                                         String doctorEmail) {
        User technician = userAccountService.findByEmail(technicianEmail);
        User patient = userAccountService.findByEmail(patientEmail);
        User doctor = isBlank(doctorEmail) ? null : userAccountService.findByEmail(doctorEmail);

        try {
            Path uploadPath = Paths.get(UPLOAD_DIR).toAbsolutePath().normalize();
            Files.createDirectories(uploadPath);

            StoredImage storedImage = selectRandomUploadImage(uploadPath);

            ImagingRecord record = new ImagingRecord();
            record.setRecordCode(nextRecordCode());
            record.setPatient(patient);
            record.setDoctor(doctor);
            record.setTechnician(technician);
            record.setBodyPart("Ảnh X-Ray ngẫu nhiên");
            record.setFileName(storedImage.fileName());
            record.setAiPrediction("Đang phân tích AI bằng YOLO/ANFIS");
            record.setAiConfidence(0);
            record.setRecommendation("Chờ bác sĩ xác nhận kết quả AI.");
            record.setStatus("PENDING_AI");
            ImagingRecord savedRecord = imagingRecordRepository.save(record);

            applyAiAnalysis(savedRecord, uploadPath, storedImage.fileBytes());
            return imagingRecordRepository.save(savedRecord);
        } catch (IOException e) {
            throw new RuntimeException("Không thể lấy ảnh ngẫu nhiên hoặc phân tích ảnh X-Ray: " + e.getMessage(), e);
        }
    }

    @Override
    public ImagingRecord getRecordById(Long recordId) {
        return imagingRecordRepository.findById(recordId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy hồ sơ #" + recordId));
    }

    @Override
    @Transactional
    public ImagingRecord updateRecordCoordinates(Long recordId, Integer bboxX, Integer bboxY, Integer bboxWidth, Integer bboxHeight) {
        ImagingRecord record = imagingRecordRepository.findById(recordId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy hồ sơ #" + recordId));
        record.setBboxX(bboxX);
        record.setBboxY(bboxY);
        record.setBboxWidth(bboxWidth);
        record.setBboxHeight(bboxHeight);
        return imagingRecordRepository.save(record);
    }

    @Override
    public List<ImagingRecord> findRecordsUploadedByTechnician(String technicianEmail) {
        return imagingRecordRepository.findByTechnicianEmailOrderByCreatedAtDesc(technicianEmail);
    }

    @Override
    @Transactional
    public ImagingRecord confirmDoctorReview(Long recordId, String doctorEmail, String conclusion, String recommendation) {
        ImagingRecord record = imagingRecordRepository.findById(recordId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy hồ sơ #" + recordId));
        User doctor = userAccountService.findByEmail(doctorEmail);
        record.setDoctor(doctor);
        record.setDoctorConclusion(cleanSentence(isBlank(conclusion) ? record.getAiPrediction() : conclusion));
        record.setRecommendation(cleanSentence(isBlank(recommendation) ? "Bác sĩ đã xác nhận kết quả. Theo dõi và điều trị theo chỉ định chuyên môn." : recommendation));
        record.setStatus("DOCTOR_CONFIRMED");
        record.setConfirmedAt(LocalDateTime.now());
        return imagingRecordRepository.save(record);
    }

    @Override
    @Transactional
    public ImagingRecord rejectDoctorReview(Long recordId, String doctorEmail, String conclusion, String recommendation) {
        ImagingRecord record = imagingRecordRepository.findById(recordId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy hồ sơ #" + recordId));
        User doctor = userAccountService.findByEmail(doctorEmail);
        record.setDoctor(doctor);
        record.setDoctorConclusion(cleanSentence(isBlank(conclusion) ? "Bác sĩ chưa xác nhận kết quả AI; cần đánh giá lại." : conclusion));
        record.setRecommendation(cleanSentence(isBlank(recommendation) ? "Cần chụp lại, bổ sung tư thế hoặc kiểm tra trực tiếp theo chỉ định bác sĩ." : recommendation));
        record.setStatus("DOCTOR_REJECTED");
        return imagingRecordRepository.save(record);
    }

    @Override
    public Page<ImagingRecord> searchConfirmedLibrary(String keyword, String bodyPart, Pageable pageable) {
        return imagingRecordRepository.searchConfirmedLibrary(trimToEmpty(keyword), trimToEmpty(bodyPart), pageable);
    }

    @Override
    @Transactional
    public void clearNonConfirmedRecords() {
        imagingRecordRepository.deleteByStatusIn(
                List.of("PENDING_AI", "AI_DONE", "AI_ANALYZED", "PENDING_DOCTOR", "AI_FAILED", "DOCTOR_REJECTED")
        );
    }

    @Override
    public Page<ImagingRecord> searchForPatient(User patient, String keyword, String bodyPart, Pageable pageable) {
        return imagingRecordRepository.searchForPatient(patient, keyword, bodyPart, pageable);
    }

    @Override
    @Transactional
    public void deleteRecordForPatient(Long recordId, User patient) {
        ImagingRecord record = imagingRecordRepository.findById(recordId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy hồ sơ."));
        
        if (!record.getPatient().getId().equals(patient.getId())) {
            throw new IllegalArgumentException("Bạn không có quyền xóa hồ sơ này.");
        }

        if (record.getFileName() != null && !record.getFileName().isEmpty()) {
            try {
                Path filePath = Paths.get("src/main/resources/static/uploads/" + record.getFileName());
                Files.deleteIfExists(filePath);
            } catch (IOException e) {
            }
        }

        imagingRecordRepository.delete(record);
    }

    private StoredImage selectRandomUploadImage(Path uploadPath) throws IOException {
        List<Path> imageFiles = Files.list(uploadPath)
                .filter(Files::isRegularFile)
                .filter(path -> {
                    String lowerName = path.getFileName().toString().toLowerCase();
                    return (lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg") || lowerName.endsWith(".png") || lowerName.endsWith(".gif") || lowerName.endsWith(".bmp") || lowerName.endsWith(".dcm"))
                            && !lowerName.startsWith("annotated_");
                })
                .collect(Collectors.toList());

        if (imageFiles.isEmpty()) {
            throw new IOException("Không có ảnh mẫu trong kho ảnh để chụp.");
        }

        Path randomImage = imageFiles.get(ThreadLocalRandom.current().nextInt(imageFiles.size()));
        byte[] fileBytes = Files.readAllBytes(randomImage);
        Files.delete(randomImage);
        return new StoredImage(randomImage.getFileName().toString(), fileBytes);
    }


    private void applyAiAnalysis(ImagingRecord record, Path uploadPath, byte[] fileBytes) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(fileBytes) {
            @Override
            public String getFilename() {
                return record.getFileName();
            }
        });

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(
                    AI_SERVICE_URL,
                    new HttpEntity<>(body, headers),
                    String.class
            );

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                markAiFailed(record, "AI service không trả về kết quả hợp lệ.");
                return;
            }

            JsonNode jsonNode = objectMapper.readTree(response.getBody());
            int confidence = (int) Math.round(jsonNode.path("highest_confidence").asDouble(0.0) * 100);
            JsonNode diagnosisNode = jsonNode.path("diagnosis");
            String impression = diagnosisNode.path("impression").asText("");
            String summary    = diagnosisNode.path("summary").asText("");

            String annotatedBase64 = jsonNode.path("annotated_image_base64").asText("");
            if (!isBlank(annotatedBase64) && !"null".equalsIgnoreCase(annotatedBase64)) {
                byte[] decodedBytes = Base64.getDecoder().decode(annotatedBase64);
                String annotatedFileName = "annotated_" + record.getFileName();
                try (FileOutputStream fos = new FileOutputStream(uploadPath.resolve(annotatedFileName).toFile())) {
                    fos.write(decodedBytes);
                }
                record.setFileName(annotatedFileName);
            }

            // Dùng impression trực tiếp — đã là ngôn ngữ bác sĩ từ ANFIS service
            String clinicalText = !isBlank(impression) ? impression : summary;
            record.setAiPrediction(limitText(clinicalText, LEGACY_TEXT_COLUMN_LIMIT));
            record.setAiConfidence(confidence);

            // Lấy recommendations từ ANFIS (top 3), fallback nếu rỗng
            String recommendation = buildRecommendationText(diagnosisNode.path("recommendations"));
            record.setRecommendation(limitText(recommendation, LEGACY_TEXT_COLUMN_LIMIT));
            record.setStatus("PENDING_DOCTOR");
        } catch (Exception ex) {
            markAiFailed(record, "Lỗi kết nối/phân tích AI: " + ex.getMessage());
        }
    }

    private void markAiFailed(ImagingRecord record, String message) {
        record.setAiPrediction(limitText(message, LEGACY_TEXT_COLUMN_LIMIT));
        record.setAiConfidence(0);
        record.setRecommendation("Vui lòng kiểm tra AI service hoặc gửi bác sĩ đọc phim thủ công.");
        record.setStatus("AI_FAILED");
    }

    private String buildRecommendationText(JsonNode recommendationsNode) {
        if (recommendationsNode == null || !recommendationsNode.isArray() || recommendationsNode.isEmpty()) {
            return "Chờ bác sĩ xác nhận kết quả và đưa ra hướng điều trị phù hợp.";
        }
        StringBuilder sb = new StringBuilder();
        int limit = Math.min(recommendationsNode.size(), 3);
        for (int i = 0; i < limit; i++) {
            String item = recommendationsNode.get(i).asText("").trim();
            if (!isBlank(item)) {
                if (!sb.isEmpty()) sb.append(" ");
                sb.append(cleanSentence(item));
            }
        }
        return sb.isEmpty() ? "Chờ bác sĩ xác nhận kết quả và đưa ra hướng điều trị phù hợp." : sb.toString();
    }

    private String cleanSentence(String value) {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty()) {
            return "";
        }
        return text.endsWith(".") ? text : text + ".";
    }

    private String limitText(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, Math.max(0, maxLength - 3)).trim() + "...";
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private String nextRecordCode() {
        long next = imagingRecordRepository.count() + 1;
        return "XR-" + LocalDate.now().getYear() + "-" + String.format("%04d", next);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private record StoredImage(String fileName, byte[] fileBytes) {
    }
}
