package com.ssafy.backend.meeting.client;

import java.time.Duration;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.ssafy.backend.meeting.dto.ResponseProcessMeetingDto;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class AiTranscriptionClient {

    private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";

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

    public ResponseProcessMeetingDto processMeeting(Long meetingId) {
        ResponseProcessMeetingDto response = executeRequest(
                () -> restClient.post()
                        .uri("/meetings/{meetingId}/process", meetingId)
                        .retrieve()
                        .body(ResponseProcessMeetingDto.class),
                "처리"
        );

        validateProcessResponse(meetingId, response);
        return response;
    }

    private <T> T executeRequest(
            Supplier<T> request,
            String operation
    ) {
        try {
            return request.get();
        } catch (ResourceAccessException exception) {
            throw new AiTranscriptionException(
                    AiTranscriptionFailureType.RETRYABLE,
                    "AI 회의 " + operation + " API 연결 또는 응답 시간 초과",
                    exception
            );
        } catch (RestClientResponseException exception) {
            AiTranscriptionFailureType failureType =
                    isRetryableStatus(exception.getStatusCode())
                            ? AiTranscriptionFailureType.RETRYABLE
                            : AiTranscriptionFailureType.NON_RETRYABLE;
            throw new AiTranscriptionException(
                    failureType,
                    "AI 회의 " + operation + " API HTTP 오류: "
                            + exception.getStatusCode().value(),
                    exception
            );
        } catch (RestClientException exception) {
            throw new AiTranscriptionException(
                    AiTranscriptionFailureType.NON_RETRYABLE,
                    "AI 회의 " + operation + " API 응답 처리 실패",
                    exception
            );
        }
    }

    private void validateProcessResponse(
            Long meetingId,
            ResponseProcessMeetingDto response
    ) {
        if (response == null
                || !meetingId.equals(response.meetingId())
                || !StringUtils.hasText(response.jobId())
                || !StringUtils.hasText(response.status())) {
            log.warn(
                    "AI 회의 처리 API 응답 불일치: meetingId={}, response={}",
                    meetingId,
                    response
            );
            throw new AiTranscriptionException(
                    AiTranscriptionFailureType.NON_RETRYABLE,
                    "AI 회의 처리 API 응답 검증 실패"
            );
        }
    }

    private boolean isRetryableStatus(HttpStatusCode statusCode) {
        int status = statusCode.value();
        return statusCode.is5xxServerError()
                || status == 408
                || status == 429;
    }
}
