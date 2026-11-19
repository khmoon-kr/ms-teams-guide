package com.example.teams.autoconfigure;

import com.example.teams.config.TeamsSettings;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.ZoneId;

/**
 * Spring Boot 애플리케이션의 {@code application.yml} / {@code application.properties} 내
 * {@code teams.*} 프리픽스 설정을 바인딩하여 {@link TeamsSettings}로 변환하는 프로퍼티 클래스입니다.
 *
 * <h2>설정 예시 ({@code application.yml})</h2>
 * <pre>{@code
 * teams:
 *   webhook-url: ${TEAMS_WEBHOOK_URL:}
 *   base-url: https://admin.our-service.com
 *   entry-path-template: /admin/orders/{id}
 *   link-secret: ${TEAMS_LINK_SECRET}
 *   link-ttl-minutes: 30
 *   max-outbox-size: 0
 *   display-zone: Asia/Seoul
 * }</pre>
 *
 * @see TeamsAutoConfiguration
 * @see TeamsSettings
 */
@ConfigurationProperties(prefix = "teams")
public class TeamsProperties {

    /**
     * 기본 전송 대상 MS Teams Workflows Incoming Webhook URL입니다 (미설정 시 Dry-Run 모드).
     */
    private String webhookUrl = "";

    /**
     * 서명 링크가 가리킬 웹 애플리케이션의 기본 도메인 주소입니다.
     */
    private String baseUrl = TeamsSettings.DEFAULT_BASE_URL;

    /**
     * 기본 서명 링크 생성 시 사용할 경로 템플릿입니다 ({@code {id}} 플레이스홀더 자동 치환).
     */
    private String entryPathTemplate = TeamsSettings.DEFAULT_ENTRY_PATH_TEMPLATE;

    /**
     * 외부 이동 링크 서명·검증에 사용할 최소 16바이트 이상의 {@code HMAC-SHA256} 비밀키입니다.
     */
    private String linkSecret = TeamsSettings.DEFAULT_DEV_LINK_SECRET;

    /**
     * 서명된 링크의 유효시간(분 단위)입니다.
     */
    private long linkTtlMinutes = 60;

    /**
     * MS Teams 표준 채널 Outgoing Webhook 검증용 Base64 보안 토큰입니다 (선택).
     */
    private String outgoingWebhookSecret = "";

    /**
     * 웹훅 HTTP 연결 수립 타임아웃(초)입니다.
     */
    private int connectTimeoutSeconds = 5;

    /**
     * 웹훅 HTTP 요청당 응답 대기 타임아웃(초)입니다.
     */
    private int requestTimeoutSeconds = 10;

    /**
     * 인메모리 Outbox 최대 보관 개수입니다 (실서비스에서 메모리 적재를 끄려면 {@code 0}).
     */
    private int maxOutboxSize = 50;

    /**
     * 알림 본문 내 시각 포맷팅에 사용할 표준 시간대 ID입니다 (예: {@code "Asia/Seoul"}, {@code "UTC"}).
     */
    private String displayZone = "Asia/Seoul";

    /**
     * 기본 프로퍼티 인스턴스를 생성합니다.
     */
    public TeamsProperties() {
    }

    /**
     * 바인딩된 프로퍼티 값을 검증하여 불변 {@link TeamsSettings} 객체로 변환합니다.
     *
     * @return 검증 완료된 {@link TeamsSettings}
     */
    public TeamsSettings toSettings() {
        ZoneId resolvedZone = (displayZone != null && !displayZone.isBlank())
                ? ZoneId.of(displayZone.trim())
                : ZoneId.of("Asia/Seoul");
        return TeamsSettings.builder()
                .webhookUrl(webhookUrl)
                .baseUrl(baseUrl)
                .entryPathTemplate(entryPathTemplate)
                .linkSecret(linkSecret)
                .linkTtlMinutes(linkTtlMinutes)
                .outgoingWebhookSecret(outgoingWebhookSecret)
                .connectTimeoutSeconds(connectTimeoutSeconds)
                .requestTimeoutSeconds(requestTimeoutSeconds)
                .maxOutboxSize(maxOutboxSize)
                .displayZone(resolvedZone)
                .build();
    }

    /**
     * 기본 웹훅 URL을 반환합니다.
     *
     * @return 웹훅 URL
     */
    public String getWebhookUrl() {
        return webhookUrl;
    }

    /**
     * 기본 웹훅 URL을 설정합니다.
     *
     * @param webhookUrl 웹훅 URL
     */
    public void setWebhookUrl(String webhookUrl) {
        this.webhookUrl = webhookUrl;
    }

    /**
     * 서명 링크 기준 도메인 주소를 반환합니다.
     *
     * @return 기본 URL
     */
    public String getBaseUrl() {
        return baseUrl;
    }

    /**
     * 서명 링크 기준 도메인 주소를 설정합니다.
     *
     * @param baseUrl 기본 URL
     */
    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    /**
     * 기본 서명 링크 경로 템플릿을 반환합니다.
     *
     * @return 경로 템플릿
     */
    public String getEntryPathTemplate() {
        return entryPathTemplate;
    }

    /**
     * 기본 서명 링크 경로 템플릿을 설정합니다.
     *
     * @param entryPathTemplate 경로 템플릿
     */
    public void setEntryPathTemplate(String entryPathTemplate) {
        this.entryPathTemplate = entryPathTemplate;
    }

    /**
     * 링크 서명용 {@code HMAC-SHA256} 비밀키를 반환합니다.
     *
     * @return 비밀키 문자열
     */
    public String getLinkSecret() {
        return linkSecret;
    }

    /**
     * 링크 서명용 {@code HMAC-SHA256} 비밀키를 설정합니다.
     *
     * @param linkSecret 최소 16바이트 이상의 비밀키
     */
    public void setLinkSecret(String linkSecret) {
        this.linkSecret = linkSecret;
    }

    /**
     * 서명 링크 유효시간(분)을 반환합니다.
     *
     * @return 유효시간(분)
     */
    public long getLinkTtlMinutes() {
        return linkTtlMinutes;
    }

    /**
     * 서명 링크 유효시간(분)을 설정합니다.
     *
     * @param linkTtlMinutes 유효시간(분)
     */
    public void setLinkTtlMinutes(long linkTtlMinutes) {
        this.linkTtlMinutes = linkTtlMinutes;
    }

    /**
     * Outgoing Webhook 검증용 Base64 보안 토큰을 반환합니다.
     *
     * @return Base64 보안 토큰
     */
    public String getOutgoingWebhookSecret() {
        return outgoingWebhookSecret;
    }

    /**
     * Outgoing Webhook 검증용 Base64 보안 토큰을 설정합니다.
     *
     * @param outgoingWebhookSecret Base64 보안 토큰
     */
    public void setOutgoingWebhookSecret(String outgoingWebhookSecret) {
        this.outgoingWebhookSecret = outgoingWebhookSecret;
    }

    /**
     * 웹훅 연결 타임아웃(초)을 반환합니다.
     *
     * @return 연결 타임아웃(초)
     */
    public int getConnectTimeoutSeconds() {
        return connectTimeoutSeconds;
    }

    /**
     * 웹훅 연결 타임아웃(초)을 설정합니다.
     *
     * @param connectTimeoutSeconds 연결 타임아웃(초)
     */
    public void setConnectTimeoutSeconds(int connectTimeoutSeconds) {
        this.connectTimeoutSeconds = connectTimeoutSeconds;
    }

    /**
     * 웹훅 응답 대기 타임아웃(초)을 반환합니다.
     *
     * @return 요청 타임아웃(초)
     */
    public int getRequestTimeoutSeconds() {
        return requestTimeoutSeconds;
    }

    /**
     * 웹훅 응답 대기 타임아웃(초)을 설정합니다.
     *
     * @param requestTimeoutSeconds 요청 타임아웃(초)
     */
    public void setRequestTimeoutSeconds(int requestTimeoutSeconds) {
        this.requestTimeoutSeconds = requestTimeoutSeconds;
    }

    /**
     * 인메모리 Outbox 최대 보관 개수를 반환합니다.
     *
     * @return 최대 보관 개수
     */
    public int getMaxOutboxSize() {
        return maxOutboxSize;
    }

    /**
     * 인메모리 Outbox 최대 보관 개수를 설정합니다.
     *
     * @param maxOutboxSize 최대 보관 개수 ({@code 0}이면 비활성화)
     */
    public void setMaxOutboxSize(int maxOutboxSize) {
        this.maxOutboxSize = maxOutboxSize;
    }

    /**
     * 표시용 표준 시간대 ID를 반환합니다.
     *
     * @return 시간대 ID 문자열
     */
    public String getDisplayZone() {
        return displayZone;
    }

    /**
     * 표시용 표준 시간대 ID를 설정합니다.
     *
     * @param displayZone 시간대 ID 문자열 (예: {@code "Asia/Seoul"})
     */
    public void setDisplayZone(String displayZone) {
        this.displayZone = displayZone;
    }
}
