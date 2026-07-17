package com.example.mediscanauth.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class CloudinaryService {

    private final Cloudinary cloudinary;
    private final String cloudName = "xo59jq3r";

    @Autowired
    public CloudinaryService(Cloudinary cloudinary) {
        this.cloudinary = cloudinary;
    }

    /**
     * Hàm hỗ trợ loại bỏ hoàn toàn dấu tiếng Việt để tạo tên thư mục an toàn trên
     * Cloudinary.
     */
    private String removeAccent(String s) {
        if (s == null)
            return "";
        String temp = Normalizer.normalize(s, Normalizer.Form.NFD);
        Pattern pattern = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
        return pattern.matcher(temp).replaceAll("").replace('đ', 'd').replace('Đ', 'D');
    }

    /**
     * Nhận chuỗi ảnh Base64 chụp màn hình từ trình duyệt của bác sĩ,
     * tự động tạo thư mục và upload trực tiếp lên Cloudinary với quy tắc đặt tên
     * mới.
     * Tên file đích: doctor_ + [tên file gốc] (Ví dụ:
     * doctor_img-2026-0002-ankle.png)
     */
    public String generateAndUploadDoctorImage(
            String base64ImageData,
            String patientName,
            String recordCode,
            String dbFileName) { // <-- Thêm tham số dbFileName để lấy tên file gốc

        try {
            System.out.println("====== [UPLOAD SCREENSHOT TO DOCTOR FOLDER] ======");
            System.out.println("Patient   : " + patientName);
            System.out.println("Record    : " + recordCode);
            System.out.println("Origin File: " + dbFileName);

            String safePatient = removeAccent(patientName.trim());

            // Thư mục đích trên Cloudinary
            String folderPath = String.format(
                    "MedicalAI/%s/%s/Doctor",
                    safePatient,
                    recordCode.trim());

            // Quy tắc đặt tên file ảnh bác sĩ: doctor_[tên file gốc]
            String doctorFileName;
            if (dbFileName != null && !dbFileName.trim().isEmpty()) {
                // Loại bỏ phần mở rộng (ví dụ .png, .jpg) nếu có vì Cloudinary tự quản lý
                // extension hoặc định danh qua public_id
                String rawName = dbFileName.trim();
                if (rawName.contains(".")) {
                    rawName = rawName.substring(0, rawName.lastIndexOf("."));
                }
                doctorFileName = "doctor_" + rawName;
            } else {
                // Phương án dự phòng nếu dbFileName bị null
                doctorFileName = "doctor_img-" + System.currentTimeMillis();
            }

            Map<String, Object> params = ObjectUtils.asMap(
                    "folder", folderPath,
                    "public_id", doctorFileName, // Đặt tên file đúng theo quy tắc
                    "overwrite", true,
                    "invalidate", true);

            // Cloudinary hỗ trợ nhận trực tiếp chuỗi Base64
            Map<?, ?> result = cloudinary.uploader().upload(base64ImageData, params);

            String secureUrl = (String) result.get("secure_url");

            System.out.println("======================================");
            System.out.println("UPLOAD SCREENSHOT SUCCESS");
            System.out.println("Filename: " + doctorFileName);
            System.out.println("URL: " + secureUrl);
            System.out.println("======================================");

            return secureUrl;

        } catch (Exception e) {
            System.err.println("UPLOAD SCREENSHOT FAILED");
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Lấy link hiển thị tương ứng với cấu trúc thư mục MedicalAI
     */
    public String getDisplayImageUrl(String dbFileName, String status, String patientName, String recordCode) {
        if (dbFileName == null || dbFileName.trim().isEmpty()) {
            return "";
        }

        String cleanName = dbFileName.contains(".")
                ? dbFileName.substring(0, dbFileName.lastIndexOf('.'))
                : dbFileName.trim();

        if ("COMPLETED".equalsIgnoreCase(status)) {
            String doctorName = "doctor_" + cleanName.replaceAll("[^a-zA-Z0-9_-]", "_");
            String safePatient = removeAccent(patientName.trim());
            String safeRecord = recordCode.trim();

            return String.format(
                    "https://res.cloudinary.com/%s/image/upload/f_auto,q_auto/MedicalAI/%s/%s/Doctor/%s.jpg",
                    cloudName, safePatient, safeRecord, doctorName);
        }

        return "https://res.cloudinary.com/" + cloudName + "/image/upload/f_auto,q_auto/" + dbFileName;
    }
}