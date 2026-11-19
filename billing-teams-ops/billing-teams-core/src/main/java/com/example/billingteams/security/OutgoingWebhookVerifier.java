package com.example.billingteams.security;

import com.example.billingteams.config.TeamsClientSettings;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * MS Teams 표준 채널 Outgoing Webhook이 전송하는 Authorization("HMAC <base64>") 헤더를 검증하는 순수 Java 컴포넌트.
 */
public class OutgoingWebhookVerifier {

    private static final String HMAC_ALGO = "HmacSHA256";
    private final String base64SecurityToken;

    public OutgoingWebhookVerifier(TeamsClientSettings settings) {
        this(settings != null ? settings.getOutgoingWebhookSecret() : "");
    }

    public OutgoingWebhookVerifier(String base64SecurityToken) {
        this.base64SecurityToken = base64SecurityToken != null ? base64SecurityToken.trim() : "";
    }

    public boolean isVerificationEnabled() {
        return !base64SecurityToken.isBlank();
    }

    public boolean verifyAuthorizationHeader(String authorizationHeader, byte[] rawRequestBody) {
        if (!isVerificationEnabled()) {
            return true;
        }
        if (authorizationHeader == null || !authorizationHeader.startsWith("HMAC ")) {
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
