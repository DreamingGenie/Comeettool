package com.ssafy.backend.meeting.livekit;

public interface LiveKitWebhookService {

    void handle(String rawBody, String authorizationHeader);
}
