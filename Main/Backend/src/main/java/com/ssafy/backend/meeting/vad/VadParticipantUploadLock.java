package com.ssafy.backend.meeting.vad;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.stereotype.Component;

@Component
public class VadParticipantUploadLock {

    private final ConcurrentMap<String, Object> locks = new ConcurrentHashMap<>();

    public Object lockFor(long meetingId, long participantId) {
        return locks.computeIfAbsent(meetingId + ":" + participantId, ignored -> new Object());
    }
}
