package com.example.mediscanauth.model.dto;

public class CloudinaryUploadResult {
    private String originalImageUrl;
    private String aiImageUrl;

    public CloudinaryUploadResult() {
    }

    public CloudinaryUploadResult(String originalImageUrl, String aiImageUrl) {
        this.originalImageUrl = originalImageUrl;
        this.aiImageUrl = aiImageUrl;
    }

    public String getOriginalImageUrl() {
        return originalImageUrl;
    }

    public void setOriginalImageUrl(String originalImageUrl) {
        this.originalImageUrl = originalImageUrl;
    }

    public String getAiImageUrl() {
        return aiImageUrl;
    }

    public void setAiImageUrl(String aiImageUrl) {
        this.aiImageUrl = aiImageUrl;
    }
}
