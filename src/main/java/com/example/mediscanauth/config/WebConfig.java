package com.example.mediscanauth.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Lấy đường dẫn tuyệt đối của thư mục uploads/xray ở gốc dự án
        Path uploadDir = Paths.get("uploads/xray");
        String uploadPath = uploadDir.toFile().getAbsolutePath();
        
        // Chuẩn hóa dấu gạch chéo cho Windows (thay \ bằng /)
        String formattedPath = uploadPath.replace("\\", "/");
        
        // Windows yêu cầu bắt đầu bằng file:/// (3 dấu xoẹt) sau đó là đường dẫn ổ đĩa
        String resourceLocation = "file:///" + formattedPath + "/";
        
        // Nếu chạy trên Linux/macOS thì chỉ cần file:
        if (uploadPath.startsWith("/")) {
            resourceLocation = "file:" + formattedPath + "/";
        }

        registry.addResourceHandler("/uploads/xray/**")
                .addResourceLocations(resourceLocation);
    }
}