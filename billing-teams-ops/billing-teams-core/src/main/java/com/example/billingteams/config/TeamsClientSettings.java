package com.example.billingteams.config;

import java.util.Objects;

/**
 * 프레임워크 비종속(Pure Java) MS Teams 알림 및 서명 링크 설정 객체.
 * Spring Boot 환경에서는 BillingTeamsProperties를 통해 자동 생성되며,
 * 일반 Java / 타 프레임워크 환경에서는 {@link #builder()}로 직접 생성할 수 있습니다.
 */
public final class TeamsClientSettings {

    public static final String DEFAULT_BASE_URL = "http://localhost:8080";
    public static final String DEFAULT_ENTRY_PATH_TEMPLATE = "/ops/transactions/{txId}";
    public static final String DEFAULT_DEV_LINK_SECRET = "dev-billing-teams-hmac-secret-key-change-in-prod-2026";

    private final String webhookUrl;
    private final String baseUrl;
    private final String entryPathTemplate;
    private final String linkSecret;
    private final long linkTtlMinutes;
    private final String outgoingWebhookSecret;
    private final int connectTimeoutSeconds;
    private final int requestTimeoutSeconds;
    private final int maxOutboxSize;

    private TeamsClientSettings(Builder builder) {
        this.webhookUrl = builder.webhookUrl != null ? builder.webhookUrl.trim() : "";
        this.baseUrl = (builder.baseUrl != null && !builder.baseUrl.isBlank())
                ? builder.baseUrl.trim()
                : DEFAULT_BASE_URL;
        this.entryPathTemplate = (builder.entryPathTemplate != null && !builder.entryPathTemplate.isBlank())
                ? builder.entryPathTemplate.trim()
                : DEFAULT_ENTRY_PATH_TEMPLATE;
        this.linkSecret = Objects.requireNonNull(builder.linkSecret, "linkSecret must not be null");
        if (this.linkSecret.isBlank()) {
            throw new IllegalArgumentException("linkSecret must not be blank");
        }
        this.linkTtlMinutes = builder.linkTtlMinutes > 0 ? builder.linkTtlMinutes : 60L;
        this.outgoingWebhookSecret = builder.outgoingWebhookSecret != null ? builder.outgoingWebhookSecret.trim() : "";
        this.connectTimeoutSeconds = builder.connectTimeoutSeconds > 0 ? builder.connectTimeoutSeconds : 5;
        this.requestTimeoutSeconds = builder.requestTimeoutSeconds > 0 ? builder.requestTimeoutSeconds : 10;
        this.maxOutboxSize = Math.max(0, builder.maxOutboxSize);
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getWebhookUrl() {
        return webhookUrl;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getEntryPathTemplate() {
        return entryPathTemplate;
    }

    public String getLinkSecret() {
        return linkSecret;
    }

    public long getLinkTtlMinutes() {
        return linkTtlMinutes;
    }

    public String getOutgoingWebhookSecret() {
        return outgoingWebhookSecret;
    }

    public int getConnectTimeoutSeconds() {
        return connectTimeoutSeconds;
    }

    public int getRequestTimeoutSeconds() {
        return requestTimeoutSeconds;
    }

    public int getMaxOutboxSize() {
        return maxOutboxSize;
    }

    public static final class Builder {
        private String webhookUrl = "";
        private String baseUrl = DEFAULT_BASE_URL;
        private String entryPathTemplate = DEFAULT_ENTRY_PATH_TEMPLATE;
        private String linkSecret = DEFAULT_DEV_LINK_SECRET;
        private long linkTtlMinutes = 60L;
        private String outgoingWebhookSecret = "";
        private int connectTimeoutSeconds = 5;
        private int requestTimeoutSeconds = 10;
        private int maxOutboxSize = 50;

        private Builder() {}

        public Builder webhookUrl(String webhookUrl) {
            this.webhookUrl = webhookUrl;
            return this;
        }

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        /**
         * 서명 링크 생성 시 사용할 경로 템플릿 (예: "/admin/payments/{txId}" 또는 "/ops/transactions/{txId}")
         */
        public Builder entryPathTemplate(String entryPathTemplate) {
            this.entryPathTemplate = entryPathTemplate;
            return this;
        }

        public Builder linkSecret(String linkSecret) {
            this.linkSecret = linkSecret;
            return this;
        }

        public Builder linkTtlMinutes(long linkTtlMinutes) {
            this.linkTtlMinutes = linkTtlMinutes;
            return this;
        }

        public Builder outgoingWebhookSecret(String outgoingWebhookSecret) {
            this.outgoingWebhookSecret = outgoingWebhookSecret;
            return this;
        }

        public Builder connectTimeoutSeconds(int connectTimeoutSeconds) {
            this.connectTimeoutSeconds = connectTimeoutSeconds;
            return this;
        }

        public Builder requestTimeoutSeconds(int requestTimeoutSeconds) {
            this.requestTimeoutSeconds = requestTimeoutSeconds;
            return this;
        }

        /**
         * 최근 발송 페이로드 메모리 보관 개수 (운영 환경에서 메모리 보관이 불필요하면 0 설정)
         */
        public Builder maxOutboxSize(int maxOutboxSize) {
            this.maxOutboxSize = maxOutboxSize;
            return this;
        }

        public TeamsClientSettings build() {
            return new TeamsClientSettings(this);
        }
    }
}
