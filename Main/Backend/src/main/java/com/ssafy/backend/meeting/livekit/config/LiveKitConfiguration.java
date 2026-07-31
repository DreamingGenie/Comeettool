package com.ssafy.backend.meeting.livekit.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.livekit.server.RoomServiceClient;
import io.livekit.server.WebhookReceiver;

@Configuration
@EnableConfigurationProperties(LiveKitProperties.class)
public class LiveKitConfiguration {

    @Bean
    public RoomServiceClient liveKitRoomServiceClient(LiveKitProperties liveKitProperties) {
        return RoomServiceClient.createClient(
                liveKitProperties.apiUrl(),
                liveKitProperties.apiKey(),
                liveKitProperties.apiSecret()
        );
    }

    @Bean
    public WebhookReceiver liveKitWebhookReceiver(LiveKitProperties liveKitProperties) {
        return new WebhookReceiver(
                liveKitProperties.apiKey(),
                liveKitProperties.apiSecret()
        );
    }
}
