package com.example.mediscanauth.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
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
     * Hàm hỗ trợ loại bỏ hoàn toàn dấu tiếng Việt để tạo tên thư mục an toàn trên Cloudinary.
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
     * tự động tạo thư mục và upload trực tiếp lên Cloudinary với quy tắc đặt tên mới.
     * Tên file đích: doctor_ + [tên file gốc] (Ví dụ: doctor_img-2026-0002-ankle.png)
     */
    public String generateAndUploadDoctorImage(
            String base64ImageData,
            String patientName,
            String recordCode,
            String dbFileName) {

        try {
            System.out.println("====== [UPLOAD SCREENSHOT TO DOCTOR FOLDER] ======");
            System.out.println("Patient    : " + patientName);
            System.out.println("Record     : " + recordCode);
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
                String rawName = dbFileName.trim();
                if (rawName.contains(".")) {
                    rawName = rawName.substring(0, rawName.lastIndexOf("."));
                }
                doctorFileName = "doctor_" + rawName;
            } else {
                doctorFileName = "doctor_img-" + System.currentTimeMillis();
            }

            Map<String, Object> params = ObjectUtils.asMap(
                    "folder", folderPath,
                    "public_id", doctorFileName,
                    "overwrite", true,
                    "invalidate", true);

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

        // Tách lấy phần tên (bỏ đuôi mở rộng nếu có)
        String cleanName = dbFileName.contains(".")
                ? dbFileName.substring(0, dbFileName.lastIndexOf('.'))
                : dbFileName.trim();

        if ("COMPLETED".equalsIgnoreCase(status)) {
            // Loại bỏ ký tự đặc biệt không hợp lệ trong filename, thay bằng '_'
            String safeFileName = cleanName.replaceAll("[^a-zA-Z0-9_-]", "_");
            String doctorName = "doctor_" + safeFileName;
            String safePatient = removeAccent(patientName.trim());
            String safeRecord = recordCode.trim();

            return String.format(
                    "https://res.cloudinary.com/%s/image/upload/f_auto,q_auto/MedicalAI/%s/%s/Doctor/%s",
                    cloudName, safePatient, safeRecord, doctorName);
        }else{
            String safeFileName = cleanName.replaceAll("[^a-zA-Z0-9_-]", "_");
            String aiName = "annotated_" + safeFileName;
            String safePatient = removeAccent(patientName.trim());
            String safeRecord = recordCode.trim();

            return String.format(
                    "https://res.cloudinary.com/%s/image/upload/f_auto,q_auto/MedicalAI/%s/%s/AI/%s",
                    cloudName, safePatient, safeRecord, aiName);
        }
    }

    /**
     * Lấy link ảnh gốc trong thư mục Origin (Nếu cần dùng đến)
     */
    public String getOriginalImageUrl(String dbFileName, String patientName, String recordCode) {
        if (dbFileName == null || dbFileName.trim().isEmpty()) {
            return "https://via.placeholder.com/600x400?text=ORIGINAL+NOT+FOUND";
        }

        String cleanName = dbFileName.contains(".")
                ? dbFileName.substring(0, dbFileName.lastIndexOf('.'))
                : dbFileName.trim();

        String safeFileName = cleanName.replaceAll("[^a-zA-Z0-9_-]", "_");
        String safePatient = removeAccent(patientName.trim());
        String safeRecord = recordCode.trim();

        return String.format(
                "https://res.cloudinary.com/%s/image/upload/f_auto,q_auto/MedicalAI/%s/%s/Origin/%s?t=%d",
                cloudName, safePatient, safeRecord, safeFileName, System.currentTimeMillis());
    }
}