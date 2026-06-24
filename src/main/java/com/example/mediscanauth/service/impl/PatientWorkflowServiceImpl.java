package com.example.mediscanauth.service.impl;

import com.example.mediscanauth.model.ImagingRecord;
import com.example.mediscanauth.model.User;
import com.example.mediscanauth.repository.ImagingRecordRepository;
import com.example.mediscanauth.service.PatientWorkflowService;
import com.example.mediscanauth.service.UserAccountService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    // TODO: move to application.properties
    private static final String UPLOAD_DIR = "src/main/resources/static/uploads/";
    private static final String AI_SERVICE_URL = "http://localhost:8000/predict";
    private static final int LEGACY_TEXT_COLUMN_LIMIT = 490;

    public PatientWorkflowServiceImpl(ImagingRecordRepository imagingRecordRepository,
                                      UserAccountService userAccountService) {
        this.imagingRecordRepository = imagingRecordRepository;
        this.userAccountService = userAccountService;
        this.restTemplate = new RestTemplate();
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
                response = restTemplate.postForEntity(AI_SERVICE_URL, requestEntity, String.class);
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
                    finalFileName = annotatedFileName;
                }
                
                String clinicalText = !diagnosisImpression.isBlank() ? diagnosisImpression : diagnosisSummary;
                String aiPredictionText = buildAiPrediction(fractureDetected, confidence, bodyPart, clinicalText, riskLevel, fractureScore);
                String aiRecommendationText = buildAiRecommendation(fractureDetected, confidence, riskLevel);

                record.setFileName(finalFileName);
                record.setAiPrediction(limitText(aiPredictionText, LEGACY_TEXT_COLUMN_LIMIT));
                record.setAiConfidence(confidence);
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
        builder.append("Kết luận tạm thời:");

        if (fractureDetected) {
            builder.append(" Hình ảnh cho thấy dấu hiệu gãy ").append(bodyPart.toLowerCase()).append(", với độ tin cậy ").append(confidence).append("%.");
        } else {
            builder.append(" Chưa phát hiện bằng chứng rõ ràng của gãy ").append(bodyPart.toLowerCase()).append(", kết quả này cần được bác sĩ xác nhận thêm.");
        }

        if (!riskLevel.isBlank()) {
            builder.append(" Mức nguy cơ: ").append(toVietnameseRiskLevel(riskLevel)).append(".");
        }

        if (!clinicalText.isBlank()) {
            builder.append(" Nhận định: ").append(cleanSentence(clinicalText));
        }

        builder.append(" Điểm ANFIS: ").append(String.format("%.1f", fractureScore)).append("/100.");
        return builder.toString();
    }

    private String buildAiRecommendation(boolean fractureDetected, int confidence, String riskLevel) {
        StringBuilder builder = new StringBuilder();
        builder.append("Khuyến nghị:");

        if (fractureDetected) {
            if (confidence >= 80) {
                builder.append(" Nên liên hệ bác sĩ chuyên khoa xương khớp ngay để được chẩn đoán hình ảnh chính xác và lên kế hoạch điều trị.");
            } else {
                builder.append(" Kết quả cho thấy khả năng gãy xương, cần thăm khám lâm sàng và xét nghiệm bổ sung để xác nhận.");
            }
        } else {
            builder.append(" Dù chưa thấy dấu hiệu gãy rõ rệt, vẫn khuyến nghị theo dõi triệu chứng và tái khám nếu có đau tăng hoặc sưng tấy.");
        }

        if (!riskLevel.isBlank() && !"LOW".equalsIgnoreCase(riskLevel)) {
            builder.append(" Với mức nguy cơ ").append(riskLevel.toLowerCase()).append(", nên cân nhắc kiểm tra thêm theo chỉ dẫn bác sĩ.");
        }

        builder.append(" AI chỉ hỗ trợ phân tích ban đầu; quyết định điều trị cuối cùng cần do bác sĩ chuyên môn đưa ra.");
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
}
