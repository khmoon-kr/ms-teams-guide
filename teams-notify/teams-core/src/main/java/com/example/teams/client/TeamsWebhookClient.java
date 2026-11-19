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
 * 표준 JDK 17 {@link HttpClient}를 이용해 MS Teams Workflows Incoming Webhook으로
 * JSON 페이로드를 전송하는 스레드 안전(Thread-Safe) 클라이언트입니다.
 *
 * <h2>설계 특징</h2>
 * <ul>
 *   <li><b>Dry-Run 지원</b>: 웹훅 URL이 설정되지 않은 로컬·테스트 환경에서는 외부 네트워크 호출 없이
 *       페이로드 직렬화 및 Outbox 기록만 수행합니다.</li>
 *   <li><b>다중 채널 분기 전송</b>: 기본 설정된 URL 외에도 {@link #sendCardToWebhook(String, String, CardBuildResult)}을 통해
 *       심각도나 주제별로 서로 다른 Teams 채팅·채널 웹훅으로 전송할 수 있습니다.</li>
 *   <li><b>민감 URL 마스킹</b>: 네트워크 오류 발생 시 예외 메시지에 포함될 수 있는 웹훅 시크릿 URL을
 *       {@code [REDACTED_URL]}로 마스킹하여 로그 유출을 방지합니다.</li>
 * </ul>
 *
 * @see com.example.teams.TeamsNotifier#webhookClient()
 */
public class TeamsWebhookClient {

    private static final Logger log = LoggerFactory.getLogger(TeamsWebhookClient.class);
    private static final Pattern URL_TOKEN_MASK = Pattern.compile("https?://\\S+");

    /**
     * 단건 웹훅 발송 시도 결과와 전송된 원본 페이로드 스냅샷을 담는 불변 레코드입니다.
     *
     * @param dispatchId      발송 추적용 고유 식별자 (예: {@code "DSP-8A12F901"})
     * @param resourceId      발송 대상 리소스 식별자
     * @param eventType       이벤트 유형 식별자
     * @param summary         로그 및 목록 표시용 한 줄 요약
     * @param signedDetailUrl 카드에 포함된 첫 번째 서명 링크 URL
     * @param signedActionUrl 카드에 포함된 두 번째 서명 링크 URL
     * @param liveDelivered   실제 원격 Teams 웹훅 엔드포인트가 2xx 응답을 반환했는지 여부 (Dry-Run 시 {@code false})
     * @param httpStatus      HTTP 응답 상태 코드 (Dry-Run 시 {@code 200}, 네트워크 오류 시 {@code 502})
     * @param deliveryNote    발송 결과 요약 메시지 (실패 시 URL이 마스킹된 사유 포함)
     * @param dispatchedAt    발송 시각 (UTC {@link Instant})
     * @param payload         전송된 최상위 JSON 맵 (수정 불가)
     * @param prettyJson      들여쓰기 포맷팅된 전송 JSON 문자열
     */
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
        /**
         * 필수 인자 검증 및 방어적 불변 복사를 수행하는 정규 생성자입니다.
         *
         * @param dispatchId      발송 추적용 고유 식별자 (예: {@code "DSP-8A12F901"})
         * @param resourceId      발송 대상 리소스 식별자
         * @param eventType       이벤트 유형 식별자
         * @param summary         로그 및 목록 표시용 한 줄 요약
         * @param signedDetailUrl 카드에 포함된 첫 번째 서명 링크 URL
         * @param signedActionUrl 카드에 포함된 두 번째 서명 링크 URL
         * @param liveDelivered   실제 원격 Teams 웹훅 엔드포인트가 2xx 응답을 반환했는지 여부 (Dry-Run 시 {@code false})
         * @param httpStatus      HTTP 응답 상태 코드 (Dry-Run 시 {@code 200}, 네트워크 오류 시 {@code 502})
         * @param deliveryNote    발송 결과 요약 메시지 (실패 시 URL이 마스킹된 사유 포함)
         * @param dispatchedAt    발송 시각 (UTC {@link Instant})
         * @param payload         전송된 최상위 JSON 맵 (수정 불가)
         * @param prettyJson      들여쓰기 포맷팅된 전송 JSON 문자열
         */
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

    /**
     * 기본 Jackson 매퍼, 기본 타임아웃 {@link HttpClient}, 시스템 UTC 시계를 사용하여 클라이언트를 생성합니다.
     *
     * @param settings Teams 설정 객체 ({@code null} 불가)
     */
    public TeamsWebhookClient(TeamsSettings settings) {
        this(settings, createDefaultObjectMapper());
    }

    /**
     * 지정된 {@link ObjectMapper}와 설정 타임아웃 기반 {@link HttpClient}로 클라이언트를 생성합니다.
     *
     * @param settings     Teams 설정 객체 ({@code null} 불가)
     * @param objectMapper JSON 직렬화에 사용할 매퍼 ({@code null}이면 기본 매퍼 생성)
     */
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

    /**
     * 사내 프록시·mTLS 등이 설정된 커스텀 {@link HttpClient}와 {@link Clock}을 주입받아 클라이언트를 생성합니다.
     *
     * @param settings     Teams 설정 객체 ({@code null} 불가)
     * @param objectMapper JSON 직렬화에 사용할 매퍼 ({@code null}이면 기본 매퍼 생성)
     * @param httpClient   전송에 사용할 JDK {@link HttpClient} ({@code null} 불가)
     * @param clock        발송 시각 기록용 시계 ({@code null} 불가)
     */
    public TeamsWebhookClient(TeamsSettings settings, ObjectMapper objectMapper, HttpClient httpClient, Clock clock) {
        this.settings = Objects.requireNonNull(settings, "settings must not be null");
        this.objectMapper = objectMapper != null ? objectMapper : createDefaultObjectMapper();
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * {@link TeamsSettings#getWebhookUrl()}에 설정된 기본 웹훅 주소로 조립된 카드를 전송합니다.
     *
     * @param resourceId 발송 대상 리소스 식별자
     * @param cardResult {@link com.example.teams.cards.TeamsCardFactory}가 생성한 카드 결과 ({@code null} 불가)
     * @return 발송 상태 및 페이로드 기록이 담긴 {@link TeamsDispatchRecord}
     */
    public TeamsDispatchRecord sendCard(String resourceId, CardBuildResult cardResult) {
        return sendCardToWebhook(settings.getWebhookUrl(), resourceId, cardResult);
    }

    /**
     * 기본 설정 URL 대신 호출 시 지정한 특정 Teams Workflows Webhook URL로 카드를 전송합니다.
     *
     * @param targetWebhookUrl 전송 대상 웹훅 URL ({@code null} 또는 빈 문자열이면 Dry-Run 처리)
     * @param resourceId       발송 대상 리소스 식별자
     * @param cardResult       조립된 카드 결과 ({@code null} 불가)
     * @return 발송 상태 및 페이로드 기록이 담긴 {@link TeamsDispatchRecord}
     */
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

    /**
     * 임의의 커스텀 JSON 맵(예: Workflows {@code Parse JSON} 트리거용 데이터 페이로드)을 직접 전송합니다.
     *
     * @param targetWebhookUrl 전송 대상 웹훅 URL ({@code null} 또는 공백이면 기본 설정 URL 사용)
     * @param resourceId       발송 대상 리소스 식별자
     * @param eventType        이벤트 유형 코드
     * @param summary          로그 표시용 한 줄 요약
     * @param payload          전송할 JSON 객체 맵
     * @return 발송 상태 및 페이로드 기록이 담긴 {@link TeamsDispatchRecord}
     */
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

    /**
     * 인메모리 Outbox에 보관된 최근 발송 이력 목록을 최신순 불변 스냅샷으로 반환합니다.
     * {@link TeamsSettings#getMaxOutboxSize()}가 {@code 0}이면 항상 빈 리스트를 반환합니다.
     *
     * @return 최신순 {@link TeamsDispatchRecord} 불변 리스트
     */
    public List<TeamsDispatchRecord> getOutbox() {
        return Collections.unmodifiableList(new ArrayList<>(outbox));
    }

    /**
     * 인메모리 Outbox에 보관된 모든 발송 이력을 비웁니다.
     */
    public void clearOutbox() {
        outbox.clear();
    }

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
