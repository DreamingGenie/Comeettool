package com.ssafy.backend.global.jwt;

import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * RSA 개인키(PKCS#8)·공개키(X.509 SPKI)를 로드한다. 위치는 Spring Resource 표기를 지원한다(classpath:, file:, 접두사 없으면 classpath). 파일 상대경로 대신
 * Resource를 써서 **실행 디렉터리에 의존하지 않는다**.
 */
public final class RsaKeyUtil {

    private static final ResourceLoader RESOURCE_LOADER = new DefaultResourceLoader();
    private static final byte[] KEY_PAIR_VALIDATION_MESSAGE =
            "a707-jwt-key-pair-validation".getBytes(StandardCharsets.UTF_8);

    private RsaKeyUtil() {
    }

    public static LoadedRsaKeyPair loadKeyPair(
            String privateKeyBase64,
            String publicKeyBase64,
            String privateKeyPath,
            String publicKeyPath
    ) {
        boolean hasPrivateKeyBase64 = hasText(privateKeyBase64);
        boolean hasPublicKeyBase64 = hasText(publicKeyBase64);

        if (hasPrivateKeyBase64 != hasPublicKeyBase64) {
            throw new IllegalStateException(
                    "JWT_PRIVATE_KEY_BASE64와 JWT_PUBLIC_KEY_BASE64는 함께 설정해야 합니다."
            );
        }

        PrivateKey privateKey = hasPrivateKeyBase64
                ? loadPrivateKeyBase64(privateKeyBase64)
                : loadPrivateKey(privateKeyPath);
        PublicKey loadedPublicKey = hasPublicKeyBase64
                ? loadPublicKeyBase64(publicKeyBase64)
                : loadPublicKey(publicKeyPath);

        if (!(loadedPublicKey instanceof RSAPublicKey publicKey)) {
            throw new IllegalStateException("JWT 공개키가 RSA 공개키가 아닙니다.");
        }

        validateKeyPair(privateKey, publicKey);
        return new LoadedRsaKeyPair(privateKey, publicKey);
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

    private static PrivateKey loadPrivateKeyBase64(String encodedPem) {
        try {
            byte[] der = decodePem(decodeBase64Pem(encodedPem));
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (Exception e) {
            throw new IllegalStateException("JWT Base64 개인키 로드 실패", e);
        }
    }

    private static PublicKey loadPublicKeyBase64(String encodedPem) {
        try {
            byte[] der = decodePem(decodeBase64Pem(encodedPem));
            return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
        } catch (Exception e) {
            throw new IllegalStateException("JWT Base64 공개키 로드 실패", e);
        }
    }

    private static String decodeBase64Pem(String encodedPem) {
        String normalized = encodedPem.replaceAll("\\s", "");
        byte[] pem = Base64.getDecoder().decode(normalized);
        return new String(pem, StandardCharsets.UTF_8);
    }

    private static void validateKeyPair(PrivateKey privateKey, RSAPublicKey publicKey) {
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(privateKey);
            signature.update(KEY_PAIR_VALIDATION_MESSAGE);
            byte[] signed = signature.sign();

            signature.initVerify(publicKey);
            signature.update(KEY_PAIR_VALIDATION_MESSAGE);

            if (!signature.verify(signed)) {
                throw new IllegalStateException("JWT 개인키와 공개키가 일치하지 않습니다.");
            }
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("JWT RSA 키 쌍 검증 실패", e);
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
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

    public record LoadedRsaKeyPair(PrivateKey privateKey, RSAPublicKey publicKey) {
    }
}
