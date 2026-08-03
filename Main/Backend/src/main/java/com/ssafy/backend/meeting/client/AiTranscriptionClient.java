package com.ssafy.backend.meeting.client;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.ssafy.backend.global.exception.CustomException;
import com.ssafy.backend.global.exception.ErrorCode;
import com.ssafy.backend.meeting.dto.RequestStartTranscriptionDto;
import com.ssafy.backend.meeting.dto.ResponseStartTranscriptionDto;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class AiTranscriptionClient {

    private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";
    private static final String SUCCESS_CODE = "SUCCESS";

    private final RestClient restClient;

    @Autowired
    public AiTranscriptionClient(
            RestClient.Builder restClientBuilder,
            @Value("${ai.internal-base-url}") String baseUrl,
            @Value("${ai.internal-token}") String internalToken,
            @Value("${ai.connect-timeout:3s}") Duration connectTimeout,
            @Value("${ai.read-timeout:5s}") Duration readTimeout
    ) {
        SimpleClientHttpRequestFactory requestFactory =
                new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeout);
        requestFactory.setReadTimeout(readTimeout);

        this.restClient = restClientBuilder
                .requestFactory(requestFactory)
                .baseUrl(baseUrl)
                .defaultHeader(INTERNAL_TOKEN_HEADER, internalToken)
                .build();
    }

    AiTranscriptionClient(RestClient restClient) {
        this.restClient = restClient;
    }

    public ResponseStartTranscriptionDto startTranscription(
            Long meetingId,
            String startedAt
    ) {
        try {
            ResponseStartTranscriptionDto response = restClient.post()
                    .uri(
                            "/internal/v1/meetings/{meetingId}/transcription/start",
                            meetingId
                    )
                    .body(new RequestStartTranscriptionDto(startedAt))
                    .retrieve()
                    .body(ResponseStartTranscriptionDto.class);

            validateResponse(meetingId, response);
            return response;
        } catch (RestClientException exception) {
            log.warn(
                    "AI 회의 시작 API 호출 실패: meetingId={}",
                    meetingId,
                    exception
            );
            throw new CustomException(
                    ErrorCode.MEETING_AI_TRANSCRIPTION_START_FAILED
            );
        }
    }

    private void validateResponse(
            Long meetingId,
            ResponseStartTranscriptionDto response
    ) {
        String expectedS3Prefix = "conferences/" + meetingId + "/";

        if (response == null
                || !SUCCESS_CODE.equals(response.code())
                || !meetingId.equals(response.meetingRoomId())
                || !expectedS3Prefix.equals(response.s3Prefix())) {
            log.warn(
                    "AI 회의 시작 API 응답 불일치: meetingId={}, response={}",
                    meetingId,
                    response
            );
            throw new CustomException(
                    ErrorCode.MEETING_AI_TRANSCRIPTION_START_FAILED
            );
        }
    }
}
