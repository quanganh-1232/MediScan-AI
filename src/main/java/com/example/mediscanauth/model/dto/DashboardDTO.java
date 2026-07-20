package com.example.mediscanauth.model.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class DashboardDTO {
    private long queueCount;
    private List<QueueItemDTO> queueRecords;

    @Data
    @Builder
    public static class QueueItemDTO {
        private Long recordId;
        private String recordCode;
        private java.time.LocalDateTime capturedAt;

        // Thay vì String patientName, ta dùng Object PatientDTO
        private PatientDTO patient;

        private String bodyPart;
        private String aiPrediction;
        private Double aiConfidence;
        private String status;
        private String fileName;           
        private String doctorConclusion;
    }

    @Data
    @Builder
    public static class PatientDTO {
        private String fullName;
        private String gender;
    }
}