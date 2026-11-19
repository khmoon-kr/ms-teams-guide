package com.example.teams.security;

import com.example.teams.config.TeamsSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MS Teams 카드·메시지 내 외부 브라우저 이동 링크({@code Action.OpenUrl}, HTML {@code <a href>} 등)에
 * {@code HMAC-SHA256} 서명 및 만료 시각(TTL)을 부여하고 검증하는 스레드 안전(Thread-Safe) 컴포넌트입니다.
 *
 * <h2>서명 규격</h2>
 * <p>서명 원문(Canonical Payload)은 파이프({@code |}) 구분자로 결합된 다음 6개 필드로 구성됩니다:</p>
 * <pre>{@code
 * {resourceId}|msteams|{cardType}|{action}|{iatEpochSeconds}|{expEpochSeconds}
 * }</pre>
 * <p>위 문자열을 {@link TeamsSettings#getLinkSecret()} 비밀키로 {@code HMAC-SHA256} 해싱한 뒤
 * 패딩 없는 Base64URL 문자열로 인코딩하여 {@code sig} 쿼리 파라미터로 부착합니다.</p>
 *
 * <h2>단위 테스트 지원</h2>
 * <p>{@link #TeamsLinkSigner(TeamsSettings, Clock)} 생성자에 {@link Clock#fixed(Instant, java.time.ZoneId)}를
 * 주입하면 {@code Thread.sleep()} 없이도 만료 링크({@link LinkVerificationStatus#EXPIRED}) 시나리오를
 * 결정론적으로 검증할 수 있습니다.</p>
 *
 * @see LinkVerificationResult
 * @see LinkVerificationStatus
 */
public class TeamsLinkSigner {

    /**
     * 정식 MS Teams 알림에서 생성된 링크임을 나타내는 고정 {@code src} 쿼리 파라미터 값입니다.
     */
    public static final String EXPECTED_SOURCE = "msteams";

    private static final String HMAC_ALGO = "HmacSHA256";
    private static final Pattern PATH_VAR_PATTERN = Pattern.compile("\\{[^/]+}");
    private static final Logger log = LoggerFactory.getLogger(TeamsLinkSigner.class);
    private static final AtomicBoolean DEV_SECRET_WARNED = new AtomicBoolean(false);

    private final TeamsSettings settings;
    private final Clock clock;

    /**
     * 시스템 기본 UTC 시계({@link Clock#systemUTC()})를 사용하여 서명기를 생성합니다.
     *
     * @param settings Teams 설정 객체 ({@code null} 불가)
     * @throws NullPointerException {@code settings}가 {@code null}인 경우
     */
    public TeamsLinkSigner(TeamsSettings settings) {
        this(settings, Clock.systemUTC());
    }

    /**
     * 지정된 {@link Clock}을 사용하여 서명기를 생성합니다 (단위 테스트 및 커스텀 시간 소스 주입용).
     *
     * @param settings Teams 설정 객체 ({@code null} 불가)
     * @param clock    발급 시각({@code iat}) 및 만료 판정에 사용할 시계 ({@code null} 불가)
     * @throws NullPointerException 인자 중 {@code null}이 포함된 경우
     */
    public TeamsLinkSigner(TeamsSettings settings, Clock clock) {
        this.settings = Objects.requireNonNull(settings, "settings must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        if (settings.isUsingDefaultDevSecret() && DEV_SECRET_WARNED.compareAndSet(false, true)) {
            log.warn("[TeamsLinkSigner] 기본 개발용 HMAC 시크릿이 사용 중입니다. 운영 환경에서는 반드시 teams.link-secret 값을 주입하세요.");
        }
    }

    /**
     * {@link TeamsSettings#getBaseUrl()}과 {@link TeamsSettings#getEntryPathTemplate()}을 결합하고
     * 설정된 유효시간({@link TeamsSettings#getLinkTtlMinutes()})을 적용해 서명된 진입 URL을 생성합니다.
     *
     * @param resourceId 대상 리소스·거래·문서 식별자 (경로 템플릿의 {@code {id}} 치환 및 서명 원문에 사용, {@code null} 불가)
     * @param cardType   알림·카드 유형 식별자 (예: {@code "ORDER_ALERT"}, {@code "RECEIPT_CARD"})
     * @param action     진입 후 수행할 의도 액션 (예: {@code "view"}, {@code "approve"})
     * @return {@code src}, {@code cardType}, {@code action}, {@code iat}, {@code exp}, {@code sig}가 부착된 전체 URL
     */
    public String createSignedEntryUrl(String resourceId, String cardType, String action) {
        Instant now = Instant.now(clock);
        Instant exp = now.plusSeconds(settings.getLinkTtlMinutes() * 60);
        return createSignedEntryUrl(resourceId, cardType, action, now.getEpochSecond(), exp.getEpochSecond());
    }

    /**
     * 발급 시각과 만료 시각(Epoch 초)을 직접 지정하여 기본 진입 경로 기반의 서명된 URL을 생성합니다.
     *
     * @param resourceId  대상 리소스 식별자 ({@code null} 불가)
     * @param cardType    알림·카드 유형 식별자
     * @param action      의도 액션 식별자
     * @param iatEpochSec 발급 시각 (UTC Epoch Seconds)
     * @param expEpochSec 만료 시각 (UTC Epoch Seconds)
     * @return 서명 파라미터가 부착된 전체 URL
     */
    public String createSignedEntryUrl(
            String resourceId,
            String cardType,
            String action,
            long iatEpochSec,
            long expEpochSec
    ) {
        Objects.requireNonNull(resourceId, "resourceId must not be null");
        String base = settings.getBaseUrl().replaceAll("/+$", "");
        String pathTemplate = settings.getEntryPathTemplate().startsWith("/")
                ? settings.getEntryPathTemplate()
                : "/" + settings.getEntryPathTemplate();
        String encodedId = urlEncode(resourceId);
        Matcher matcher = PATH_VAR_PATTERN.matcher(pathTemplate);
        String resolvedPath = matcher.find()
                ? matcher.replaceAll(Matcher.quoteReplacement(encodedId))
                : pathTemplate + "/" + encodedId;
        return signCustomUrl(base + resolvedPath, resourceId, cardType, action, iatEpochSec, expEpochSec);
    }

    /**
     * 기본 경로 템플릿과 무관한 임의의 대상 URL에 Teams 유입 검증용 서명 쿼리 파라미터를 덧붙입니다.
     * 이미 쿼리 스트링({@code ?key=val})이 포함된 URL이면 자동으로 {@code &}로 연결합니다.
     *
     * @param targetUrl  서명을 부착할 대상 웹 URL (예: {@code "https://admin.example.com/approvals/77?tab=summary"})
     * @param resourceId 검증 시 대조할 대상 리소스 식별자 ({@code null} 불가)
     * @param cardType   알림·카드 유형 식별자
     * @param action     의도 액션 식별자
     * @return 서명 파라미터가 추가된 전체 URL
     */
    public String signCustomUrl(String targetUrl, String resourceId, String cardType, String action) {
        Instant now = Instant.now(clock);
        Instant exp = now.plusSeconds(settings.getLinkTtlMinutes() * 60);
        return signCustomUrl(targetUrl, resourceId, cardType, action, now.getEpochSecond(), exp.getEpochSecond());
    }

    /**
     * 발급·만료 시각을 직접 지정하여 임의의 대상 URL에 서명 파라미터를 덧붙입니다.
     *
     * @param targetUrl   서명을 부착할 대상 웹 URL ({@code null} 불가)
     * @param resourceId  대상 리소스 식별자 ({@code null} 불가)
     * @param cardType    알림·카드 유형 식별자
     * @param action      의도 액션 식별자
     * @param iatEpochSec 발급 시각 (UTC Epoch Seconds)
     * @param expEpochSec 만료 시각 (UTC Epoch Seconds)
     * @return 서명 파라미터가 추가된 전체 URL
     */
    public String signCustomUrl(
            String targetUrl,
            String resourceId,
            String cardType,
            String action,
            long iatEpochSec,
            long expEpochSec
    ) {
        Objects.requireNonNull(targetUrl, "targetUrl must not be null");
        Objects.requireNonNull(resourceId, "resourceId must not be null");
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
     * 쿼리 파라미터 {@link Map}(Servlet Filter, API Gateway, SPA 검증 API 등)으로부터 서명 유효성을 검증합니다.
     *
     * @param resourceId  현재 접근 중인 경로/요청의 리소스 식별자
     * @param queryParams URL 쿼리 파라미터 맵 ({@code null} 또는 빈 맵이면 {@link LinkVerificationStatus#DIRECT_UNVERIFIED} 반환)
     * @return 4단계 상태와 상세 메타데이터가 담긴 {@link LinkVerificationResult}
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

    /**
     * 개별 쿼리 파라미터 값을 받아 상수 시간 {@code HMAC-SHA256} 대조 및 만료 시각({@code exp}) 검증을 수행합니다.
     *
     * @param resourceId 현재 요청 경로의 리소스 식별자 (서명 발급 당시의 식별자와 일치해야 함)
     * @param src        유입 출처 파라미터 ({@code "msteams"} 기대, 미전달 시 {@code null})
     * @param cardType   알림·카드 유형 파라미터
     * @param action     액션 파라미터
     * @param iat        발급 시각 Epoch 초
     * @param exp        만료 시각 Epoch 초
     * @param sig        Base64URL 인코딩된 {@code HMAC-SHA256} 서명 문자열
     * @return 검증 상태와 사유 메시지를 포함한 {@link LinkVerificationResult}
     */
    public LinkVerificationResult verify(
            String resourceId,
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

        if (isBlank(resourceId) || isBlank(src) || isBlank(cardType) || isBlank(action)
                || iat == null || exp == null || isBlank(sig)) {
            return new LinkVerificationResult(
                    LinkVerificationStatus.INVALID_SIGNATURE,
                    "Teams 검증에 필요한 필수 파라미터(resourceId, src, cardType, action, iat, exp, sig) 중 일부가 누락되었습니다.",
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

        String canonical = buildCanonicalPayload(resourceId, src, cardType, action, iat, exp);
        String expectedSig = computeHmacBase64Url(canonical);

        boolean matches = MessageDigest.isEqual(
                expectedSig.getBytes(StandardCharsets.UTF_8),
                sig.getBytes(StandardCharsets.UTF_8)
        );

        if (!matches) {
            return new LinkVerificationResult(
                    LinkVerificationStatus.INVALID_SIGNATURE,
                    "HMAC-SHA256 서명이 일치하지 않습니다. 주소창의 식별자·만료시간·액션 또는 서명(sig)이 임의로 수정되었습니다.",
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
                    "서명은 정상이지만 링크 유효시간이 만료되었습니다. Teams 채널에서 최신 알림을 확인하거나 새 링크를 요청해 주세요.",
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
                "MS Teams 알림에 발급된 정식 서명 링크를 통해 유입되었음이 확인되었습니다.",
                src,
                cardType,
                action,
                Instant.ofEpochSecond(iat),
                Instant.ofEpochSecond(exp),
                canonical
        );
    }

    String buildCanonicalPayload(String resourceId, String src, String cardType, String action, long iat, long exp) {
        return String.join("|", resourceId, src, cardType, action, String.valueOf(iat), String.valueOf(exp));
    }

    String computeHmacBase64Url(String data) {
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
