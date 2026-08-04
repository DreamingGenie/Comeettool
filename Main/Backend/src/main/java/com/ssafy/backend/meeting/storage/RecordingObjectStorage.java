package com.ssafy.backend.meeting.storage;

import java.util.List;
import java.util.Optional;

public interface RecordingObjectStorage {

    boolean exists(String objectKey);

    Optional<byte[]> getBytes(String objectKey);

    /** @return true if created, false if already exists (If-None-Match) */
    boolean putIfAbsent(String objectKey, byte[] bytes, String contentType);

    void put(String objectKey, byte[] bytes, String contentType);

    List<String> listKeys(String prefix);
}
