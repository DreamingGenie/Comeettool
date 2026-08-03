package com.ssafy.backend.meeting.config;

import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class MeetingTranscriptionTaskConfig {

    public static final String TRANSCRIPTION_TASK_EXECUTOR =
            "meetingTranscriptionTaskExecutor";
    public static final String TRANSCRIPTION_RETRY_SCHEDULER =
            "meetingTranscriptionRetryScheduler";

    @Bean(name = TRANSCRIPTION_TASK_EXECUTOR)
    public ThreadPoolTaskExecutor meetingTranscriptionTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("meeting-transcription-http-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.setRejectedExecutionHandler(
                new ThreadPoolExecutor.AbortPolicy()
        );
        return executor;
    }

    @Bean(name = TRANSCRIPTION_RETRY_SCHEDULER)
    public ThreadPoolTaskScheduler meetingTranscriptionRetryScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("meeting-transcription-retry-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(10);
        scheduler.setRejectedExecutionHandler(
                new ThreadPoolExecutor.AbortPolicy()
        );
        return scheduler;
    }
}
