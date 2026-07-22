package com.example.mediscanauth.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.example.mediscanauth.model.dto.CloudinaryUploadResult;
import org.springframework.web.multipart.MultipartFile;
import java.awt.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.Map;

@Service
public class CloudinaryService {

    private final Cloudinary cloudinary;

    @Autowired
    public CloudinaryService(Cloudinary cloudinary) {
        this.cloudinary = cloudinary;
    }

    /**
     * Hàm hỗ trợ loại bỏ hoàn toàn dấu tiếng Việt để tạo tên thư mục an toàn trên Cloudinary.
     */
    public String generateAndUploadDoctorImage(String dbFileName, int x, int y, int w, int h) {
        try {
            System.out.println("====== [BACKEND CLOUDINARY CONVERSION] ======");
            System.out.println("-> Tên file gốc từ DB: " + dbFileName);

            // 1. TỰ ĐỘNG BIẾN ĐỔI CHUỖI SANG LINK CLOUDINARY THỰC TẾ
            String cloudName = "xo59jq3r";
            String cleanName = dbFileName;
            if (cleanName.contains(".")) {
                cleanName = cleanName.substring(0, cleanName.lastIndexOf('.'));
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

            if (image == null) {
                throw new IOException("Không thể đọc dữ liệu ảnh từ Cloudinary với cả 2 định dạng PNG/JPG.");
            }

            // 2. TIẾN HÀNH VẼ BOUNDING BOX MÀU ĐỎ
            Graphics2D g2d = image.createGraphics();
            g2d.setColor(Color.RED); 
            g2d.setStroke(new BasicStroke(4)); // Khung viền dày 4px
            g2d.drawRect(x, y, w, h);
            g2d.dispose();

            // 3. TẠO FILE TẠM VỚI TIỀN TỐ doctor_
            String doctorFileName = "doctor_" + cleanName;
            File tempFile = File.createTempFile(doctorFileName + "_", ".png");
            ImageIO.write(image, "png", tempFile);
            System.out.println("-> Đã tạo tệp vẽ tạm thành công: " + tempFile.getAbsolutePath());

            // 4. UPLOAD TRỰC TIẾP LÊN THƯ MỤC GỐC (ROOT) CỦA CLOUDINARY
            // Loại bỏ hoàn toàn tham số "folder" để tránh xung đột thư mục chưa tạo
            Map<String, Object> params = ObjectUtils.asMap(
                    "folder", folderPath,
                    "public_id", doctorFileName,
                    "overwrite", true,
                    "invalidate", true);

            Map<?, ?> result = cloudinary.uploader().upload(base64ImageData, params);
            String secureUrl = (String) result.get("secure_url");

            String secureUrl = uploadResult.get("secure_url").toString();
            System.out.println("-> [THÀNH CÔNG] Ảnh của bác sĩ đã lên Cloudinary: " + secureUrl);
            System.out.println("=============================================");

            return secureUrl;

        } catch (IOException e) {
            System.err.println("-> [LỖI NGHIÊM TRỌNG] Thất bại trong tiến trình vẽ/tải ảnh: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    public Map<String, String> uploadTechnicianImages(
            String uploadDir,
            String originalFileName,
            String patientName,
            String recordCode) {

        try {

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