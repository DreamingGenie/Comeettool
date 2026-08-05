package com.ssafy.backend.meeting.storage;

import com.ssafy.backend.global.storage.StorageDirectory;
import com.ssafy.backend.global.storage.StorageObjectKey;
import java.util.Locale;

/**
 * conferences/{meetingId}/participants/{participantId}/segment-NNNNNN.{extension}
 * 형식의 회의 세그먼트 Key를 생성한다.
 */
public final class ConferenceSegmentObjectKey {

    private static final int MAX_SEGMENT_NUMBER = 999_999;

    private ConferenceSegmentObjectKey() {
    }

    public static StorageObjectKey audio(
            Long meetingId,
            Long participantId,
            int segmentNumber
    ) {
        return create(meetingId, participantId, segmentNumber, "ogg");
    }

    public static StorageObjectKey metadata(
            Long meetingId,
            Long participantId,
            int segmentNumber
    ) {
        return create(meetingId, participantId, segmentNumber, "json");
    }

    private static StorageObjectKey create(
            Long meetingId,
            Long participantId,
            int segmentNumber,
            String extension
    ) {
        requirePositive(meetingId, "회의 ID");
        requirePositive(participantId, "참여자 ID");
        if (segmentNumber < 1 || segmentNumber > MAX_SEGMENT_NUMBER) {
            throw new IllegalArgumentException("세그먼트 번호는 1부터 999999 사이여야 합니다.");
        }

        String filename = String.format(
                Locale.ROOT,
                "segment-%06d.%s",
                segmentNumber,
                extension
        );
        return StorageObjectKey.of(
                StorageDirectory.CONFERENCES,
                meetingId.toString(),
                "participants",
                participantId.toString(),
                filename
        );
    }

    private static void requirePositive(Long value, String name) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(name + "는 양수여야 합니다.");
        }
    }
}
