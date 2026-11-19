package com.example.billingteams.client;

import com.example.billingteams.cards.BillingAdaptiveCardFactory.CardBuildResult;
import com.example.billingteams.config.TeamsClientSettings;
import com.example.billingteams.domain.BillingEventType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * JDK 17 표준 {@link HttpClient} 기반 MS Teams Workflows Incoming Webhook 전송 클라이언트.
 * Spring Web 의존성 없이 모든 Java 17 애플리케이션에서 독립 라이브러리로 동작합니다.
 */
public class TeamsWorkflowWebhookClient {

    private static final Logger log = LoggerFactory.getLogger(TeamsWorkflowWebhookClient.class);

    public record TeamsDispatchRecord(
            String dispatchId,
            String txId,
            BillingEventType eventType,
            String summary,
            String signedDetailUrl,
            String signedActionUrl,
            boolean liveDelivered,
            int httpStatus,
            String deliveryNote,
            Instant dispatchedAt,
            Map<String, Object> payload,
            String prettyJson
    ) {}

    private final TeamsClientSettings settings;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final CopyOnWriteArrayList<TeamsDispatchRecord> outbox = new CopyOnWriteArrayList<>();

    public TeamsWorkflowWebhookClient(TeamsClientSettings settings) {
        this(settings, createDefaultObjectMapper());
    }

    public TeamsWorkflowWebhookClient(TeamsClientSettings settings, ObjectMapper objectMapper) {
        this(
                settings,
                objectMapper,
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(settings.getConnectTimeoutSeconds()))
                        .build()
        );
    }

    public TeamsWorkflowWebhookClient(TeamsClientSettings settings, ObjectMapper objectMapper, HttpClient httpClient) {
        this.settings = Objects.requireNonNull(settings, "settings must not be null");
        this.objectMapper = objectMapper != null ? objectMapper : createDefaultObjectMapper();
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
    }

    public TeamsDispatchRecord sendCard(String txId, CardBuildResult cardResult) {
        return sendCardToWebhook(settings.getWebhookUrl(), txId, cardResult);
    }

    /**
     * 채널별로 서로 다른 Teams Workflows Webhook URL(예: 정산방 vs 긴급장애방)로 라우팅할 때 사용합니다.
     */
    public TeamsDispatchRecord sendCardToWebhook(String targetWebhookUrl, String txId, CardBuildResult cardResult) {
        return dispatchInternal(
                targetWebhookUrl,
                txId,
                cardResult.eventType(),
                cardResult.summaryText(),
                cardResult.signedDetailUrl(),
                cardResult.signedActionUrl(),
                cardResult.teamsEnvelope()
        );
    }

    /**
     * 커스텀 Adaptive Card 또는 Workflows 트리거용 JSON 페이로드를 직접 전송합니다.
     */
    public TeamsDispatchRecord sendRawPayload(
            String targetWebhookUrl,
            String txId,
            BillingEventType eventType,
            String summary,
            Map<String, Object> payload
    ) {
        String effectiveUrl = (targetWebhookUrl != null && !targetWebhookUrl.isBlank())
                ? targetWebhookUrl
                : settings.getWebhookUrl();
        return dispatchInternal(effectiveUrl, txId, eventType, summary, null, null, payload);
    }

    private TeamsDispatchRecord dispatchInternal(
            String webhookUrl,
            String txId,
            BillingEventType eventType,
            String summary,
            String signedDetailUrl,
            String signedActionUrl,
            Map<String, Object> payload
    ) {
        String dispatchId = "DSP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Instant now = Instant.now();
        String prettyJson = toPrettyJson(payload);

        boolean liveDelivered = false;
        int statusCode = 200;
        String deliveryNote;

        if (webhookUrl == null || webhookUrl.isBlank()) {
            deliveryNote = "DRY-RUN (TEAMS_WEBHOOK_URL 미설정 — 로컬 Outbox에 저장됨)";
            log.info("[Teams Dry-Run] {} -> {}", dispatchId, summary);
        } else {
            try {
                String jsonBody = objectMapper.writeValueAsString(payload);
                // 주의: Workflows 'Anyone' 트리거는 Authorization 헤더가 있으면 실패하므로 Content-Type만 전송
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(webhookUrl.trim()))
                        .timeout(Duration.ofSeconds(settings.getRequestTimeoutSeconds()))
                        .header("Content-Type", "application/json; charset=UTF-8")
                        .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                statusCode = response.statusCode();
                liveDelivered = statusCode >= 200 && statusCode < 300;
                deliveryNote = liveDelivered
                        ? "Teams Workflows Webhook 전송 성공 (HTTP " + statusCode + ")"
                        : "Teams Webhook 비정상 응답 (HTTP " + statusCode + ")";
                log.info("[Teams Webhook Sent] {} -> HTTP {}", dispatchId, statusCode);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                statusCode = 502;
                deliveryNote = "Teams Webhook 전송 인터럽트: " + ie.getMessage();
                log.warn("[Teams Webhook Interrupted] {} -> {}", dispatchId, ie.getMessage());
            } catch (Exception e) {
                statusCode = 502;
                deliveryNote = "Teams Webhook 전송 실패: " + e.getMessage();
                log.warn("[Teams Webhook Failed] {} -> {}", dispatchId, e.getMessage());
            }
        }

        TeamsDispatchRecord record = new TeamsDispatchRecord(
                dispatchId,
                txId,
                eventType,
                summary,
                signedDetailUrl,
                signedActionUrl,
                liveDelivered,
                statusCode,
                deliveryNote,
                now,
                payload,
                prettyJson
        );

        int maxOutbox = settings.getMaxOutboxSize();
        if (maxOutbox > 0) {
            outbox.add(0, record);
            while (outbox.size() > maxOutbox) {
                outbox.remove(outbox.size() - 1);
            }
        }
        return record;
    }

    public List<TeamsDispatchRecord> getOutbox() {
        return Collections.unmodifiableList(new ArrayList<>(outbox));
    }

    public void clearOutbox() {
        outbox.clear();
    }

    private String toPrettyJson(Object payload) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            return String.valueOf(payload);
        }
    }

    private static ObjectMapper createDefaultObjectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
}
