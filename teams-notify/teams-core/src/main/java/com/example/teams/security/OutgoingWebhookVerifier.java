package com.example.teams.security;

import com.example.teams.config.TeamsSettings;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * MS Teams 표준 채널의 <b>Outgoing Webhook</b>이 백엔드로 요청을 보낼 때 동봉하는
 * {@code Authorization: HMAC <base64Signature>} 헤더의 진위를 검증하는 스레드 안전 컴포넌트입니다.
 *
 * <h2>동작 방식</h2>
 * <p>Teams 채널에서 Outgoing Webhook을 등록하면 Base64로 인코딩된 Security Token이 발급됩니다.
 * 이 검증기는 해당 Base64 문자열을 먼저 바이트 배열로 디코딩하여 {@code HMAC-SHA256} 비밀키로 초기화한 뒤,
 * HTTP 요청 본문 원본 바이트({@code rawRequestBody})에 대한 서명을 계산해 상수 시간 비교({@link MessageDigest#isEqual})로 대조합니다.</p>
 *
 * <p>만약 설정된 토큰이 비어 있으면({@link #isVerificationEnabled()}가 {@code false}), 로컬 개발 편의를 위해
 * 검증을 건너뛰고 {@code true}를 반환합니다.</p>
 *
 * @see TeamsSettings#getOutgoingWebhookSecret()
 */
public class OutgoingWebhookVerifier {

    private static final String HMAC_ALGO = "HmacSHA256";
    private final String base64SecurityToken;

    /**
     * {@link TeamsSettings}에 설정된 {@code outgoingWebhookSecret}으로 검증기를 생성합니다.
     *
     * @param settings Teams 클라이언트 설정 ({@code null}일 경우 검증 비활성화 상태로 생성)
     */
    public OutgoingWebhookVerifier(TeamsSettings settings) {
        this(settings != null ? settings.getOutgoingWebhookSecret() : "");
    }

    /**
     * Teams Outgoing Webhook 등록 시 발급받은 Base64 Security Token 문자열로 검증기를 생성합니다.
     *
     * @param base64SecurityToken Base64 인코딩된 Outgoing Webhook 보안 토큰 (빈 문자열 또는 {@code null}이면 검증 비활성화)
     */
    public OutgoingWebhookVerifier(String base64SecurityToken) {
        this.base64SecurityToken = base64SecurityToken != null ? base64SecurityToken.trim() : "";
    }

    /**
     * Outgoing Webhook 보안 토큰이 설정되어 서명 검증이 활성화된 상태인지 반환합니다.
     *
     * @return 보안 토큰이 설정되어 있으면 {@code true}, 미설정(개발 모드)이면 {@code false}
     */
    public boolean isVerificationEnabled() {
        return !base64SecurityToken.isBlank();
    }

    /**
     * HTTP {@code Authorization} 헤더와 요청 본문 원본 바이트를 대조하여 MS Teams가 보낸 정식 요청인지 검증합니다.
     *
     * @param authorizationHeader HTTP {@code Authorization} 헤더 값 (예: {@code "HMAC dGhpcyBpcyBhIHRlc3Q="})
     * @param rawRequestBody      파싱 전 HTTP 요청 본문 원본 바이트 배열 ({@code null} 불가)
     * @return 서명이 일치하거나 검증이 비활성화된 경우 {@code true}, 헤더 누락·형식 오류·서명 불일치 시 {@code false}
     */
    public boolean verifyAuthorizationHeader(String authorizationHeader, byte[] rawRequestBody) {
        if (!isVerificationEnabled()) {
            return true;
        }
        if (rawRequestBody == null || authorizationHeader == null || !authorizationHeader.startsWith("HMAC ")) {
            return false;
        }
        String providedBase64 = authorizationHeader.substring("HMAC ".length()).trim();
        try {
            byte[] keyBytes = Base64.getDecoder().decode(base64SecurityToken);
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(keyBytes, HMAC_ALGO));
            byte[] computed = mac.doFinal(rawRequestBody);
            String expectedBase64 = Base64.getEncoder().encodeToString(computed);
            return MessageDigest.isEqual(
                    expectedBase64.getBytes(StandardCharsets.UTF_8),
                    providedBase64.getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception e) {
            return false;
        }
    }
}
