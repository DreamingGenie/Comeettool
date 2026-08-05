package com.ssafy.backend.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * 로컬 파일 스토리지(LocalFileStorageService)가 저장한 파일을 /files/** 경로로 서빙한다. PROFILE_IMG=S3인 경우에는 이 핸들러가 등록되더라도 /uploads/ 디렉터리가
 * 비어 있어 무해하다.
 */
@Configuration
@ConditionalOnProperty(name = "profile-img", havingValue = "FILE")
public class WebMvcConfig implements WebMvcConfigurer {

    @Value("${file.upload-dir:/uploads}")
    private String uploadDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/files/**")
                .addResourceLocations("file:" + uploadDir + "/");
    }
}