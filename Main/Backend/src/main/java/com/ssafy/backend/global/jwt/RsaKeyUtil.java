package com.ssafy.backend.global.jwt;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * PEM 파일에서 RSA 개인키(PKCS#8)·공개키(X.509 SPKI)를 로드한다.
 */
public final class RsaKeyUtil {

    private RsaKeyUtil() {
    }

    public static PrivateKey loadPrivateKey(String path) {
        try {
            byte[] der = decodePem(Files.readString(Path.of(path)));
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (Exception e) {
            throw new IllegalStateException("JWT 개인키 로드 실패: " + path, e);
        }
    }

    public static PublicKey loadPublicKey(String path) {
        try {
            byte[] der = decodePem(Files.readString(Path.of(path)));
            return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
        } catch (Exception e) {
            throw new IllegalStateException("JWT 공개키 로드 실패: " + path, e);
        }
    }

    private static byte[] decodePem(String pem) {
        String base64 = pem.replaceAll("-----BEGIN (.*)-----", "")
                .replaceAll("-----END (.*)-----", "")
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(base64);
    }
}
