package com.example.teams.cards;

import com.example.teams.cards.AdaptiveCardSpec.ActionButton;
import com.example.teams.cards.AdaptiveCardSpec.Fact;
import com.example.teams.security.TeamsLinkSigner;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * {@link AdaptiveCardSpec}을 입력받아 MS Teams 풀와이드({@code width: Full}) Adaptive Card v1.4 문서와
 * Workflows Incoming Webhook 표준 봉투({@code type: "message", attachments: [...]})를 조립하는 스레드 안전 팩토리입니다.
 *
 * <p>또한 카드 하단 버튼에 {@link TeamsLinkSigner}의 {@code HMAC-SHA256} 서명 링크를 손쉽게 연결할 수 있는
 * 헬퍼 메서드({@link #signedEntryButton}, {@link #signedCustomButton})를 함께 제공합니다.</p>
 *
 * @see AdaptiveCardSpec
 * @see com.example.teams.TeamsNotifier#cards()
 */
public class TeamsCardFactory {

    private static final DateTimeFormatter DEFAULT_TS_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z");

    /**
     * 조립이 완료된 Adaptive Card 웹훅 봉투와 서명 링크 요약을 담는 불변 레코드입니다.
     *
     * @param eventType       이벤트 유형 식별자
     * @param summaryText     발송 내역 및 로그 표시용 요약 문구
     * @param signedDetailUrl 첫 번째 액션 버튼의 URL (액션이 없으면 {@code null})
     * @param signedActionUrl 두 번째 액션 버튼의 URL (버튼이 1개뿐이면 {@code signedDetailUrl}과 동일)
     * @param teamsEnvelope   Teams Workflows Webhook으로 즉시 POST 전송 가능한 최상위 JSON 맵 (수정 불가)
     */
    public record CardBuildResult(
            String eventType,
            String summaryText,
            String signedDetailUrl,
            String signedActionUrl,
            Map<String, Object> teamsEnvelope
    ) {
        /**
         * 필수 인자 검증 및 방어적 불변 복사를 수행하는 정규 생성자입니다.
         *
         * @param eventType       이벤트 유형 식별자
         * @param summaryText     발송 내역 및 로그 표시용 요약 문구
         * @param signedDetailUrl 첫 번째 액션 버튼의 URL (액션이 없으면 {@code null})
         * @param signedActionUrl 두 번째 액션 버튼의 URL (버튼이 1개뿐이면 {@code signedDetailUrl}과 동일)
         * @param teamsEnvelope   Teams Workflows Webhook으로 즉시 POST 전송 가능한 최상위 JSON 맵 (수정 불가)
         */
        public CardBuildResult {
            Objects.requireNonNull(eventType, "eventType must not be null");
            Objects.requireNonNull(summaryText, "summaryText must not be null");
            teamsEnvelope = teamsEnvelope != null
                    ? Collections.unmodifiableMap(new LinkedHashMap<>(teamsEnvelope))
                    : Map.of();
        }
    }

    private final TeamsLinkSigner linkSigner;
    private final ZoneId displayZone;

    /**
     * 기본 표시 시간대({@code Asia/Seoul})를 사용하여 카드 팩토리를 생성합니다.
     *
     * @param linkSigner 버튼 URL 서명에 사용할 {@link TeamsLinkSigner} ({@code null} 불가)
     */
    public TeamsCardFactory(TeamsLinkSigner linkSigner) {
        this(linkSigner, ZoneId.of("Asia/Seoul"));
    }

    /**
     * 지정된 표시 시간대({@link ZoneId})를 사용하여 카드 팩토리를 생성합니다.
     *
     * @param linkSigner  버튼 URL 서명에 사용할 {@link TeamsLinkSigner} ({@code null} 불가)
     * @param displayZone {@link #formatTimestamp(Instant)} 호출 시 적용할 시간대 ({@code null}이면 {@code Asia/Seoul})
     */
    public TeamsCardFactory(TeamsLinkSigner linkSigner, ZoneId displayZone) {
        this.linkSigner = Objects.requireNonNull(linkSigner, "linkSigner must not be null");
        this.displayZone = displayZone != null ? displayZone : ZoneId.of("Asia/Seoul");
    }

    /**
     * 설정된 기본 진입 경로({@code entryPathTemplate})에 {@code HMAC-SHA256} 서명을 부착한 카드 액션 버튼을 생성합니다.
     *
     * @param title      버튼에 표시할 텍스트 ({@code null} 불가)
     * @param resourceId 대상 리소스 식별자 ({@code null} 불가)
     * @param cardType   서명에 기록할 카드·이벤트 유형
     * @param action     서명에 기록할 의도 액션 (예: {@code "view"}, {@code "approve"})
     * @return 서명된 URL이 설정된 {@link ActionButton}
     */
    public ActionButton signedEntryButton(String title, String resourceId, String cardType, String action) {
        String signedUrl = linkSigner.createSignedEntryUrl(resourceId, cardType, action);
        return new ActionButton(title, signedUrl);
    }

    /**
     * 임의의 외부 웹 URL에 {@code HMAC-SHA256} 서명을 부착한 카드 액션 버튼을 생성합니다.
     *
     * @param title      버튼에 표시할 텍스트 ({@code null} 불가)
     * @param targetUrl  이동할 대상 URL ({@code null} 불가)
     * @param resourceId 검증 대조용 리소스 식별자 ({@code null} 불가)
     * @param cardType   서명에 기록할 카드·이벤트 유형
     * @param action     서명에 기록할 의도 액션
     * @return 서명된 URL이 설정된 {@link ActionButton}
     */
    public ActionButton signedCustomButton(String title, String targetUrl, String resourceId, String cardType, String action) {
        String signedUrl = linkSigner.signCustomUrl(targetUrl, resourceId, cardType, action);
        return new ActionButton(title, signedUrl);
    }

    /**
     * 선언적 {@link AdaptiveCardSpec}으로부터 Teams 웹훅 전송용 {@link CardBuildResult}를 생성합니다.
     *
     * @param spec 조립할 Adaptive Card 스펙 ({@code null} 불가)
     * @return 불변 웹훅 봉투와 대표 링크가 담긴 {@link CardBuildResult}
     */
    public CardBuildResult buildCard(AdaptiveCardSpec spec) {
        Objects.requireNonNull(spec, "spec must not be null");
        Map<String, Object> cardDoc = toAdaptiveCardMap(spec);
        Map<String, Object> envelope = wrapWebhookEnvelope(cardDoc);

        List<ActionButton> actions = spec.getActions();
        String primaryUrl = !actions.isEmpty() ? actions.get(0).url() : null;
        String secondaryUrl = actions.size() > 1 ? actions.get(1).url() : primaryUrl;

        return new CardBuildResult(
                spec.getEventType(),
                spec.getSummaryText(),
                primaryUrl,
                secondaryUrl,
                envelope
        );
    }

    /**
     * 단일 Adaptive Card JSON 본문 맵을 MS Teams Workflows Webhook 표준 {@code message} 봉투로 감쌉니다.
     *
     * @param adaptiveCard {@code "type": "AdaptiveCard"} 본문 맵 ({@code null} 불가)
     * @return {@code attachments[0].content} 구조를 갖춘 불변 봉투 맵
     */
    public Map<String, Object> wrapWebhookEnvelope(Map<String, Object> adaptiveCard) {
        Objects.requireNonNull(adaptiveCard, "adaptiveCard must not be null");
        Map<String, Object> attachment = new LinkedHashMap<>();
        attachment.put("contentType", "application/vnd.microsoft.card.adaptive");
        attachment.put("contentUrl", null);
        attachment.put("content", Collections.unmodifiableMap(new LinkedHashMap<>(adaptiveCard)));

        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("type", "message");
        envelope.put("attachments", List.of(Collections.unmodifiableMap(attachment)));
        return Collections.unmodifiableMap(envelope);
    }

    /**
     * 이 팩토리에 설정된 기본 시간대({@code displayZone})를 기준으로 {@link Instant}를 사람이 읽기 쉬운 문자열로 변환합니다.
     *
     * @param instant 변환할 시각 ({@code null}이면 {@code "-"} 반환)
     * @return {@code "yyyy-MM-dd HH:mm:ss z"} 형식 문자열
     */
    public String formatTimestamp(Instant instant) {
        return formatTimestamp(instant, displayZone);
    }

    /**
     * 지정한 시간대({@link ZoneId})를 기준으로 {@link Instant}를 포맷팅합니다.
     *
     * @param instant 변환할 시각 ({@code null}이면 {@code "-"} 반환)
     * @param zone    적용할 시간대 ({@code null}이면 {@code UTC})
     * @return 포맷팅된 시각 문자열
     */
    public static String formatTimestamp(Instant instant, ZoneId zone) {
        if (instant == null) {
            return "-";
        }
        ZoneId targetZone = zone != null ? zone : ZoneId.of("UTC");
        return DEFAULT_TS_FMT.format(instant.atZone(targetZone));
    }

    /**
     * 정수 원화 금액을 천 단위 콤마와 원화 기호({@code ₩})가 포함된 문자열로 포맷팅합니다.
     *
     * @param amount 원화 금액
     * @return 예: {@code "₩128,000"}
     */
    public static String formatCurrencyKrw(long amount) {
        return String.format("₩%,d", amount);
    }

    Map<String, Object> toAdaptiveCardMap(AdaptiveCardSpec spec) {
        List<Map<String, Object>> headerItems = new ArrayList<>();
        headerItems.add(Map.of(
                "type", "TextBlock",
                "text", spec.getBadgeLabel(),
                "weight", "Bolder",
                "size", "Small",
                "wrap", true
        ));
        headerItems.add(Map.of(
                "type", "TextBlock",
                "text", spec.getHeadline(),
                "weight", "Bolder",
                "size", "ExtraLarge",
                "color", spec.getHeadlineColor().wireValue(),
                "spacing", "None"
        ));
        if (!spec.getSubtitle().isBlank()) {
            headerItems.add(Map.of(
                    "type", "TextBlock",
                    "text", spec.getSubtitle(),
                    "isSubtle", true,
                    "wrap", true,
                    "spacing", "Small"
            ));
        }

        Map<String, Object> headerContainer = new LinkedHashMap<>();
        headerContainer.put("type", "Container");
        headerContainer.put("style", spec.getBannerStyle().wireValue());
        headerContainer.put("bleed", true);
        headerContainer.put("items", List.copyOf(headerItems));

        List<Map<String, Object>> bodyElements = new ArrayList<>();
        bodyElements.add(Collections.unmodifiableMap(headerContainer));

        if (!spec.getFacts().isEmpty()) {
            List<Map<String, String>> wireFacts = spec.getFacts().stream()
                    .map((Fact f) -> Map.of("title", f.title(), "value", f.value()))
                    .toList();
            Map<String, Object> factSet = new LinkedHashMap<>();
            factSet.put("type", "FactSet");
            factSet.put("separator", true);
            factSet.put("spacing", "Medium");
            factSet.put("facts", wireFacts);
            bodyElements.add(Collections.unmodifiableMap(factSet));
        }

        if (spec.getFooterText() != null && !spec.getFooterText().isBlank()) {
            bodyElements.add(Map.of(
                    "type", "TextBlock",
                    "text", spec.getFooterText(),
                    "size", "Small",
                    "isSubtle", true,
                    "wrap", true,
                    "spacing", "Medium"
            ));
        }

        List<Map<String, Object>> wireActions = spec.getActions().stream()
                .map((ActionButton btn) -> {
                    Map<String, Object> action = new LinkedHashMap<>();
                    action.put("type", "Action.OpenUrl");
                    action.put("title", btn.title());
                    action.put("url", btn.url());
                    return Collections.unmodifiableMap(action);
                })
                .toList();

        Map<String, Object> card = new LinkedHashMap<>();
        card.put("$schema", "http://adaptivecards.io/schemas/adaptive-card.json");
        card.put("type", "AdaptiveCard");
        card.put("version", "1.4");
        card.put("msteams", Map.of("width", "Full"));
        card.put("body", List.copyOf(bodyElements));
        card.put("actions", wireActions);
        return Collections.unmodifiableMap(card);
    }
}
