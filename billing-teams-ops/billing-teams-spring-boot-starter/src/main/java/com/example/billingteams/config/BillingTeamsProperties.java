package com.example.billingteams.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "billing.teams")
public class BillingTeamsProperties {

    private String webhookUrl = "";
    private String baseUrl = TeamsClientSettings.DEFAULT_BASE_URL;
    private String entryPathTemplate = TeamsClientSettings.DEFAULT_ENTRY_PATH_TEMPLATE;
    private String linkSecret = TeamsClientSettings.DEFAULT_DEV_LINK_SECRET;
    private long linkTtlMinutes = 60;
    private String outgoingWebhookSecret = "";
    private int connectTimeoutSeconds = 5;
    private int requestTimeoutSeconds = 10;
    private int maxOutboxSize = 50;

    public TeamsClientSettings toClientSettings() {
        return TeamsClientSettings.builder()
                .webhookUrl(webhookUrl)
                .baseUrl(baseUrl)
                .entryPathTemplate(entryPathTemplate)
                .linkSecret(linkSecret)
                .linkTtlMinutes(linkTtlMinutes)
                .outgoingWebhookSecret(outgoingWebhookSecret)
                .connectTimeoutSeconds(connectTimeoutSeconds)
                .requestTimeoutSeconds(requestTimeoutSeconds)
                .maxOutboxSize(maxOutboxSize)
                .build();
    }

    public String getWebhookUrl() {
        return webhookUrl;
    }

    public void setWebhookUrl(String webhookUrl) {
        this.webhookUrl = webhookUrl;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getEntryPathTemplate() {
        return entryPathTemplate;
    }

    public void setEntryPathTemplate(String entryPathTemplate) {
        this.entryPathTemplate = entryPathTemplate;
    }

    public String getLinkSecret() {
        return linkSecret;
    }

    public void setLinkSecret(String linkSecret) {
        this.linkSecret = linkSecret;
    }

    public long getLinkTtlMinutes() {
        return linkTtlMinutes;
    }

    public void setLinkTtlMinutes(long linkTtlMinutes) {
        this.linkTtlMinutes = linkTtlMinutes;
    }

    public String getOutgoingWebhookSecret() {
        return outgoingWebhookSecret;
    }

    public void setOutgoingWebhookSecret(String outgoingWebhookSecret) {
        this.outgoingWebhookSecret = outgoingWebhookSecret;
    }

    public int getConnectTimeoutSeconds() {
        return connectTimeoutSeconds;
    }

    public void setConnectTimeoutSeconds(int connectTimeoutSeconds) {
        this.connectTimeoutSeconds = connectTimeoutSeconds;
    }

    public int getRequestTimeoutSeconds() {
        return requestTimeoutSeconds;
    }

    public void setRequestTimeoutSeconds(int requestTimeoutSeconds) {
        this.requestTimeoutSeconds = requestTimeoutSeconds;
    }

    public int getMaxOutboxSize() {
        return maxOutboxSize;
    }

    public void setMaxOutboxSize(int maxOutboxSize) {
        this.maxOutboxSize = maxOutboxSize;
    }
}
