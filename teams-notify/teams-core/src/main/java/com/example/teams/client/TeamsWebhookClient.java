package com.example.teams.client;

import com.example.teams.cards.TeamsCardFactory.CardBuildResult;
import com.example.teams.config.TeamsSettings;
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
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;

/**
 * JDK 17 표준 {@link HttpClient} 기반 MS Teams Workflows Incoming Webhook 전송 클라이언트 (도메인 중립).
 */
public class TeamsWebhookClient {

    private static final Logger log = LoggerFactory.getLogger(TeamsWebhookClient.class);
    private static final Pattern URL_TOKEN_MASK = Pattern.compile("https?://\\S+");

    public record TeamsDispatchRecord(
            String dispatchId,
            String resourceId,
            String eventType,
            String summary,
            String signedDetailUrl,
            String signedActionUrl,
            boolean liveDelivered,
            int httpStatus,
            String deliveryNote,
            Instant dispatchedAt,
            Map<String, Object> payload,
            String prettyJson
    ) {
        public TeamsDispatchRecord {
            payload = payload != null
                    ? Collections.unmodifiableMap(new LinkedHashMap<>(payload))
                    : Map.of();
        }
    }

    private final TeamsSettings settings;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final Clock clock;
    private final CopyOnWriteArrayList<TeamsDispatchRecord> outbox = new CopyOnWriteArrayList<>();

    public TeamsWebhookClient(TeamsSettings settings) {
        this(settings, createDefaultObjectMapper());
    }

    public TeamsWebhookClient(TeamsSettings settings, ObjectMapper objectMapper) {
        this(
                settings,
                objectMapper,
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(settings.getConnectTimeoutSeconds()))
                        .build(),
                Clock.systemUTC()
        );
    }

    public TeamsWebhookClient(TeamsSettings settings, ObjectMapper objectMapper, HttpClient httpClient, Clock clock) {
        this.settings = Objects.requireNonNull(settings, "settings must not be null");
        this.objectMapper = objectMapper != null ? objectMapper : createDefaultObjectMapper();
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public TeamsDispatchRecord sendCard(String resourceId, CardBuildResult cardResult) {
        return sendCardToWebhook(settings.getWebhookUrl(), resourceId, cardResult);
    }

    public TeamsDispatchRecord sendCardToWebhook(String targetWebhookUrl, String resourceId, CardBuildResult cardResult) {
        Objects.requireNonNull(cardResult, "cardResult must not be null");
        return dispatchInternal(
                targetWebhookUrl,
                resourceId,
                cardResult.eventType(),
                cardResult.summaryText(),
                cardResult.signedDetailUrl(),
                cardResult.signedActionUrl(),
                cardResult.teamsEnvelope()
        );
    }

    public TeamsDispatchRecord sendRawPayload(
            String targetWebhookUrl,
            String resourceId,
            String eventType,
            String summary,
            Map<String, Object> payload
    ) {
        String effectiveUrl = (targetWebhookUrl != null && !targetWebhookUrl.isBlank())
                ? targetWebhookUrl
                : settings.getWebhookUrl();
        return dispatchInternal(effectiveUrl, resourceId, eventType, summary, null, null, payload);
    }

    private TeamsDispatchRecord dispatchInternal(
            String webhookUrl,
            String resourceId,
            String eventType,
            String summary,
            String signedDetailUrl,
            String signedActionUrl,
            Map<String, Object> payload
    ) {
        String dispatchId = "DSP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Instant now = Instant.now(clock);
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
                // 주의: Workflows 'Anyone' 트리거는 Authorization 헤더가 있으면 실패하므로 Content-Type만 전송 (규칙 6.4)
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
                String safeMsg = sanitizeErrorMessage(ie);
                deliveryNote = "Teams Webhook 전송 인터럽트: " + safeMsg;
                log.warn("[Teams Webhook Interrupted] {} -> {}", dispatchId, safeMsg);
            } catch (Exception e) {
                statusCode = 502;
                String safeMsg = sanitizeErrorMessage(e);
                deliveryNote = "Teams Webhook 전송 실패: " + safeMsg;
                log.warn("[Teams Webhook Failed] {} -> {}", dispatchId, safeMsg);
            }
        }

        TeamsDispatchRecord record = new TeamsDispatchRecord(
                dispatchId,
                resourceId,
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

    /**
     * 예외 메시지에 시크릿 토큰이 포함된 전체 Webhook URL이 노출되지 않도록 마스킹합니다. (규칙 6.2)
     */
    static String sanitizeErrorMessage(Throwable error) {
        if (error == null || error.getMessage() == null) {
            return error != null ? error.getClass().getSimpleName() : "unknown error";
        }
        return URL_TOKEN_MASK.matcher(error.getMessage()).replaceAll("[REDACTED_URL]");
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
