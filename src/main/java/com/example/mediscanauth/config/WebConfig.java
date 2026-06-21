package com.example.mediscanauth.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // CHỈ ĐỊNH: Map chuỗi ảo /uploads/** vào tận thư mục gốc SWP/uploads/ trên máy bác
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:///C:/Users/NGUYEN QUANG ANH/Desktop/SWP/uploads");
    }
}