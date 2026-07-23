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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import com.example.mediscanauth.repository.NotificationRepository;
import com.example.mediscanauth.model.Notification;
import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import java.util.Map;
import com.example.mediscanauth.service.CloudinaryService;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Service
public class ImagingRecordServiceImpl implements ImagingRecordService {

    private static final List<String> ACTIVE_QUEUE_STATUSES = List.of("PENDING_AI", "AI_DONE", "AI_ANALYZED",
            "PENDING_DOCTOR");
    private static final String UPLOAD_DIR = "src/main/resources/static/uploads/";
    private static final int LEGACY_TEXT_COLUMN_LIMIT = 490;
    // ai-service does real CPU work (YOLO + classical CV + ANFIS); a low read
    // timeout would false-positive on legitimate slow analyses, but it must
    // still be bounded so a hung ai-service can't hold this transaction's DB
    // connection open forever.
    private static final int AI_CONNECT_TIMEOUT_MS = 5_000;
    private static final int AI_READ_TIMEOUT_MS = 45_000;

    @Value("${ai.service.url}")
    private String aiServiceUrl;

    @Value("${ai.service.api-key}")
    private String aiServiceApiKey;

    private final ImagingRecordRepository imagingRecordRepository;
    private final PatientRepository patientRepository;
    private final UserRepository userRepository;
    private final UserAccountService userAccountService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final NotificationRepository notificationRepository;
    private final Cloudinary cloudinary;
    private final CloudinaryService cloudinaryService;

    public ImagingRecordServiceImpl(
            ImagingRecordRepository imagingRecordRepository,
            UserAccountService userAccountService,
            PatientRepository patientRepository,
            UserRepository userRepository,
            NotificationRepository notificationRepository,
            Cloudinary cloudinary,
            CloudinaryService cloudinaryService) {

        this.imagingRecordRepository = imagingRecordRepository;
        this.userAccountService = userAccountService;
        this.patientRepository = patientRepository;
        this.userRepository = userRepository;
        this.notificationRepository = notificationRepository;
        this.cloudinary = cloudinary;
        this.cloudinaryService = cloudinaryService;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(AI_CONNECT_TIMEOUT_MS);
        requestFactory.setReadTimeout(AI_READ_TIMEOUT_MS);
        this.restTemplate = new RestTemplate(requestFactory);
        this.objectMapper = new ObjectMapper();
    }

    // ==================== DOCTOR DASHBOARD METHODS (từ code trên)
    // ====================
    @Override
    public DashboardDTO getDoctorDashboardStats(Long doctorId) {
        List<ImagingRecord> records = imagingRecordRepository
                .findByStatusInAndDoctorUserIdOrderByCreatedAtDesc(ACTIVE_QUEUE_STATUSES, doctorId);

        List<DashboardDTO.QueueItemDTO> queueItems = records.stream()
                .map(this::toQueueItemDTO)
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
        return records.stream().map(this::toQueueItemDTO).toList();
    }

    @Override
    public List<DashboardDTO.QueueItemDTO> getCompletedDTOsForDoctor(Long doctorId) {
        List<String> completedStatuses = List.of("COMPLETED");
        List<ImagingRecord> records = imagingRecordRepository
                .findByDoctorUserIdAndStatusInOrderByCreatedAtDesc(doctorId, completedStatuses);
        return records.stream().map(this::toQueueItemDTO).toList();
    }

    @Override
    public ImagingRecord getRecordDetail(Long recordId) {
        return imagingRecordRepository.findById(recordId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy hồ sơ ID: " + recordId));
    }

    @Override
    public List<com.example.mediscanauth.model.dto.AiRegionProjection> getAiRegionsByRecordId(Long recordId) {
        return imagingRecordRepository.findAiRegionsByRecordId(recordId);
    }

    // ==================== CORE METHODS (giữ logic từ code dưới)
    // ====================
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
    public ImagingRecord createFromTechnician(String technicianEmail, String patientEmail, String bodyPart,
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
                                                         String doctorEmail,
                                                         MultipartFile image) {
        User technician = userAccountService.findByEmail(technicianEmail);
        User patient = userAccountService.findByEmail(patientEmail);
        User doctor = isBlank(doctorEmail) ? null : userAccountService.findByEmail(doctorEmail);

        try {
            Path uploadPath = Paths.get(UPLOAD_DIR).toAbsolutePath().normalize();
            Files.createDirectories(uploadPath);

            String fileName = image.getOriginalFilename();
            Path destination = uploadPath.resolve(fileName);

            Files.copy(
                    image.getInputStream(),
                    destination,
                    StandardCopyOption.REPLACE_EXISTING);

            byte[] imageBytes = image.getBytes();

            ImagingRecord record = new ImagingRecord();
            record.setRecordCode(nextRecordCode());
            record.setPatient(patient);
            record.setDoctor(doctor);
            record.setTechnician(technician);
            record.setBodyPart("Ảnh X-Ray ngẫu nhiên");
            record.setFileName(fileName);
            record.setAiPrediction("Đang phân tích AI bằng YOLO+ANFIS");
            record.setAiConfidence(0);
            record.setRecommendation("Chờ bác sĩ xác nhận kết quả AI.");
            record.setStatus("PENDING_AI");

            ImagingRecord savedRecord = imagingRecordRepository.save(record);
            applyAiAnalysis(savedRecord, uploadPath, imageBytes);
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
    public Patient getPatientProfile(User user) {
        return patientRepository.findByUser(user).orElse(null);
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

    @Override
    @Transactional
    public ImagingRecord updateRecordCoordinates(Long recordId, Integer bboxX, Integer bboxY, Integer bboxWidth,
            Integer bboxHeight) {
        ImagingRecord record = getRecordById(recordId);
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
    public ImagingRecord confirmDoctorReview(Long recordId, String doctorEmail, String conclusion,
            String recommendation, String base64ImageData) { // <--- Nhận thêm tham số base64ImageData
        
        ImagingRecord record = getRecordById(recordId);
        User doctor = userAccountService.findByEmail(doctorEmail);
        record.setDoctor(doctor);
        record.setDoctorConclusion(cleanSentence(isBlank(conclusion) ? record.getAiPrediction() : conclusion));
        record.setRecommendation(cleanSentence(
                isBlank(recommendation) ? "Bác sĩ đã xác nhận kết quả." : recommendation));
        record.setStatus("COMPLETED");
        record.setConfirmedAt(LocalDateTime.now());

        String dbFileName = record.getFileName();

        // Kiểm tra nếu có dữ liệu ảnh chụp màn hình gửi lên từ Client
        if (dbFileName != null && !dbFileName.isEmpty() && base64ImageData != null && !base64ImageData.isEmpty()) {

            String patientName = record.getPatient() != null ? record.getPatient().getFullName() : "Unknown_Patient";
            String recordCode = record.getRecordCode() != null ? record.getRecordCode() : "Unknown_Code";

            // Gọi dịch vụ upload trực tiếp chuỗi Base64 (ảnh đã chụp màn hình hiển thị bao gồm cả khung AI)
            String doctorImageUrl = cloudinaryService.generateAndUploadDoctorImage(
                    base64ImageData,
                    patientName,
                    recordCode,
                    dbFileName); // <-- Truyền tên file gốc từ Database

            if (doctorImageUrl != null && !doctorImageUrl.isEmpty()) {
                System.out.println("-> [INFO] Ảnh chụp màn hình hiển thị đã được đẩy lên Cloudinary: " + doctorImageUrl);
                System.out.println("-> [DB KEEP] Giữ nguyên tên file gốc trong Database: " + dbFileName);
            }
        }

        ImagingRecord savedRecord = imagingRecordRepository.save(record);

        // Tạo thông báo...
        Notification notification = new Notification();
        notification.setUser(savedRecord.getPatient());
        notification.setRecordId(savedRecord.getRecordId());
        notification.setTitle("Kết quả X-quang đã có");
        notification.setMessage("Kết quả chẩn đoán cho hồ sơ " + savedRecord.getRecordCode() + " đã được bác sĩ xác nhận.");
        notification.setRead(false);
        notificationRepository.save(notification);

        return savedRecord;
    }
    
    @Override
    @Transactional
    public ImagingRecord rejectDoctorReview(Long recordId, String doctorEmail, String conclusion,
            String recommendation) {
        ImagingRecord record = getRecordById(recordId);
        User doctor = userAccountService.findByEmail(doctorEmail);
        record.setDoctor(doctor);
        record.setDoctorConclusion(
                cleanSentence(isBlank(conclusion) ? "Bác sĩ chưa xác nhận kết quả AI; cần đánh giá lại." : conclusion));
        record.setRecommendation(cleanSentence(
                isBlank(recommendation) ? "Cần chụp lại, bổ sung tư thế hoặc kiểm tra trực tiếp theo chỉ định bác sĩ."
                        : recommendation));
        record.setStatus("DOCTOR_REJECTED");
        return imagingRecordRepository.save(record);
    }

    @Override
    public Page<ImagingRecord> searchConfirmedLibrary(String keyword, String bodyPart, Pageable pageable) {
        return imagingRecordRepository.searchConfirmedLibrary(trimToEmpty(keyword), trimToEmpty(bodyPart), pageable);
    }

    @Override
    public Page<ImagingRecord> searchForPatient(User patient, String keyword, String bodyPart, Pageable pageable) {
        return imagingRecordRepository.searchForPatient(patient, keyword, bodyPart, pageable);
    }

    @Override
    @Transactional
    public void deleteRecordForPatient(Long recordId, User patient) {
        ImagingRecord record = getRecordById(recordId);
        if (!record.getPatient().getUserId().equals(patient.getUserId())) {
            throw new IllegalArgumentException("Bạn không có quyền xóa hồ sơ này.");
        }
        if (record.getFileName() != null && !record.getFileName().isEmpty()) {
            try {
                Path filePath = Paths.get("src/main/resources/static/uploads/" + record.getFileName());
                Files.deleteIfExists(filePath);
            } catch (IOException ignored) {
            }
        }
        imagingRecordRepository.delete(record);
    }

    @Override
    @Transactional
    public void clearNonConfirmedRecords() {
        imagingRecordRepository.deleteByStatusIn(
                List.of("PENDING_AI", "AI_DONE", "AI_ANALYZED", "PENDING_DOCTOR", "AI_FAILED", "DOCTOR_REJECTED"));
    }

    // ==================== HELPER METHODS ====================
    private DashboardDTO.QueueItemDTO toQueueItemDTO(ImagingRecord record) {
        return DashboardDTO.QueueItemDTO.builder()
                .recordId(record.getRecordId())
                .recordCode(record.getRecordCode())
                .capturedAt(record.getCreatedAt())
                .patient(DashboardDTO.PatientDTO.builder()
                        .fullName(record.getPatient() != null ? record.getPatient().getFullName() : "N/A")
                        .build())
                .bodyPart(record.getBodyPart())
                .aiPrediction(record.getAiPrediction())
                .aiConfidence(record.getAiConfidence() != null ? record.getAiConfidence() : 0.0)
                .status(record.getStatus())
                .fileName(record.getFileName())
                .doctorConclusion(record.getDoctorConclusion())
                .build();
    }

    private String nextRecordCode() {
        long next = imagingRecordRepository.count() + 1;
        return "XR-" + LocalDate.now().getYear() + "-" + String.format("%04d", next);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String cleanSentence(String value) {
        String text = value == null ? "" : value.trim();
        return text.isEmpty() ? "" : (text.endsWith(".") ? text : text + ".");
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private String limitText(String value, int maxLength) {
        if (value == null || value.length() <= maxLength)
            return value;
        return value.substring(0, Math.max(0, maxLength - 3)).trim() + "...";
    }

    // ==================== AI & UPLOAD HELPERS (giữ nguyên từ code dưới)
    // ====================
    private StoredImage selectRandomUploadImage(Path uploadPath) throws IOException {
        List<Path> imageFiles = Files.list(uploadPath)
                .filter(Files::isRegularFile)
                .filter(path -> {
                    String lowerName = path.getFileName().toString().toLowerCase();
                    return (lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg") ||
                            lowerName.endsWith(".png") || lowerName.endsWith(".gif") ||
                            lowerName.endsWith(".bmp") || lowerName.endsWith(".dcm"))
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
        headers.set("X-Internal-Api-Key", aiServiceApiKey);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(fileBytes) {
            @Override
            public String getFilename() {
                return record.getFileName();
            }
        });

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(
                    aiServiceUrl, new HttpEntity<>(body, headers), String.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                markAiFailed(record, "AI service không trả về kết quả hợp lệ.");
                return;
            }

            JsonNode jsonNode = objectMapper.readTree(response.getBody());
            int confidence = (int) Math.round(jsonNode.path("highest_confidence").asDouble(0.0) * 100);
            JsonNode diagnosisNode = jsonNode.path("diagnosis");
            String impression = diagnosisNode.path("impression").asText("");
            String summary = diagnosisNode.path("summary").asText("");

            String annotatedBase64 = jsonNode.path("annotated_image_base64").asText("");
            if (!isBlank(annotatedBase64) && !"null".equalsIgnoreCase(annotatedBase64)) {
                byte[] decodedBytes = Base64.getDecoder().decode(annotatedBase64);
                String annotatedFileName = "annotated_" + record.getFileName();
                try (FileOutputStream fos = new FileOutputStream(uploadPath.resolve(annotatedFileName).toFile())) {
                    fos.write(decodedBytes);
                }
                record.setFileName(annotatedFileName);
            }

            String clinicalText = !isBlank(impression) ? impression : summary;
            record.setAiPrediction(limitText(clinicalText, LEGACY_TEXT_COLUMN_LIMIT));
            record.setAiConfidence(confidence);

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
                if (!sb.isEmpty())
                    sb.append(" ");
                sb.append(cleanSentence(item));
            }
        }
        return sb.isEmpty() ? "Chờ bác sĩ xác nhận kết quả và đưa ra hướng điều trị phù hợp." : sb.toString();
    }

    private record StoredImage(String fileName, byte[] fileBytes) {
    }
}