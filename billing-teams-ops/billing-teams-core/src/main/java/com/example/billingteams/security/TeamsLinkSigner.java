package com.example.billingteams.security;

import com.example.billingteams.config.TeamsClientSettings;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;

/**
 * MS Teams 카드/메시지 내 외부 브라우저 이동 링크(Action.OpenUrl, HTML anchor 등)에
 * HMAC-SHA256 서명 및 만료 시각(TTL)을 부여하고 검증하는 순수 Java 컴포넌트입니다.
 */
public class TeamsLinkSigner {

    public static final String EXPECTED_SOURCE = "msteams";
    private static final String HMAC_ALGO = "HmacSHA256";

    private final TeamsClientSettings settings;
    private final Clock clock;

    public TeamsLinkSigner(TeamsClientSettings settings) {
        this(settings, Clock.systemUTC());
    }

    public TeamsLinkSigner(TeamsClientSettings settings, Clock clock) {
        this.settings = Objects.requireNonNull(settings, "settings must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * 설정된 baseUrl + entryPathTemplate(기본값: /ops/transactions/{txId})을 기준으로 서명된 URL을 생성합니다.
     */
    public String createSignedTransactionUrl(String txId, String cardType, String action) {
        Instant now = Instant.now(clock);
        Instant exp = now.plusSeconds(settings.getLinkTtlMinutes() * 60);
        return createSignedTransactionUrl(txId, cardType, action, now.getEpochSecond(), exp.getEpochSecond());
    }

    public String createSignedTransactionUrl(String txId, String cardType, String action, long iatEpochSec, long expEpochSec) {
        String base = settings.getBaseUrl().replaceAll("/+$", "");
        String pathTemplate = settings.getEntryPathTemplate().startsWith("/")
                ? settings.getEntryPathTemplate()
                : "/" + settings.getEntryPathTemplate();
        String resolvedPath = pathTemplate.replace("{txId}", urlEncode(txId));
        return signCustomUrl(base + resolvedPath, txId, cardType, action, iatEpochSec, expEpochSec);
    }

    /**
     * 외부 프로젝트에서 임의의 자체 웹 URL(예: https://admin.company.com/settlements/9912?tab=refund)에
     * Teams 유입 검증용 서명 파라미터를 덧붙일 때 사용합니다.
     */
    public String signCustomUrl(String targetUrl, String resourceId, String cardType, String action) {
        Instant now = Instant.now(clock);
        Instant exp = now.plusSeconds(settings.getLinkTtlMinutes() * 60);
        return signCustomUrl(targetUrl, resourceId, cardType, action, now.getEpochSecond(), exp.getEpochSecond());
    }

    public String signCustomUrl(
            String targetUrl,
            String resourceId,
            String cardType,
            String action,
            long iatEpochSec,
            long expEpochSec
    ) {
        String canonical = buildCanonicalPayload(resourceId, EXPECTED_SOURCE, cardType, action, iatEpochSec, expEpochSec);
        String sig = computeHmacBase64Url(canonical);
        char separator = targetUrl.contains("?") ? '&' : '?';

        return targetUrl + separator
                + "src=" + urlEncode(EXPECTED_SOURCE)
                + "&cardType=" + urlEncode(cardType)
                + "&action=" + urlEncode(action)
                + "&iat=" + iatEpochSec
                + "&exp=" + expEpochSec
                + "&sig=" + urlEncode(sig);
    }

    /**
     * 쿼리 파라미터 Map(예: Servlet, Filter, API Gateway, SPA 요청 등)으로부터 직접 서명을 검증합니다.
     */
    public LinkVerificationResult verifyQueryMap(String resourceId, Map<String, String> queryParams) {
        if (queryParams == null || queryParams.isEmpty()) {
            return verify(resourceId, null, null, null, null, null, null);
        }
        Long iat = parseLongSafely(queryParams.get("iat"));
        Long exp = parseLongSafely(queryParams.get("exp"));
        if ((queryParams.containsKey("iat") && iat == null) || (queryParams.containsKey("exp") && exp == null)) {
            return new LinkVerificationResult(
                    LinkVerificationStatus.INVALID_SIGNATURE,
                    "타임스탬프 파라미터(iat, exp) 형식이 올바르지 않습니다.",
                    queryParams.get("src"),
                    queryParams.get("cardType"),
                    queryParams.get("action"),
                    null,
                    null,
                    null
            );
        }
        return verify(
                resourceId,
                queryParams.get("src"),
                queryParams.get("cardType"),
                queryParams.get("action"),
                iat,
                exp,
                queryParams.get("sig")
        );
    }

    public LinkVerificationResult verify(
            String txId,
            String src,
            String cardType,
            String action,
            Long iat,
            Long exp,
            String sig
    ) {
        boolean noParamsProvided = isBlank(src) && isBlank(sig) && iat == null && exp == null;
        if (noParamsProvided) {
            return new LinkVerificationResult(
                    LinkVerificationStatus.DIRECT_UNVERIFIED,
                    "Teams 서명 토큰 없이 브라우저 주소창 또는 외부 북마크로 직접 접속했습니다. 조회만 가능하며 Teams 인증 전용 원클릭 실행은 비활성화됩니다.",
                    "direct",
                    "-",
                    action != null ? action : "view",
                    null,
                    null,
                    null
            );
        }

        if (isBlank(src) || isBlank(cardType) || isBlank(action) || iat == null || exp == null || isBlank(sig)) {
            return new LinkVerificationResult(
                    LinkVerificationStatus.INVALID_SIGNATURE,
                    "Teams 검증에 필요한 필수 쿼리 파라미터(src, cardType, action, iat, exp, sig) 중 일부가 누락되었습니다.",
                    src,
                    cardType,
                    action,
                    iat != null ? Instant.ofEpochSecond(iat) : null,
                    exp != null ? Instant.ofEpochSecond(exp) : null,
                    null
            );
        }

        if (!EXPECTED_SOURCE.equals(src)) {
            return new LinkVerificationResult(
                    LinkVerificationStatus.INVALID_SIGNATURE,
                    "유입 출처(src)가 'msteams'가 아닙니다: " + src,
                    src,
                    cardType,
                    action,
                    Instant.ofEpochSecond(iat),
                    Instant.ofEpochSecond(exp),
                    null
            );
        }

        String canonical = buildCanonicalPayload(txId, src, cardType, action, iat, exp);
        String expectedSig = computeHmacBase64Url(canonical);

        boolean matches = MessageDigest.isEqual(
                expectedSig.getBytes(StandardCharsets.UTF_8),
                sig.getBytes(StandardCharsets.UTF_8)
        );

        if (!matches) {
            return new LinkVerificationResult(
                    LinkVerificationStatus.INVALID_SIGNATURE,
                    "HMAC-SHA256 서명이 일치하지 않습니다. 주소창의 거래 ID·만료시간·액션 또는 서명(sig)이 임의로 수정되었습니다.",
                    src,
                    cardType,
                    action,
                    Instant.ofEpochSecond(iat),
                    Instant.ofEpochSecond(exp),
                    canonical
            );
        }

        long nowEpoch = Instant.now(clock).getEpochSecond();
        if (nowEpoch > exp) {
            return new LinkVerificationResult(
                    LinkVerificationStatus.EXPIRED,
                    "서명은 정상이지만 링크 유효시간이 만료되었습니다. Teams 채널에서 최신 알림 카드를 확인하거나 새 링크를 요청해 주세요.",
                    src,
                    cardType,
                    action,
                    Instant.ofEpochSecond(iat),
                    Instant.ofEpochSecond(exp),
                    canonical
            );
        }

        return new LinkVerificationResult(
                LinkVerificationStatus.VERIFIED_TEAMS,
                "우리 백엔드가 MS Teams 알림에 발급한 정식 서명 링크를 통해 유입되었음이 확인되었습니다.",
                src,
                cardType,
                action,
                Instant.ofEpochSecond(iat),
                Instant.ofEpochSecond(exp),
                canonical
        );
    }

    public String buildCanonicalPayload(String txId, String src, String cardType, String action, long iat, long exp) {
        return String.join("|", txId, src, cardType, action, String.valueOf(iat), String.valueOf(exp));
    }

    public String computeHmacBase64Url(String data) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            SecretKeySpec secretKey = new SecretKeySpec(
                    settings.getLinkSecret().getBytes(StandardCharsets.UTF_8),
                    HMAC_ALGO
            );
            mac.init(secretKey);
            byte[] raw = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute HMAC-SHA256 signature", e);
        }
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value != null ? value : "", StandardCharsets.UTF_8);
    }

    private static Long parseLongSafely(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
