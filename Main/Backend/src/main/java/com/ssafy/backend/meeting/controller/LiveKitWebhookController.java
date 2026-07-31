package com.ssafy.backend.meeting.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ssafy.backend.meeting.livekit.LiveKitWebhookService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/webhooks")
@RequiredArgsConstructor
public class LiveKitWebhookController {

    private static final String LIVEKIT_WEBHOOK_MEDIA_TYPE = "application/webhook+json";

    private final LiveKitWebhookService liveKitWebhookService;

    @PostMapping(
            value = "/livekit",
            consumes = LIVEKIT_WEBHOOK_MEDIA_TYPE
    )
    public ResponseEntity<Void> receiveLiveKitWebhook(
            @RequestHeader(
                    value = HttpHeaders.AUTHORIZATION,
                    required = false
            ) String authorizationHeader,
            @RequestBody String rawBody
    ) {
        liveKitWebhookService.handle(rawBody, authorizationHeader);
        return ResponseEntity.ok().build();
    }
}
