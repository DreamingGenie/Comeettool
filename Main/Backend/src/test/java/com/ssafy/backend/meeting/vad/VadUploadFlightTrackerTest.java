package com.ssafy.backend.meeting.vad;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("VAD 업로드 in-flight 추적 테스트")
class VadUploadFlightTrackerTest {

    @Test
    @DisplayName("in-flight가 없으면 quietPeriod 후 idle 한다")
    void awaitIdle_whenNoInFlight_returnsTrue() {
        VadUploadFlightTracker tracker = new VadUploadFlightTracker(
                Duration.ofSeconds(2),
                Duration.ofMillis(100)
        );

        assertThat(tracker.awaitIdle(1L)).isTrue();
    }

    @Test
    @DisplayName("in-flight가 끝나면 idle 한다")
    void awaitIdle_waitsUntilInFlightClears() throws Exception {
        VadUploadFlightTracker tracker = new VadUploadFlightTracker(
                Duration.ofSeconds(3),
                Duration.ofMillis(100)
        );
        tracker.begin(1L);

        Future<Boolean> future = Executors.newSingleThreadExecutor()
                .submit(() -> tracker.awaitIdle(1L));

        Thread.sleep(200L);
        tracker.end(1L);

        assertThat(future.get(2, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    @DisplayName("timeout이면 false를 반환한다")
    void awaitIdle_timesOut() {
        VadUploadFlightTracker tracker = new VadUploadFlightTracker(
                Duration.ofMillis(200),
                Duration.ofMillis(50)
        );
        tracker.begin(1L);

        assertThat(tracker.awaitIdle(1L)).isFalse();
        tracker.end(1L);
    }
}
