package com.example.mediscanauth.service.impl;

import com.example.mediscanauth.constant.OperationalConfig;
import com.example.mediscanauth.model.ImagingRecord;
import com.example.mediscanauth.model.User;
import com.example.mediscanauth.repository.ImagingRecordRepository;
import com.example.mediscanauth.model.Appointment;
import com.example.mediscanauth.model.Patient;
import com.example.mediscanauth.repository.AppointmentRepository;
import com.example.mediscanauth.repository.PatientRepository;
import com.example.mediscanauth.repository.UserRepository;
import com.example.mediscanauth.service.PatientWorkflowService;
import com.example.mediscanauth.service.UserAccountService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.Base64;
import java.util.UUID;

@Service
public class PatientWorkflowServiceImpl implements PatientWorkflowService {

    private final ImagingRecordRepository imagingRecordRepository;
    private final UserAccountService userAccountService;
    private final AppointmentRepository appointmentRepository;
    private final PatientRepository patientRepository;
    private final UserRepository userRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    private static final String UPLOAD_DIR = "src/main/resources/static/uploads/";
    private static final int LEGACY_TEXT_COLUMN_LIMIT = OperationalConfig.LEGACY_TEXT_COLUMN_LIMIT;
    private static final int AI_CONNECT_TIMEOUT_MS = OperationalConfig.AI_CONNECT_TIMEOUT_MS;
    private static final int AI_READ_TIMEOUT_MS = OperationalConfig.AI_READ_TIMEOUT_MS;

    @Value("${ai.service.url:http://localhost:8000/predict}")
    private String aiServiceUrl;

    @Value("${ai.service.api-key:dev-ai-key-change-me}")
    private String aiServiceApiKey;

    public PatientWorkflowServiceImpl(ImagingRecordRepository imagingRecordRepository,
                                      UserAccountService userAccountService,
                                      AppointmentRepository appointmentRepository,
                                      PatientRepository patientRepository,
                                      UserRepository userRepository) {
        this.imagingRecordRepository = imagingRecordRepository;
        this.userAccountService = userAccountService;
        this.appointmentRepository = appointmentRepository;
        this.patientRepository = patientRepository;
        this.userRepository = userRepository;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(AI_CONNECT_TIMEOUT_MS);
        requestFactory.setReadTimeout(AI_READ_TIMEOUT_MS);
        this.restTemplate = new RestTemplate(requestFactory);
        this.objectMapper = new ObjectMapper();
    }

    @Override
    @Transactional
    public ImagingRecord uploadImageAndAnalyze(String patientEmail, String bodyPart, MultipartFile file) {
        User patientUser = userAccountService.findByEmail(patientEmail);

        try {
            // 1. Save original image locally
            String originalFileName = UUID.randomUUID().toString() + "_" + file.getOriginalFilename();
            Path uploadPath = Paths.get(UPLOAD_DIR);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }
            Path originalFilePath = uploadPath.resolve(originalFileName).toAbsolutePath();
            byte[] fileBytes = file.getBytes();
            java.nio.file.Files.write(originalFilePath, fileBytes);

            // 2. Send to AI Service
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            headers.set("X-Internal-Api-Key", aiServiceApiKey);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", new ByteArrayResource(fileBytes) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename();
                }
            });
            
            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
            // 3. Process AI Result and create ImagingRecord
            ImagingRecord record = new ImagingRecord();
            record.setRecordCode(nextCode("IMG"));
            record.setPatient(patientUser);
            record.setBodyPart(bodyPart);

            ResponseEntity<String> response = null;
            try {
                response = restTemplate.postForEntity(aiServiceUrl, requestEntity, String.class);
            } catch (Exception e) {
                record.setFileName(originalFileName);
                record.setAiPrediction(limitText("Lỗi kết nối AI: " + e.getMessage(), LEGACY_TEXT_COLUMN_LIMIT));
                record.setAiConfidence(0);
                record.setStatus("AI_FAILED");
                return imagingRecordRepository.save(record);
            }
            
            if (response != null && response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode jsonNode = objectMapper.readTree(response.getBody());
                boolean fractureDetected = jsonNode.get("fracture_detected").asBoolean();
                int confidence = (int) (jsonNode.get("highest_confidence").asDouble() * 100);
                JsonNode diagnosisNode = jsonNode.path("diagnosis");
                String diagnosisImpression = diagnosisNode.path("impression").asText("");
                String diagnosisSummary = diagnosisNode.path("summary").asText("");
                String riskLevel = diagnosisNode.path("risk_level").asText("");
                double fractureScore = diagnosisNode.path("fracture_score").asDouble(0.0);
                
                String annotatedBase64 = jsonNode.get("annotated_image_base64").asText();
                String finalFileName = originalFileName;
                
                if (annotatedBase64 != null && !annotatedBase64.isEmpty() && !annotatedBase64.equals("null")) {
                    byte[] decodedBytes = Base64.getDecoder().decode(annotatedBase64);
                    String annotatedFileName = "annotated_" + originalFileName;
                    Path annotatedFilePath = uploadPath.resolve(annotatedFileName);
                    try (FileOutputStream fos = new FileOutputStream(annotatedFilePath.toFile())) {
                        fos.write(decodedBytes);
                    }

                }
                
                String clinicalText = !diagnosisImpression.isBlank() ? diagnosisImpression : diagnosisSummary;
                String aiPredictionText = buildAiPrediction(fractureDetected, confidence, bodyPart, clinicalText, riskLevel, fractureScore);
                String aiRecommendationText = buildAiRecommendation(fractureDetected, confidence, riskLevel);

                record.setFileName(originalFileName);
                record.setAiPrediction(limitText(aiPredictionText, LEGACY_TEXT_COLUMN_LIMIT));
                record.setAiConfidence(confidence);
                record.setRiskLevel(riskLevel);
                record.setRecommendation(limitText(aiRecommendationText, LEGACY_TEXT_COLUMN_LIMIT));
                record.setStatus("AI_ANALYZED");
            } else {
                record.setFileName(originalFileName);
                record.setAiPrediction("Lỗi phân tích AI");
                record.setAiConfidence(0);
                record.setRecommendation(limitText("Vui lòng thử lại hoặc liên hệ quản trị viên.", LEGACY_TEXT_COLUMN_LIMIT));
                record.setStatus("AI_FAILED");
            }

            return imagingRecordRepository.save(record);

        } catch (IOException e) {
            throw new RuntimeException("Lỗi xử lý file upload", e);
        }
    }

    private String buildAiPrediction(boolean fractureDetected, int confidence, String bodyPart, String clinicalText, String riskLevel, double fractureScore) {
        StringBuilder builder = new StringBuilder();
        if (!clinicalText.isBlank()) {
            String formattedText = cleanSentence(clinicalText)
                    .replaceAll("(?i)(RẤT CAO|CAO|TRUNG BÌNH|THẤP|RẤT THẤP)", "<b>$1</b>");
            builder.append("Nhận định chuyên môn:\n").append(formattedText);
        } else {
            if (fractureDetected) {
                builder.append("Hình ảnh cho thấy dấu hiệu bất thường, cần bác sĩ xem xét thêm.");
            } else {
                builder.append("Chưa phát hiện dấu hiệu bất thường trên hệ thống AI.");
            }
        }

        builder.append("\n\n(Điểm đánh giá hệ thống: ").append(String.format("%.1f", fractureScore)).append("/100)");
        return builder.toString();
    }

    private String buildAiRecommendation(boolean fractureDetected, int confidence, String riskLevel) {
        StringBuilder builder = new StringBuilder();
        builder.append("Khuyến nghị:");

        if (fractureDetected) {
            if (confidence >= OperationalConfig.AI_CONFIDENCE_STRONG_THRESHOLD) {
                builder.append("\n- Nên liên hệ bác sĩ chuyên khoa xương khớp ngay để được chẩn đoán hình ảnh chính xác và lên kế hoạch điều trị.");
            } else {
                builder.append("\n- Kết quả cho thấy khả năng gãy xương, cần thăm khám lâm sàng và xét nghiệm bổ sung để xác nhận.");
            }
        } else {
            builder.append("\n- Dù chưa thấy dấu hiệu gãy rõ rệt, vẫn khuyến nghị theo dõi triệu chứng và tái khám nếu có đau tăng hoặc sưng tấy.");
        }

        if (!riskLevel.isBlank() && !"LOW".equalsIgnoreCase(riskLevel)) {
            builder.append("\n- Với mức nguy cơ <b>").append(toVietnameseRiskLevel(riskLevel)).append("</b>, nên cân nhắc kiểm tra thêm theo chỉ dẫn bác sĩ.");
        }

        builder.append("\n\nLưu ý: AI chỉ hỗ trợ phân tích ban đầu; quyết định điều trị cuối cùng cần do bác sĩ chuyên môn đưa ra.");
        return builder.toString();
    }

    private String toVietnameseRiskLevel(String riskLevel) {
        return switch (riskLevel.toLowerCase()) {
            case "very_low" -> "rất thấp";
            case "low" -> "thấp";
            case "moderate" -> "trung bình";
            case "high" -> "cao";
            case "very_high" -> "rất cao";
            default -> riskLevel;
        };
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

    private String nextCode(String prefix) {
        String shortId = java.util.UUID.randomUUID().toString().substring(0, 5).toUpperCase();
        return prefix + "-" + LocalDate.now().getYear() + "-" + shortId;
    }

    @Override
    @Transactional
    public Appointment bookAppointment(String patientEmail, Long doctorId, String date, String time) {
        User user = userAccountService.findByEmail(patientEmail);
        Patient patient = patientRepository.findByUser(user)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy hồ sơ bệnh nhân"));

        User doctor = null;
        if (doctorId != null) {
            doctor = userRepository.findById(doctorId).orElse(null);
        }

        java.time.LocalDateTime scheduledTime = java.time.LocalDateTime.parse(date + "T" + time);

        // Backend: không cho đặt lịch trong quá khứ
        if (scheduledTime.isBefore(java.time.LocalDateTime.now())) {
            throw new RuntimeException("Không thể đặt lịch vào thời điểm trong quá khứ. Vui lòng chọn từ hôm nay trở đi.");
        }

        // Backend: chỉ cho đặt trong giờ hành chính (07:00 - 17:00)
        int hour = scheduledTime.getHour();
        if (hour < 7 || hour >= 17) {
            throw new RuntimeException("Chỉ có thể đặt lịch trong giờ hành chính (07:00 - 17:00).");
        }

        // Kiểm tra trùng lịch bác sĩ (±30 phút)
        if (doctor != null) {
            java.time.LocalDateTime from = scheduledTime.minusMinutes(29);
            java.time.LocalDateTime to   = scheduledTime.plusMinutes(30);
            long conflicts = appointmentRepository.countDoctorConflicts(doctor, from, to);
            if (conflicts > 0) {
                throw new RuntimeException(
                    "Bác sĩ " + doctor.getFullName() + " đã có lịch hẹn vào khung giờ này. " +
                    "Vui lòng chọn giờ khác (cách ít nhất 30 phút)."
                );
            }
        }

        Appointment appointment = new Appointment();
        appointment.setAppointmentCode(nextCode("APT"));
        appointment.setPatient(patient);
        appointment.setDoctor(doctor);
        appointment.setScheduledTime(scheduledTime);
        appointment.setStatus("SCHEDULED");
        appointment.setAppointmentType("DOCTOR_CONSULTATION");

        return appointmentRepository.save(appointment);
    }

    @Override
    @Transactional
    public void cancelAppointment(String patientEmail, Long appointmentId) {
        User user = userAccountService.findByEmail(patientEmail);
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy lịch hẹn."));

        // Security: only the patient who owns this appointment can cancel
        if (appointment.getPatient() == null
                || !appointment.getPatient().getUser().getUserId().equals(user.getUserId())) {
            throw new RuntimeException("Bạn không có quyền hủy lịch hẹn này.");
        }

        // Only PENDING or SCHEDULED appointments can be cancelled by patient
        if (!"PENDING".equals(appointment.getStatus()) && !"SCHEDULED".equals(appointment.getStatus())) {
            throw new RuntimeException("Không thể hủy lịch hẹn đã được xác nhận hoặc hoàn tất.");
        }

        appointment.setStatus("CANCELLED");
        appointmentRepository.save(appointment);
    }

    @Override
    @Transactional
    public Patient updatePatientProfile(String patientEmail, String fullName, String phone,
                                        String gender, java.time.LocalDate dateOfBirth,
                                        String address, String medicalHistory) {
        User user = userAccountService.findByEmail(patientEmail);
        Patient patient = patientRepository.findByUser(user)
                .orElseGet(() -> {
                    Patient p = new Patient();
                    p.setUser(user);
                    return p;
                });

        if (fullName != null && !fullName.isBlank()) patient.setFullName(fullName);
        if (phone != null) patient.setPhone(phone);
        if (gender != null) patient.setGender(gender);
        if (dateOfBirth != null) patient.setDateOfBirth(dateOfBirth);
        if (address != null) patient.setAddress(address);
        if (medicalHistory != null) patient.setMedicalHistory(medicalHistory);

        // Also update the User fullName to stay in sync
        if (fullName != null && !fullName.isBlank()) {
            user.setFullName(fullName);
            userRepository.save(user);
        }

        return patientRepository.save(patient);
    }
}
