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
            record.setRecordCode(nextCode("IMG", imagingRecordRepository.count() + 1));
            record.setPatient(patientUser);
            record.setBodyPart(bodyPart);

            ResponseEntity<String> response = null;
            try {
                response = restTemplate.postForEntity(AI_SERVICE_URL, requestEntity, String.class);
            } catch (Exception e) {
                record.setFileName(originalFileName);
                record.setAiPrediction("Lỗi kết nối AI: " + e.getMessage());
                record.setAiConfidence(0);
                record.setStatus("AI_FAILED");
                return imagingRecordRepository.save(record);
            }
            
            if (response != null && response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode jsonNode = objectMapper.readTree(response.getBody());
                boolean fractureDetected = jsonNode.get("fracture_detected").asBoolean();
                int confidence = (int) (jsonNode.get("highest_confidence").asDouble() * 100);
                
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
                
                String prediction;
                if (fractureDetected) {
                    if (confidence >= 80) {
                        prediction = "Phát hiện có gãy xương (Độ tin cậy rất cao: " + confidence + "%)";
                    } else if (confidence >= 50) {
                        prediction = "Có khả năng gãy xương (Độ tin cậy khá: " + confidence + "%)";
                    } else {
                        prediction = "Nghi ngờ gãy xương nhưng độ tin cậy thấp (" + confidence + "%). Cần bác sĩ kiểm tra.";
                    }
                } else {
                    prediction = "Không phát hiện gãy xương";
                }
                
                record.setFileName(finalFileName);
                record.setAiPrediction(prediction);
                record.setAiConfidence(confidence);
                record.setStatus("AI_ANALYZED");
                
            } else {
                record.setFileName(originalFileName);
                record.setAiPrediction("Lỗi phân tích AI");
                record.setAiConfidence(0);
                record.setStatus("AI_FAILED");
            }

            return imagingRecordRepository.save(record);

        } catch (IOException e) {
            throw new RuntimeException("Lỗi xử lý file upload", e);
        }
    }

    private String nextCode(String prefix, long next) {
        return prefix + "-" + LocalDate.now().getYear() + "-" + String.format("%05d", next);
    }
}
