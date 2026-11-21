package com.ssafy.backend.global.storage;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.ssafy.backend.global.exception.CustomException;
import com.ssafy.backend.global.exception.ErrorCode;

/**
 * 로컬 구현이 S3 구현과 같은 계약(ObjectStorageService)을 지키는지 실제 임시 디렉터리로 확인한다.
 * 호출하는 쪽(VAD 녹음 멱등 저장, 회의록 내보내기)이 기대는 동작만 고른다.
 */
@DisplayName("LocalObjectStorageService 단위 테스트")
class LocalObjectStorageServiceTest {

    private static final StorageObjectKey KEY = StorageObjectKey.of(
            StorageDirectory.CONFERENCES, "3", "participants", "12", "segment-000001.json");

    @TempDir
    Path uploadRoot;

    private LocalObjectStorageService storage;

    @BeforeEach
    void setUp() {
        storage = new LocalObjectStorageService(uploadRoot.toString());
    }

    @Test
    @DisplayName("업로드한 객체는 upload-dir 아래 key와 같은 경로에 저장되고 그대로 다운로드된다")
    void uploadStoresUnderKeyPathAndDownloadsSameBytes() throws Exception {
        byte[] content = "{\"seq\":1}".getBytes(UTF_8);

        StoredObject stored = storage.upload(request(KEY, content), new ByteArrayInputStream(content));

        // LocalStorageDownloadUrlProvider의 /files/{key} URL이 가리키는 파일과 같은 위치여야 한다.
        assertThat(stored.key()).isEqualTo(KEY.value());
        assertThat(Files.readAllBytes(uploadRoot.resolve(KEY.value()))).isEqualTo(content);
        assertThat(storage.exists(KEY)).isTrue();
        assertThat(storage.metadata(KEY).contentLength()).isEqualTo(content.length);

        ByteArrayOutputStream downloaded = new ByteArrayOutputStream();
        storage.download(KEY, downloaded);
        assertThat(downloaded.toByteArray()).isEqualTo(content);
    }

    @Test
    @DisplayName("uploadIfAbsent는 처음에만 저장하고, 이미 있으면 false를 돌려주며 기존 내용을 덮어쓰지 않는다")
    void uploadIfAbsentDoesNotOverwriteExistingObject() throws Exception {
        byte[] first = "first".getBytes(UTF_8);
        byte[] second = "second".getBytes(UTF_8);

        boolean created = storage.uploadIfAbsent(request(KEY, first), new ByteArrayInputStream(first));
        boolean createdAgain = storage.uploadIfAbsent(request(KEY, second), new ByteArrayInputStream(second));

        assertThat(created).isTrue();
        assertThat(createdAgain).isFalse();
        assertThat(Files.readAllBytes(uploadRoot.resolve(KEY.value()))).isEqualTo(first);
    }

    @Test
    @DisplayName("없는 객체의 다운로드·메타데이터 조회는 NOT_FOUND, 존재 확인은 false, 삭제는 예외 없이 지나간다")
    void missingObjectFollowsS3Contract() {
        assertThat(storage.exists(KEY)).isFalse();
        assertThatThrownBy(() -> storage.download(KEY, new ByteArrayOutputStream()))
                .isInstanceOf(CustomException.class)
                .extracting(ex -> ((CustomException) ex).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
        assertThatThrownBy(() -> storage.metadata(KEY))
                .isInstanceOf(CustomException.class)
                .extracting(ex -> ((CustomException) ex).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
        storage.delete(KEY);
    }

    @Test
    @DisplayName("list는 주어진 경로 아래 객체의 파일명만 돌려주고, 경로가 없으면 빈 목록이다")
    void listReturnsObjectNamesUnderPrefix() {
        StorageObjectKey secondKey = StorageObjectKey.of(
                StorageDirectory.CONFERENCES, "3", "participants", "12", "segment-000002.json");
        StorageObjectKey otherParticipant = StorageObjectKey.of(
                StorageDirectory.CONFERENCES, "3", "participants", "13", "segment-000001.json");
        byte[] content = "{}".getBytes(UTF_8);
        storage.upload(request(KEY, content), new ByteArrayInputStream(content));
        storage.upload(request(secondKey, content), new ByteArrayInputStream(content));
        storage.upload(request(otherParticipant, content), new ByteArrayInputStream(content));

        assertThat(storage.list(StorageDirectory.CONFERENCES, "3", "participants", "12"))
                .containsExactly("segment-000001.json", "segment-000002.json");
        assertThat(storage.list(StorageDirectory.CONFERENCES, "999")).isEmpty();
    }

    @Test
    @DisplayName("삭제한 객체는 더 이상 존재하지 않는다")
    void deleteRemovesObject() {
        byte[] content = "x".getBytes(UTF_8);
        storage.upload(request(KEY, content), new ByteArrayInputStream(content));

        storage.delete(KEY);

        assertThat(storage.exists(KEY)).isFalse();
    }

    private static StorageUploadRequest request(StorageObjectKey key, byte[] content) {
        return new StorageUploadRequest(key, content.length, "application/json", null);
    }
}
