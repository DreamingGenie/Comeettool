package com.ssafy.backend.meeting.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.ThreadPoolExecutor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@DisplayName("회의 STT 작업 스레드 설정 테스트")
class MeetingTranscriptionTaskConfigTest {

    private final MeetingTranscriptionTaskConfig config =
            new MeetingTranscriptionTaskConfig();

    @Test
    @DisplayName("HTTP Executor는 포화 시 작업을 거부한다")
    void meetingTranscriptionTaskExecutor_usesAbortPolicy() {
        ThreadPoolTaskExecutor executor =
                config.meetingTranscriptionTaskExecutor();
        executor.initialize();

        try {
            assertThat(
                    executor.getThreadPoolExecutor()
                            .getRejectedExecutionHandler()
            ).isInstanceOf(ThreadPoolExecutor.AbortPolicy.class);
        } finally {
            executor.shutdown();
        }
    }

    @Test
    @DisplayName("재시도 Scheduler는 예약만 담당하는 단일 스레드를 사용한다")
    void meetingTranscriptionRetryScheduler_usesSingleThread() {
        ThreadPoolTaskScheduler scheduler =
                config.meetingTranscriptionRetryScheduler();
        scheduler.initialize();

        try {
            assertThat(
                    scheduler.getScheduledThreadPoolExecutor()
                            .getCorePoolSize()
            ).isEqualTo(1);
            assertThat(
                    scheduler.getScheduledThreadPoolExecutor()
                            .getRejectedExecutionHandler()
            ).isInstanceOf(ThreadPoolExecutor.AbortPolicy.class);
        } finally {
            scheduler.shutdown();
        }
    }
}
