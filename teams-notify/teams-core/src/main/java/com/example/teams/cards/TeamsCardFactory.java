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
 * {@link AdaptiveCardSpec}을 입력받아 MS Teams Adaptive Card v1.4 및 Workflows Webhook 봉투(Envelope)를
 * 불변 구조로 조립하는 도메인 중립 카드 팩토리입니다.
 */
public class TeamsCardFactory {

    private static final DateTimeFormatter DEFAULT_TS_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z");

    public record CardBuildResult(
            String eventType,
            String summaryText,
            String signedDetailUrl,
            String signedActionUrl,
            Map<String, Object> teamsEnvelope
    ) {
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

    public TeamsCardFactory(TeamsLinkSigner linkSigner) {
        this(linkSigner, ZoneId.of("Asia/Seoul"));
    }

    public TeamsCardFactory(TeamsLinkSigner linkSigner, ZoneId displayZone) {
        this.linkSigner = Objects.requireNonNull(linkSigner, "linkSigner must not be null");
        this.displayZone = displayZone != null ? displayZone : ZoneId.of("Asia/Seoul");
    }

    /**
     * 설정된 기본 진입 경로(entryPathTemplate)에 서명을 부여한 카드 액션 버튼을 생성합니다.
     */
    public ActionButton signedEntryButton(String title, String resourceId, String cardType, String action) {
        String signedUrl = linkSigner.createSignedEntryUrl(resourceId, cardType, action);
        return new ActionButton(title, signedUrl);
    }

    /**
     * 임의의 외부 URL에 서명을 부여한 카드 액션 버튼을 생성합니다.
     */
    public ActionButton signedCustomButton(String title, String targetUrl, String resourceId, String cardType, String action) {
        String signedUrl = linkSigner.signCustomUrl(targetUrl, resourceId, cardType, action);
        return new ActionButton(title, signedUrl);
    }

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

    public String formatTimestamp(Instant instant) {
        return formatTimestamp(instant, displayZone);
    }

    public static String formatTimestamp(Instant instant, ZoneId zone) {
        if (instant == null) {
            return "-";
        }
        ZoneId targetZone = zone != null ? zone : ZoneId.of("UTC");
        return DEFAULT_TS_FMT.format(instant.atZone(targetZone));
    }

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
