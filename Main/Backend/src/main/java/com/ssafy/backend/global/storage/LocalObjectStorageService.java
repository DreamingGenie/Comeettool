package com.ssafy.backend.global.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.ssafy.backend.global.exception.CustomException;
import com.ssafy.backend.global.exception.ErrorCode;

import lombok.extern.slf4j.Slf4j;

/**
 * storage.provider가 S3가 아닐 때(로컬 개발) 쓰는 Object Storage 구현.
 * 객체를 {@code file.upload-dir} 아래에 S3 key와 같은 상대 경로로 저장한다.
 * {@link LocalStorageDownloadUrlProvider}가 만드는 {@code /files/{key}} URL과 같은 위치다.
 *
 * <p>S3 구현과 계약을 맞춘다 — 없는 객체의 조회·다운로드는 NOT_FOUND, 없는 객체의 삭제는 조용히 지나간다.
 * Content-Type은 파일 시스템에 남기지 않으므로 {@link #metadata}는 확장자로 추정한 값을 돌려준다.</p>
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "storage.provider", havingValue = "FILE", matchIfMissing = true)
public class LocalObjectStorageService implements ObjectStorageService {

    private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";

    private final Path uploadRoot;

    public LocalObjectStorageService(@Value("${file.upload-dir}") String uploadDir) {
        if (uploadDir == null || uploadDir.isBlank()) {
            throw new IllegalArgumentException("파일 업로드 경로가 필요합니다.");
        }
        this.uploadRoot = Paths.get(uploadDir.trim()).toAbsolutePath().normalize();
    }

    @Override
    public StoredObject upload(StorageUploadRequest request, InputStream content) {
        Objects.requireNonNull(request, "스토리지 업로드 요청이 필요합니다.");
        Objects.requireNonNull(content, "업로드할 스트림이 필요합니다.");

        StorageObjectKey objectKey = request.objectKey();
        Path target = safePath(objectKey);
        try {
            Files.createDirectories(target.getParent());
            long written = Files.copy(content, target, StandardCopyOption.REPLACE_EXISTING);
            return new StoredObject(objectKey.value(), request.contentType(), written);
        } catch (IOException e) {
            throw storageFailure("업로드", objectKey, e);
        }
    }

    @Override
    public boolean uploadIfAbsent(StorageUploadRequest request, InputStream content) {
        Objects.requireNonNull(request, "스토리지 업로드 요청이 필요합니다.");
        Objects.requireNonNull(content, "업로드할 스트림이 필요합니다.");

        StorageObjectKey objectKey = request.objectKey();
        Path target = safePath(objectKey);
        try {
            Files.createDirectories(target.getParent());
        } catch (IOException e) {
            throw storageFailure("조건부 업로드", objectKey, e);
        }

        // CREATE_NEW는 "없을 때만 생성"을 파일 시스템이 원자적으로 판정한다(S3의 If-None-Match에 해당).
        try (OutputStream out = Files.newOutputStream(
                target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            content.transferTo(out);
            return true;
        } catch (FileAlreadyExistsException e) {
            return false;
        } catch (IOException e) {
            deleteQuietly(target);
            throw storageFailure("조건부 업로드", objectKey, e);
        }
    }

    @Override
    public boolean exists(StorageObjectKey objectKey) {
        Objects.requireNonNull(objectKey, "스토리지 객체 Key가 필요합니다.");
        return Files.isRegularFile(safePath(objectKey));
    }

    @Override
    public StoredObject metadata(StorageObjectKey objectKey) {
        Objects.requireNonNull(objectKey, "스토리지 객체 Key가 필요합니다.");

        Path target = safePath(objectKey);
        try {
            long size = Files.size(target);
            String contentType = Files.probeContentType(target);
            return new StoredObject(
                    objectKey.value(),
                    contentType == null || contentType.isBlank() ? DEFAULT_CONTENT_TYPE : contentType,
                    size
            );
        } catch (NoSuchFileException e) {
            throw new CustomException(ErrorCode.NOT_FOUND);
        } catch (IOException e) {
            throw storageFailure("메타데이터 조회", objectKey, e);
        }
    }

    @Override
    public void download(StorageObjectKey objectKey, OutputStream target) {
        Objects.requireNonNull(objectKey, "스토리지 객체 Key가 필요합니다.");
        Objects.requireNonNull(target, "다운로드 출력 스트림이 필요합니다.");

        try {
            Files.copy(safePath(objectKey), target);
        } catch (NoSuchFileException e) {
            throw new CustomException(ErrorCode.NOT_FOUND);
        } catch (IOException e) {
            throw storageFailure("다운로드", objectKey, e);
        }
    }

    @Override
    public void delete(StorageObjectKey objectKey) {
        Objects.requireNonNull(objectKey, "스토리지 객체 Key가 필요합니다.");

        try {
            Files.deleteIfExists(safePath(objectKey));
        } catch (IOException e) {
            throw storageFailure("삭제", objectKey, e);
        }
    }

    @Override
    public List<String> list(StorageDirectory directory, String... prefixSegments) {
        Objects.requireNonNull(directory, "스토리지 디렉터리가 필요합니다.");
        Objects.requireNonNull(prefixSegments, "스토리지 경로가 필요합니다.");

        Path base = uploadRoot.resolve(directory.prefix());
        for (String segment : prefixSegments) {
            directory.validatePathSegment(segment);
            base = base.resolve(segment);
        }
        if (!Files.isDirectory(base)) {
            return List.of();
        }

        // S3의 prefix 조회처럼 하위 경로의 객체까지 모두 모아 파일명만 돌려준다.
        try (Stream<Path> paths = Files.walk(base)) {
            return paths.filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .sorted()
                    .toList();
        } catch (IOException e) {
            log.error(
                    "로컬 객체 목록 조회 실패: directory={}, cause={}",
                    directory.prefix(),
                    e.getClass().getSimpleName()
            );
            throw new CustomException(ErrorCode.INTERNAL_ERROR);
        }
    }

    // key는 검증된 세그먼트로만 만들어지지만, 저장 루트를 벗어나지 않는지 한 번 더 확인한다.
    private Path safePath(StorageObjectKey objectKey) {
        Path target = uploadRoot.resolve(objectKey.value()).normalize();
        if (!target.startsWith(uploadRoot)) {
            throw new IllegalArgumentException("안전하지 않은 로컬 파일 경로입니다.");
        }
        return target;
    }

    private void deleteQuietly(Path target) {
        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            log.warn("쓰다 만 로컬 객체 정리 실패");
        }
    }

    private CustomException storageFailure(String operation, StorageObjectKey objectKey, Exception exception) {
        log.error(
                "로컬 객체 {} 실패: directory={}, cause={}",
                operation,
                objectKey.directory().prefix(),
                exception.getClass().getSimpleName()
        );
        return new CustomException(ErrorCode.INTERNAL_ERROR);
    }
}
