package com.ssafy.backend.meeting.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import com.ssafy.backend.meeting.storage.RecordingStorageProperties;

@Configuration
@EnableConfigurationProperties(RecordingStorageProperties.class)
public class RecordingStorageConfiguration {
}
