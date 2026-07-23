package com.ssafy.backend.global.jwt;

import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * RSA 개인키(PKCS#8)·공개키(X.509 SPKI)를 로드한다.
 * 위치는 Spring Resource 표기를 지원한다(classpath:, file:, 접두사 없으면 classpath).
 * 파일 상대경로 대신 Resource를 써서 **실행 디렉터리에 의존하지 않는다**.
 */
public final class RsaKeyUtil {

    private static final ResourceLoader RESOURCE_LOADER = new DefaultResourceLoader();

    private RsaKeyUtil() {
    }

    public static PrivateKey loadPrivateKey(String location) {
        try {
            byte[] der = decodePem(read(location));
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (Exception e) {
            throw new IllegalStateException("JWT 개인키 로드 실패: " + location, e);
        }
    }

    public static PublicKey loadPublicKey(String location) {
        try {
            byte[] der = decodePem(read(location));
            return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
        } catch (Exception e) {
            throw new IllegalStateException("JWT 공개키 로드 실패: " + location, e);
        }
    }

    private static String read(String location) throws Exception {
        Resource resource = RESOURCE_LOADER.getResource(location);
        try (InputStream is = resource.getInputStream()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static byte[] decodePem(String pem) {
        String base64 = pem.replaceAll("-----BEGIN (.*)-----", "")
                .replaceAll("-----END (.*)-----", "")
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(base64);
    }
}
