package com.example.mediscanauth.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "ai_analysis_results")
public class AiAnalysisResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ai_result_id")
    private Long aiResultId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "image_id", nullable = false)
    private XrayImage image;

    @Column(name = "fracture_detected", nullable = false)
    private boolean fractureDetected;

    @Column(name = "confidence_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal confidenceScore;

    @Column(name = "severity_level", columnDefinition = "enum('LOW','MEDIUM','HIGH')")
    private String severityLevel;

    @Column(name = "ai_diagnosis", columnDefinition = "text")
    private String aiDiagnosis;

    @Column(name = "analyzed_at", insertable = false, updatable = false)
    private LocalDateTime analyzedAt;

    @Column(name = "status", columnDefinition = "enum('SUCCESS','FAILED')")
    private String status;

    public Long getAiResultId() {
        return aiResultId;
    }

    public void setAiResultId(Long aiResultId) {
        this.aiResultId = aiResultId;
    }

    public XrayImage getImage() {
        return image;
    }

    public void setImage(XrayImage image) {
        this.image = image;
    }

    public boolean isFractureDetected() {
        return fractureDetected;
    }

    public void setFractureDetected(boolean fractureDetected) {
        this.fractureDetected = fractureDetected;
    }

    public BigDecimal getConfidenceScore() {
        return confidenceScore;
    }

    public void setConfidenceScore(BigDecimal confidenceScore) {
        this.confidenceScore = confidenceScore;
    }

    public String getSeverityLevel() {
        return severityLevel;
    }

    public void setSeverityLevel(String severityLevel) {
        this.severityLevel = severityLevel;
    }

    public String getAiDiagnosis() {
        return aiDiagnosis;
    }

    public void setAiDiagnosis(String aiDiagnosis) {
        this.aiDiagnosis = aiDiagnosis;
    }

    public LocalDateTime getAnalyzedAt() {
        return analyzedAt;
    }

    public void setAnalyzedAt(LocalDateTime analyzedAt) {
        this.analyzedAt = analyzedAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
