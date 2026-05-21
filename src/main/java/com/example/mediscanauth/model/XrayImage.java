package com.example.mediscanauth.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "xray_images")
public class XrayImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "image_id")
    private Long imageId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "record_id", nullable = false)
    private MedicalRecord record;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "uploaded_by")
    private User uploadedBy;

    @Column(name = "original_image_path", nullable = false, length = 500)
    private String originalImagePath;

    @Column(name = "image_type", length = 50)
    private String imageType = "X-RAY";

    @Column(name = "body_part", length = 100)
    private String bodyPart;

    @Column(name = "view_position", length = 50)
    private String viewPosition;

    @Column(name = "upload_source", columnDefinition = "enum('TECHNICIAN','PATIENT')")
    private String uploadSource = "TECHNICIAN";

    @Column(name = "status", columnDefinition = "enum('UPLOADED','PROCESSING','ANALYZED','FAILED')")
    private String status = "UPLOADED";

    @Column(name = "uploaded_at", insertable = false, updatable = false)
    private LocalDateTime uploadedAt;

    public Long getImageId() {
        return imageId;
    }

    public MedicalRecord getRecord() {
        return record;
    }

    public void setRecord(MedicalRecord record) {
        this.record = record;
    }

    public User getUploadedBy() {
        return uploadedBy;
    }

    public void setUploadedBy(User uploadedBy) {
        this.uploadedBy = uploadedBy;
    }

    public String getOriginalImagePath() {
        return originalImagePath;
    }

    public void setOriginalImagePath(String originalImagePath) {
        this.originalImagePath = originalImagePath;
    }

    public String getImageType() {
        return imageType;
    }

    public void setImageType(String imageType) {
        this.imageType = imageType;
    }

    public String getBodyPart() {
        return bodyPart;
    }

    public void setBodyPart(String bodyPart) {
        this.bodyPart = bodyPart;
    }

    public String getViewPosition() {
        return viewPosition;
    }

    public void setViewPosition(String viewPosition) {
        this.viewPosition = viewPosition;
    }

    public String getUploadSource() {
        return uploadSource;
    }

    public void setUploadSource(String uploadSource) {
        this.uploadSource = uploadSource;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getUploadedAt() {
        return uploadedAt;
    }
}
