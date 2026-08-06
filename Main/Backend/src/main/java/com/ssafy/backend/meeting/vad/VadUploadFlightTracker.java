package com.ssafy.backend.meeting.vad;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * MEET-12 in-flight 업로드 추적기.
 * MEET-12 서비스는 저장 시작 시 {@link #begin(long)}, 종료 시 {@link #end(long)}를 호출한다.
 * 회의 종료 후 AI process 호출 전에 {@link #awaitIdle(long)}로 대기를 수행한다.
 */
@Slf4j
@Component
public class VadUploadFlightTracker {

    private final ConcurrentMap<Long, AtomicInteger> inFlightByMeeting =
            new ConcurrentHashMap<>();

    private final Duration drainTimeout;
    private final Duration quietPeriod;

    public VadUploadFlightTracker(
            @Value("${meeting.vad.drain-timeout:20s}") Duration drainTimeout,
            @Value("${meeting.vad.quiet-period:300ms}") Duration quietPeriod
    ) {
        this.drainTimeout = drainTimeout;
        this.quietPeriod = quietPeriod;
    }

    public void begin(long meetingId) {
        inFlightByMeeting
                .computeIfAbsent(meetingId, ignored -> new AtomicInteger())
                .incrementAndGet();
    }

    public void end(long meetingId) {
        AtomicInteger counter = inFlightByMeeting.get(meetingId);
        if (counter == null) {
            return;
        }
        int left = counter.decrementAndGet();
        if (left <= 0) {
            inFlightByMeeting.remove(meetingId, counter);
        }
    }

    /**
     * in-flight == 0 이고 quietPeriod 동안 새 begin이 없으면 true.
     * timeout 초과 시 false.
     */
    public boolean awaitIdle(long meetingId) {
        long deadlineNanos = System.nanoTime() + drainTimeout.toNanos();
        long quietNanos = quietPeriod.toNanos();
        long idleSinceNanos = -1L;

        while (System.nanoTime() < deadlineNanos) {
            if (Thread.currentThread().isInterrupted()) {
                Thread.currentThread().interrupt();
                log.warn(
                        "VAD upload drain interrupted: meetingId={}",
                        meetingId
                );
                return false;
            }

            int inFlight = currentInFlight(meetingId);
            if (inFlight > 0) {
                idleSinceNanos = -1L;
                sleepBriefly();
                continue;
            }

            if (idleSinceNanos < 0) {
                idleSinceNanos = System.nanoTime();
            }

            if (System.nanoTime() - idleSinceNanos >= quietNanos) {
                log.info(
                        "VAD upload drain completed: meetingId={}",
                        meetingId
                );
                return true;
            }
            sleepBriefly();
        }

        log.warn(
                "VAD upload drain timed out: meetingId={}, inFlight={}",
                meetingId,
                currentInFlight(meetingId)
        );
        return false;
    }

    public void clear(long meetingId) {
        inFlightByMeeting.remove(meetingId);
    }

    int currentInFlight(long meetingId) {
        AtomicInteger counter = inFlightByMeeting.get(meetingId);
        return counter == null ? 0 : Math.max(0, counter.get());
    }

    private void sleepBriefly() {
        try {
            Thread.sleep(50L);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
