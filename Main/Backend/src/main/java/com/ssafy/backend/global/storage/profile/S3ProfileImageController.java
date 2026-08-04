package com.ssafy.backend.global.storage.profile;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/files/profile-images")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "profile-img", havingValue = "S3")
public class S3ProfileImageController {

    private final S3ProfileImageStorageService storageService;

    @GetMapping("/{ownerType:users|teams}/{ownerId:[1-9]\\d*}/{filename:.+}")
    public ResponseEntity<byte[]> getScopedProfileImage(
            @PathVariable String ownerType,
            @PathVariable Long ownerId,
            @PathVariable String filename
    ) {
        ProfileImageOwner owner = new ProfileImageOwner(
                ProfileImageOwnerType.fromPathSegment(ownerType),
                ownerId
        );
        return response(storageService.download(owner, filename));
    }

    @GetMapping("/{filename:.+}")
    public ResponseEntity<byte[]> getLegacyProfileImage(@PathVariable String filename) {
        return response(storageService.downloadLegacy(filename));
    }

    private static ResponseEntity<byte[]> response(ProfileImageContent image) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.contentType()))
                .contentLength(image.content().length)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePublic().immutable())
                .header("X-Content-Type-Options", "nosniff")
                .body(image.content());
    }
}
