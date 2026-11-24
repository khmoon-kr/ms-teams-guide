package com.example.teams.config;

import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.util.Objects;

/**
 * 프레임워크에 종속되지 않는 MS Teams 알림·서명 링크 불변(Immutable) 런타임 설정 객체입니다.
 *
 * <p>Spring Boot 환경에서는 {@code teams-spring-boot-starter}의 {@code TeamsProperties}({@code teams.*})로부터
 * 자동 생성되어 빈으로 등록되며, 순수 Java 17 애플리케이션이나 배치·타 프레임워크에서는
 * {@link #builder()}를 호출해 직접 생성합니다.</p>
 *
 * <h2>사용 예시</h2>
 * <pre>{@code
 * TeamsSettings settings = TeamsSettings.builder()
 *     .webhookUrl(System.getenv("TEAMS_WEBHOOK_URL"))
 *     .baseUrl("https://admin.our-service.com")
 *     .entryPathTemplate("/admin/orders/{id}")
 *     .linkSecret(System.getenv("TEAMS_LINK_SECRET"))
 *     .linkTtlMinutes(30)
 *     .maxOutboxSize(0) // 운영 환경에서 메모리 보관 비활성화
 *     .build();
 * }</pre>
 *
 * @see com.example.teams.TeamsNotifier#create(TeamsSettings)
 */
public final class TeamsSettings {

    /**
     * {@code baseUrl} 미지정 시 사용되는 로컬 개발 기본 주소입니다 ({@value}).
     */
    public static final String DEFAULT_BASE_URL = "http://localhost:8080";

    /**
     * {@code entryPathTemplate} 미지정 시 사용되는 기본 서명 진입 경로 템플릿입니다 ({@value}).
     */
    public static final String DEFAULT_ENTRY_PATH_TEMPLATE = "/ops/items/{id}";

    /**
     * 로컬 개발 및 테스트 편의를 위한 기본 HMAC 비밀키입니다.
     * 운영 환경에서는 반드시 별도의 시크릿을 주입해야 하며, 이 기본값이 활성화되면 기동 시 경고 로그가 출력됩니다.
     */
    public static final String DEFAULT_DEV_LINK_SECRET = "dev-teams-notify-hmac-secret-key-change-in-prod";

    /**
     * {@code linkSecret}에 요구되는 최소 UTF-8 바이트 길이입니다 ({@value}바이트).
     */
    public static final int MIN_SECRET_BYTES = 16;

    private final String webhookUrl;
    private final String messageWebhookUrl;
    private final String baseUrl;
    private final String entryPathTemplate;
    private final String linkSecret;
    private final long linkTtlMinutes;
    private final String outgoingWebhookSecret;
    private final int connectTimeoutSeconds;
    private final int requestTimeoutSeconds;
    private final int maxOutboxSize;
    private final ZoneId displayZone;

    private TeamsSettings(Builder builder) {
        this.webhookUrl = builder.webhookUrl != null ? builder.webhookUrl.trim() : "";
        this.messageWebhookUrl = builder.messageWebhookUrl != null ? builder.messageWebhookUrl.trim() : "";
        this.baseUrl = (builder.baseUrl != null && !builder.baseUrl.isBlank())
                ? builder.baseUrl.trim()
                : DEFAULT_BASE_URL;
        this.entryPathTemplate = (builder.entryPathTemplate != null && !builder.entryPathTemplate.isBlank())
                ? builder.entryPathTemplate.trim()
                : DEFAULT_ENTRY_PATH_TEMPLATE;

        String rawSecret = Objects.requireNonNull(builder.linkSecret, "linkSecret must not be null");
        if (rawSecret.isBlank()) {
            throw new IllegalArgumentException("linkSecret must not be blank");
        }
        if (rawSecret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalArgumentException(
                    "linkSecret must be at least " + MIN_SECRET_BYTES + " bytes for HMAC-SHA256 security");
        }
        this.linkSecret = rawSecret;

        this.linkTtlMinutes = builder.linkTtlMinutes > 0 ? builder.linkTtlMinutes : 60L;
        this.outgoingWebhookSecret = builder.outgoingWebhookSecret != null ? builder.outgoingWebhookSecret.trim() : "";
        this.connectTimeoutSeconds = builder.connectTimeoutSeconds > 0 ? builder.connectTimeoutSeconds : 5;
        this.requestTimeoutSeconds = builder.requestTimeoutSeconds > 0 ? builder.requestTimeoutSeconds : 10;
        this.maxOutboxSize = Math.max(0, builder.maxOutboxSize);
        this.displayZone = builder.displayZone != null ? builder.displayZone : ZoneId.of("Asia/Seoul");
    }

    /**
     * 새 {@link TeamsSettings} 인스턴스를 조립하기 위한 빌더를 반환합니다.
     *
     * @return 새 {@link Builder} 인스턴스
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 기본 전송 대상 MS Teams Workflows Incoming Webhook URL(Adaptive Card용)을 반환합니다.
     * 빈 문자열일 경우 실제 HTTP 요청을 생략하는 Dry-Run 모드로 동작합니다.
     *
     * @return 웹훅 URL 문자열 (미설정 시 빈 문자열 {@code ""})
     */
    public String getWebhookUrl() {
        return webhookUrl;
    }

    /**
     * 일반 텍스트 및 HTML 메시지 전용 MS Teams Workflows Webhook URL을 반환합니다.
     * 빈 문자열일 경우 실제 HTTP 요청을 생략하는 Dry-Run 모드로 동작합니다.
     *
     * @return 일반/HTML 메시지용 웹훅 URL 문자열 (미설정 시 빈 문자열 {@code ""})
     */
    public String getMessageWebhookUrl() {
        return messageWebhookUrl;
    }

    /**
     * 서명 링크 생성 시 기준이 되는 웹 애플리케이션의 기본 도메인 주소를 반환합니다.
     *
     * @return 기본 URL (예: {@code "https://admin.example.com"})
     */
    public String getBaseUrl() {
        return baseUrl;
    }

    /**
     * 기본 서명 링크 생성 시 {@code baseUrl} 뒤에 결합되는 경로 템플릿을 반환합니다.
     * 경로 내 {@code {id}} 등의 플레이스홀더는 리소스 식별자로 자동 치환됩니다.
     *
     * @return 진입 경로 템플릿 (예: {@code "/ops/items/{id}"})
     */
    public String getEntryPathTemplate() {
        return entryPathTemplate;
    }

    /**
     * 링크 서명 및 검증에 사용되는 {@code HMAC-SHA256} 비밀키를 반환합니다.
     *
     * @return 최소 {@value #MIN_SECRET_BYTES}바이트 이상의 비밀키 문자열
     */
    public String getLinkSecret() {
        return linkSecret;
    }

    /**
     * 현재 설정이 개발용 기본 시크릿({@link #DEFAULT_DEV_LINK_SECRET})을 그대로 사용하고 있는지 여부를 반환합니다.
     *
     * @return 기본 개발 시크릿 사용 중이면 {@code true}
     */
    public boolean isUsingDefaultDevSecret() {
        return DEFAULT_DEV_LINK_SECRET.equals(linkSecret);
    }

    /**
     * 발급된 서명 링크의 기본 유효시간(분 단위)을 반환합니다.
     *
     * @return 유효시간(분, 기본값 60)
     */
    public long getLinkTtlMinutes() {
        return linkTtlMinutes;
    }

    /**
     * MS Teams 표준 채널 Outgoing Webhook 검증용 Base64 보안 토큰을 반환합니다.
     *
     * @return Base64 인코딩된 토큰 문자열 (미설정 시 빈 문자열 {@code ""})
     */
    public String getOutgoingWebhookSecret() {
        return outgoingWebhookSecret;
    }

    /**
     * Teams 웹훅 호출 시 TCP 연결 수립 타임아웃(초 단위)을 반환합니다.
     *
     * @return 연결 타임아웃(초, 기본값 5)
     */
    public int getConnectTimeoutSeconds() {
        return connectTimeoutSeconds;
    }

    /**
     * Teams 웹훅 호출 시 HTTP 응답 대기 타임아웃(초 단위)을 반환합니다.
     *
     * @return 요청 타임아웃(초, 기본값 10)
     */
    public int getRequestTimeoutSeconds() {
        return requestTimeoutSeconds;
    }

    /**
     * 디버깅 및 모니터링을 위해 메모리에 보관할 최근 발송 이력(Outbox) 최대 개수를 반환합니다.
     * {@code 0}일 경우 메모리에 발송 이력을 적재하지 않습니다.
     *
     * @return 최대 보관 개수 (0 이상, 기본값 50)
     */
    public int getMaxOutboxSize() {
        return maxOutboxSize;
    }

    /**
     * 카드·알림 본문에 시각({@link java.time.Instant})을 문자열로 포맷팅할 때 사용할 표준 시간대를 반환합니다.
     *
     * @return 표시용 {@link ZoneId} (기본값 {@code Asia/Seoul})
     */
    public ZoneId getDisplayZone() {
        return displayZone;
    }

    /**
     * {@link TeamsSettings} 인스턴스를 생성하는 가변 빌더 클래스입니다.
     */
    public static final class Builder {
        private String webhookUrl = "";
        private String messageWebhookUrl = "";
        private String baseUrl = DEFAULT_BASE_URL;
        private String entryPathTemplate = DEFAULT_ENTRY_PATH_TEMPLATE;
        private String linkSecret = DEFAULT_DEV_LINK_SECRET;
        private long linkTtlMinutes = 60L;
        private String outgoingWebhookSecret = "";
        private int connectTimeoutSeconds = 5;
        private int requestTimeoutSeconds = 10;
        private int maxOutboxSize = 50;
        private ZoneId displayZone = ZoneId.of("Asia/Seoul");

        private Builder() {}

        /**
         * 기본 전송 대상 MS Teams Workflows Incoming Webhook URL(Adaptive Card용)을 설정합니다.
         * {@code null} 또는 빈 문자열이면 Dry-Run 모드로 동작합니다.
         *
         * @param webhookUrl Workflows 웹훅 URL
         * @return 현재 빌더
         */
        public Builder webhookUrl(String webhookUrl) {
            this.webhookUrl = webhookUrl;
            return this;
        }

        /**
         * 일반 텍스트 및 HTML 메시지 전용 MS Teams Workflows Webhook URL을 설정합니다.
         * {@code null} 또는 빈 문자열이면 Dry-Run 모드로 동작합니다.
         *
         * @param messageWebhookUrl 일반/HTML 메시지용 Workflows 웹훅 URL
         * @return 현재 빌더
         */
        public Builder messageWebhookUrl(String messageWebhookUrl) {
            this.messageWebhookUrl = messageWebhookUrl;
            return this;
        }

        /**
         * 서명 링크가 가리킬 우리 웹 시스템의 기본 도메인 주소를 설정합니다.
         *
         * @param baseUrl 기본 주소 (예: {@code "https://admin.our-service.com"})
         * @return 현재 빌더
         */
        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        /**
         * 서명 링크 생성 시 사용할 진입 경로 템플릿을 설정합니다.
         *
         * @param entryPathTemplate 플레이스홀더를 포함한 경로 (예: {@code "/admin/orders/{id}"})
         * @return 현재 빌더
         */
        public Builder entryPathTemplate(String entryPathTemplate) {
            this.entryPathTemplate = entryPathTemplate;
            return this;
        }

        /**
         * 링크 서명 및 위변조 검증에 사용할 {@code HMAC-SHA256} 비밀키를 설정합니다.
         *
         * @param linkSecret 최소 16바이트 이상의 비밀키 ({@code null} 및 빈 문자열 불가)
         * @return 현재 빌더
         */
        public Builder linkSecret(String linkSecret) {
            this.linkSecret = linkSecret;
            return this;
        }

        /**
         * 서명된 링크의 유효시간(분 단위)을 설정합니다.
         *
         * @param linkTtlMinutes 유효시간(분, 0 이하 전달 시 기본값 60 적용)
         * @return 현재 빌더
         */
        public Builder linkTtlMinutes(long linkTtlMinutes) {
            this.linkTtlMinutes = linkTtlMinutes;
            return this;
        }

        /**
         * MS Teams 표준 채널 Outgoing Webhook이 전송하는 {@code Authorization} 헤더 검증용 Base64 토큰을 설정합니다.
         *
         * @param outgoingWebhookSecret Base64 보안 토큰
         * @return 현재 빌더
         */
        public Builder outgoingWebhookSecret(String outgoingWebhookSecret) {
            this.outgoingWebhookSecret = outgoingWebhookSecret;
            return this;
        }

        /**
         * 웹훅 HTTP 클라이언트의 연결 수립 타임아웃(초)을 설정합니다.
         *
         * @param connectTimeoutSeconds 연결 타임아웃(초)
         * @return 현재 빌더
         */
        public Builder connectTimeoutSeconds(int connectTimeoutSeconds) {
            this.connectTimeoutSeconds = connectTimeoutSeconds;
            return this;
        }

        /**
         * 웹훅 HTTP 요청당 응답 대기 타임아웃(초)을 설정합니다.
         *
         * @param requestTimeoutSeconds 요청 타임아웃(초)
         * @return 현재 빌더
         */
        public Builder requestTimeoutSeconds(int requestTimeoutSeconds) {
            this.requestTimeoutSeconds = requestTimeoutSeconds;
            return this;
        }

        /**
         * 최근 발송 페이로드 인메모리 Outbox 최대 보관 개수를 설정합니다.
         * 운영 환경에서 메모리 보관이 불필요하면 {@code 0}으로 설정하세요.
         *
         * @param maxOutboxSize 최대 보관 개수 (음수 전달 시 {@code 0}으로 보정)
         * @return 현재 빌더
         */
        public Builder maxOutboxSize(int maxOutboxSize) {
            this.maxOutboxSize = maxOutboxSize;
            return this;
        }

        /**
         * 시각 문자열 포맷팅에 사용할 표준 시간대를 설정합니다.
         *
         * @param displayZone 표시용 {@link ZoneId}
         * @return 현재 빌더
         */
        public Builder displayZone(ZoneId displayZone) {
            this.displayZone = displayZone;
            return this;
        }

        /**
         * 입력된 설정값을 검증하고 불변 {@link TeamsSettings} 인스턴스를 생성합니다.
         *
         * @return 검증 완료된 {@link TeamsSettings}
         * @throws NullPointerException     {@code linkSecret}이 {@code null}인 경우
         * @throws IllegalArgumentException {@code linkSecret}이 비어 있거나 16바이트 미만인 경우
         */
        public TeamsSettings build() {
            return new TeamsSettings(this);
        }
    }
}
