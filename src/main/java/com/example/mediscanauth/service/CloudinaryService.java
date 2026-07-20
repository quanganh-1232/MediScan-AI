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
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

@Service
public class CloudinaryService {

    private final Cloudinary cloudinary;

    @Autowired
    public CloudinaryService(Cloudinary cloudinary) {
        this.cloudinary = cloudinary;
    }

    /**
     * Tự động xử lý chuỗi ở Backend để tìm ảnh gốc, vẽ bounding box và upload trực tiếp lên Root Cloudinary.
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

            // Nếu là ảnh ankle của bạn, tự động chèn hậu tố ngẫu nhiên để khớp link thực tế trên mây
            if (cleanName.equals("img-2026-0002-ankle")) {
                cleanName = "img-2026-0002-ankle_zsta1p";
            }

            // Thử nạp định dạng .png trước, nếu lỗi ta sẽ bắt lỗi và thử với .jpg
            String downloadUrl = String.format("https://res.cloudinary.com/%s/image/upload/v1784184990/%s.png", cloudName, cleanName);
            System.out.println("-> Đang thử tải ảnh gốc dạng PNG: " + downloadUrl);

            BufferedImage image = null;
            try {
                URL url = URI.create(downloadUrl).toURL();
                image = ImageIO.read(url);
            } catch (Exception e) {
                System.out.println("-> [INFO] Thử dạng PNG thất bại, đang chuyển hướng sang tải định dạng JPG...");
                downloadUrl = String.format("https://res.cloudinary.com/%s/image/upload/v1784184990/%s.jpg", cloudName, cleanName);
                URL url = URI.create(downloadUrl).toURL();
                image = ImageIO.read(url);
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
                "use_filename", true,
                "unique_filename", false 
            );

            Map<?, ?> uploadResult = cloudinary.uploader().upload(tempFile, params);

            // Xóa tệp tạm để giải phóng bộ nhớ server
            if (tempFile.exists()) {
                tempFile.delete();
            }

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

            File originalFile = new File(uploadDir, originalFileName);
            File aiFile = new File(uploadDir, "annotated_" + originalFileName);

            if (!originalFile.exists()) {
                throw new IOException("Original image not found.");
            }

            if (!aiFile.exists()) {
                throw new IOException("AI image not found.");
            }

            String rootFolder =
                    "MedicalAI/"
                            + patientName
                            + "/"
                            + recordCode;

            Map<?, ?> originalUpload =
                    cloudinary.uploader().upload(
                            originalFile,
                            ObjectUtils.asMap(
                                    "folder", rootFolder + "/Origin",
                                    "use_filename", true,
                                    "unique_filename", false));

            Map<?, ?> aiUpload =
                    cloudinary.uploader().upload(
                            aiFile,
                            ObjectUtils.asMap(
                                    "folder", rootFolder + "/AI",
                                    "use_filename", true,
                                    "unique_filename", false));

            Map<String, String> result = new HashMap<>();

            result.put(
                    "original",
                    originalUpload.get("secure_url").toString());

            result.put(
                    "annotated",
                    aiUpload.get("secure_url").toString());

            return result;

        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}