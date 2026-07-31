package com.ssafy.backend.meeting.livekit.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "livekit")
public record LiveKitProperties(
        String url,
        String apiKey,
        String apiSecret,
        Duration tokenTtl
) {

    public String apiUrl() {
        if (url.startsWith("wss://")) {
            return "https://" + url.substring("wss://".length());
        }
        if (url.startsWith("ws://")) {
            return "http://" + url.substring("ws://".length());
        }
        return url;
    }
}
