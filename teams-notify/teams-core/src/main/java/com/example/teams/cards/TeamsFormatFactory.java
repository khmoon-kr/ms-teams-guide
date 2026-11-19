package com.example.teams.cards;

import com.example.teams.cards.AdaptiveCardSpec.Fact;
import com.example.teams.cards.TeamsCardFactory.CardBuildResult;
import com.example.teams.domain.TeamsFormat;
import com.example.teams.security.TeamsLinkSigner;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.time.Clock;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 어댑티브 카드 외 5가지 대안 MS Teams 알림 포맷(Rich HTML, Activity Feed, Urgent+@Tag, Receipt Card, Hero Card)과
 * 서명된 유입 링크를 도메인 중립적으로 생성하는 순수 Java 팩토리입니다.
 */
public class TeamsFormatFactory {

    public record HtmlMessageSpec(
            String resourceId,
            String subject,
            String badgeTitle,
            String mentionDisplayName,
            String mentionUserId,
            String mentionEmail,
            String introText,
            List<Fact> rows,
            String linkLabel,
            String action
    ) {
        public HtmlMessageSpec {
            Objects.requireNonNull(resourceId, "resourceId must not be null");
            rows = rows != null ? List.copyOf(rows) : List.of();
            action = (action != null && !action.isBlank()) ? action : "view";
        }
    }

    public record ActivityFeedSpec(
            String resourceId,
            String topicLabel,
            String activityType,
            String recipientEmail,
            String previewText,
            Map<String, String> templateParameters,
            String action
    ) {
        public ActivityFeedSpec {
            Objects.requireNonNull(resourceId, "resourceId must not be null");
            templateParameters = templateParameters != null
                    ? Collections.unmodifiableMap(new LinkedHashMap<>(templateParameters))
                    : Map.of();
            action = (action != null && !action.isBlank()) ? action : "view";
        }
    }

    public record UrgentAlertSpec(
            String resourceId,
            String teamTagName,
            String teamTagId,
            String headline,
            List<Fact> details,
            String linkLabel,
            String action
    ) {
        public UrgentAlertSpec {
            Objects.requireNonNull(resourceId, "resourceId must not be null");
            details = details != null ? List.copyOf(details) : List.of();
            action = (action != null && !action.isBlank()) ? action : "view";
        }
    }

    public record ReceiptItem(String title, String subtitle, String price, String quantity) {
        public ReceiptItem {
            Objects.requireNonNull(title, "title must not be null");
        }
    }

    public record ReceiptCardSpec(
            String resourceId,
            String title,
            List<Fact> facts,
            List<ReceiptItem> items,
            String taxText,
            String totalText,
            String buttonTitle,
            String action
    ) {
        public ReceiptCardSpec {
            Objects.requireNonNull(resourceId, "resourceId must not be null");
            facts = facts != null ? List.copyOf(facts) : List.of();
            items = items != null ? List.copyOf(items) : List.of();
            action = (action != null && !action.isBlank()) ? action : "view";
        }
    }

    public record HeroCardSpec(
            String resourceId,
            String title,
            String subtitle,
            String text,
            String primaryButtonTitle,
            String primaryAction,
            String secondaryButtonTitle,
            String secondaryAction
    ) {
        public HeroCardSpec {
            Objects.requireNonNull(resourceId, "resourceId must not be null");
            primaryAction = (primaryAction != null && !primaryAction.isBlank()) ? primaryAction : "view";
        }
    }

    private final TeamsLinkSigner linkSigner;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public TeamsFormatFactory(TeamsLinkSigner linkSigner) {
        this(linkSigner, createDefaultObjectMapper(), Clock.systemUTC());
    }

    public TeamsFormatFactory(TeamsLinkSigner linkSigner, ObjectMapper objectMapper) {
        this(linkSigner, objectMapper, Clock.systemUTC());
    }

    public TeamsFormatFactory(TeamsLinkSigner linkSigner, ObjectMapper objectMapper, Clock clock) {
        this.linkSigner = Objects.requireNonNull(linkSigner, "linkSigner must not be null");
        this.objectMapper = objectMapper != null ? objectMapper : createDefaultObjectMapper();
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public FormatMessage buildHtmlMessage(HtmlMessageSpec spec) {
        Objects.requireNonNull(spec, "spec must not be null");
        String signedUrl = linkSigner.createSignedEntryUrl(spec.resourceId(), TeamsFormat.HTML_MESSAGE.name(), spec.action());
        String mentionName = (spec.mentionDisplayName() != null && !spec.mentionDisplayName().isBlank())
                ? spec.mentionDisplayName().trim()
                : "담당자";
        String email = (spec.mentionEmail() != null && !spec.mentionEmail().isBlank())
                ? spec.mentionEmail().trim()
                : "oncall@company.com";

        StringBuilder rowsHtml = new StringBuilder();
        for (Fact row : spec.rows()) {
            rowsHtml.append("    <tr><th align=\"left\">")
                    .append(escapeHtml(row.title()))
                    .append("</th><td>")
                    .append(escapeHtml(row.value()))
                    .append("</td></tr>\n");
        }

        String htmlContent = """
                <div>
                  <p><b>%s</b> <at id="0">%s</at>님, %s</p>
                  <table border="1" cellpadding="6" cellspacing="0" style="border-collapse:collapse; border-color:#d0d7de;">
                %s  </table>
                  <p style="margin-top:8px;">👉 <a href="%s">%s</a></p>
                </div>
                """.formatted(
                escapeHtml(spec.badgeTitle() != null ? spec.badgeTitle() : "[운영 알림]"),
                escapeHtml(mentionName),
                escapeHtml(spec.introText() != null ? spec.introText() : "새로운 이벤트가 도착했습니다."),
                rowsHtml.toString(),
                signedUrl,
                escapeHtml(spec.linkLabel() != null ? spec.linkLabel() : "Teams 인증 서명 링크 열기")
        ).trim();

        Map<String, Object> graphPayload = new LinkedHashMap<>();
        graphPayload.put("subject", spec.subject() != null ? spec.subject() : "[알림] " + spec.resourceId());
        graphPayload.put("importance", "normal");
        graphPayload.put("body", Map.of("contentType", "html", "content", htmlContent));
        graphPayload.put("mentions", List.of(Map.of(
                "id", 0,
                "mentionText", mentionName,
                "mentioned", Map.of("user", Map.of(
                        "userIdentityType", "aadUser",
                        "id", spec.mentionUserId() != null ? spec.mentionUserId() : "00000000-0000-0000-0000-000000000000",
                        "displayName", mentionName + " (" + email + ")"
                ))
        )));

        Map<String, Object> workflowsPayload = new LinkedHashMap<>();
        workflowsPayload.put("notificationFormat", TeamsFormat.HTML_MESSAGE.name());
        workflowsPayload.put("mentionEmail", email);
        workflowsPayload.put("resourceId", spec.resourceId());
        workflowsPayload.put("htmlBody", htmlContent.replace(
                "<at id=\"0\">" + escapeHtml(mentionName) + "</at>",
                "@{outputs('Get_an_@mention_token_for_a_user')?['body/atMention']}"
        ));
        workflowsPayload.put("signedEntryUrl", signedUrl);

        return createResponse(
                TeamsFormat.HTML_MESSAGE,
                spec.resourceId(),
                signedUrl,
                "Graph API: POST /chats/{chatId}/messages 또는 Workflows: Post message in a chat or channel",
                graphPayload,
                workflowsPayload
        );
    }

    public FormatMessage buildActivityFeed(ActivityFeedSpec spec) {
        Objects.requireNonNull(spec, "spec must not be null");
        String signedUrl = linkSigner.createSignedEntryUrl(spec.resourceId(), TeamsFormat.ACTIVITY_FEED.name(), spec.action());
        String email = (spec.recipientEmail() != null && !spec.recipientEmail().isBlank())
                ? spec.recipientEmail().trim()
                : "approver@company.com";
        String preview = (spec.previewText() != null && !spec.previewText().isBlank())
                ? spec.previewText().trim()
                : spec.resourceId() + " 확인 요청";

        List<Map<String, String>> templateParams = spec.templateParameters().entrySet().stream()
                .map(e -> Map.of("name", e.getKey(), "value", e.getValue()))
                .toList();

        Map<String, Object> graphActivityPayload = new LinkedHashMap<>();
        graphActivityPayload.put("topic", Map.of(
                "source", "text",
                "value", spec.topicLabel() != null ? spec.topicLabel() : "운영 센터 · " + spec.resourceId(),
                "webUrl", "https://teams.microsoft.com/l/entity/{teamsAppId}/opsTab?webUrl=" + signedUrl
        ));
        graphActivityPayload.put("activityType", spec.activityType() != null ? spec.activityType() : "approvalRequired");
        graphActivityPayload.put("previewText", Map.of("content", preview));
        graphActivityPayload.put("templateParameters", templateParams);

        Map<String, Object> workflowsFeedPayload = new LinkedHashMap<>();
        workflowsFeedPayload.put("workflowAction", "Post a feed notification (Microsoft Teams 표준 커넥터)");
        workflowsFeedPayload.put("postAs", "Flow bot");
        workflowsFeedPayload.put("notificationType", "Send to a chat / channel");
        workflowsFeedPayload.put("recipient", email);
        workflowsFeedPayload.put("customText", "🔔 " + preview);
        workflowsFeedPayload.put("signedEntryUrl", signedUrl);

        return createResponse(
                TeamsFormat.ACTIVITY_FEED,
                spec.resourceId(),
                signedUrl,
                "Graph API: POST /users/{userId}/teamwork/sendActivityNotification 또는 Workflows: Post a feed notification",
                graphActivityPayload,
                workflowsFeedPayload
        );
    }

    public FormatMessage buildUrgentAlert(UrgentAlertSpec spec) {
        Objects.requireNonNull(spec, "spec must not be null");
        String tagName = (spec.teamTagName() != null && !spec.teamTagName().isBlank())
                ? spec.teamTagName().trim()
                : "운영당직";
        String signedUrl = linkSigner.createSignedEntryUrl(spec.resourceId(), TeamsFormat.URGENT_TAG_MESSAGE.name(), spec.action());

        StringBuilder itemsHtml = new StringBuilder();
        for (Fact d : spec.details()) {
            itemsHtml.append("  <li><b>")
                    .append(escapeHtml(d.title()))
                    .append(":</b> ")
                    .append(escapeHtml(d.value()))
                    .append("</li>\n");
        }
        itemsHtml.append("  <li><b>조치 링크:</b> <a href=\"")
                .append(signedUrl)
                .append("\">")
                .append(escapeHtml(spec.linkLabel() != null ? spec.linkLabel() : "서명된 긴급 콘솔 바로 열기"))
                .append("</a></li>");

        String urgentHtml = """
                <p><b style="color:#b91c1c;">🚨 [긴급 · URGENT]</b> <at id="0">%s</at> %s</p>
                <ul>
                %s
                </ul>
                """.formatted(
                escapeHtml(tagName),
                escapeHtml(spec.headline() != null ? spec.headline() : "즉시 확인 바랍니다!"),
                itemsHtml.toString()
        ).trim();

        Map<String, Object> graphUrgentPayload = new LinkedHashMap<>();
        graphUrgentPayload.put("importance", "urgent");
        graphUrgentPayload.put("body", Map.of("contentType", "html", "content", urgentHtml));
        graphUrgentPayload.put("mentions", List.of(Map.of(
                "id", 0,
                "mentionText", tagName,
                "mentioned", Map.of("tag", Map.of(
                        "@odata.type", "#microsoft.graph.teamworkTagIdentity",
                        "id", spec.teamTagId() != null ? spec.teamTagId() : "tag-oncall-01",
                        "displayName", tagName
                ))
        )));

        Map<String, Object> workflowsTagPayload = new LinkedHashMap<>();
        workflowsTagPayload.put("step1_action", "Get an @mention token for a team tag (태그명: " + tagName + ")");
        workflowsTagPayload.put("step2_action", "Post message in a chat or channel");
        workflowsTagPayload.put("resourceId", spec.resourceId());
        workflowsTagPayload.put("signedEntryUrl", signedUrl);

        return createResponse(
                TeamsFormat.URGENT_TAG_MESSAGE,
                spec.resourceId(),
                signedUrl,
                "Graph API: POST /chats/{chatId}/messages (importance: \"urgent\") / 채널 @팀태그 멘션",
                graphUrgentPayload,
                workflowsTagPayload
        );
    }

    public FormatMessage buildReceiptCard(ReceiptCardSpec spec) {
        Objects.requireNonNull(spec, "spec must not be null");
        String signedUrl = linkSigner.createSignedEntryUrl(spec.resourceId(), TeamsFormat.RECEIPT_CARD.name(), spec.action());

        List<Map<String, String>> wireFacts = spec.facts().stream()
                .map(f -> Map.of("key", f.title(), "value", f.value()))
                .toList();
        List<Map<String, String>> wireItems = spec.items().stream()
                .map(it -> Map.of(
                        "title", it.title(),
                        "subtitle", it.subtitle() != null ? it.subtitle() : "",
                        "price", it.price() != null ? it.price() : "-",
                        "quantity", it.quantity() != null ? it.quantity() : "1"
                ))
                .toList();

        Map<String, Object> receiptContent = new LinkedHashMap<>();
        receiptContent.put("title", spec.title() != null ? spec.title() : "상세 명세서");
        receiptContent.put("facts", wireFacts);
        receiptContent.put("items", wireItems);
        receiptContent.put("tax", spec.taxText() != null ? spec.taxText() : "-");
        receiptContent.put("total", spec.totalText() != null ? spec.totalText() : "-");
        receiptContent.put("buttons", List.of(Map.of(
                "type", "openUrl",
                "title", spec.buttonTitle() != null ? spec.buttonTitle() : "상세 내역 열기 (Teams 서명됨)",
                "value", signedUrl
        )));

        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("type", "message");
        envelope.put("attachments", List.of(Map.of(
                "contentType", "application/vnd.microsoft.card.receipt",
                "content", receiptContent
        )));

        Map<String, Object> notePayload = Map.of(
                "note", "Workflows 기본 Post card 액션은 Adaptive Card 전용입니다. Receipt Card는 Teams Bot, Outgoing Webhook 응답, 또는 Graph API chatMessage.attachments로 전송합니다.",
                "graphAttachmentContentType", "application/vnd.microsoft.card.receipt",
                "signedEntryUrl", signedUrl
        );

        return createResponse(
                TeamsFormat.RECEIPT_CARD,
                spec.resourceId(),
                signedUrl,
                "Teams Bot / Outgoing Webhook 응답 또는 Graph API chatMessage (contentType: card.receipt)",
                envelope,
                notePayload
        );
    }

    public FormatMessage buildHeroCard(HeroCardSpec spec) {
        Objects.requireNonNull(spec, "spec must not be null");
        String signedPrimaryUrl = linkSigner.createSignedEntryUrl(
                spec.resourceId(), TeamsFormat.HERO_CARD.name(), spec.primaryAction());

        List<Map<String, String>> buttons;
        if (spec.secondaryButtonTitle() != null && !spec.secondaryButtonTitle().isBlank()) {
            String secAction = (spec.secondaryAction() != null && !spec.secondaryAction().isBlank())
                    ? spec.secondaryAction()
                    : "action";
            String signedSecondaryUrl = linkSigner.createSignedEntryUrl(
                    spec.resourceId(), TeamsFormat.HERO_CARD.name(), secAction);
            buttons = List.of(
                    Map.of("type", "openUrl", "title", spec.primaryButtonTitle(), "value", signedPrimaryUrl),
                    Map.of("type", "openUrl", "title", spec.secondaryButtonTitle(), "value", signedSecondaryUrl)
            );
        } else {
            buttons = List.of(Map.of(
                    "type", "openUrl",
                    "title", spec.primaryButtonTitle() != null ? spec.primaryButtonTitle() : "상세 보기",
                    "value", signedPrimaryUrl
            ));
        }

        Map<String, Object> heroContent = new LinkedHashMap<>();
        heroContent.put("title", spec.title() != null ? spec.title() : spec.resourceId());
        heroContent.put("subtitle", spec.subtitle() != null ? spec.subtitle() : "");
        heroContent.put("text", spec.text() != null ? spec.text() : "");
        heroContent.put("buttons", buttons);

        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("type", "message");
        envelope.put("attachments", List.of(Map.of(
                "contentType", "application/vnd.microsoft.card.hero",
                "content", heroContent
        )));

        Map<String, Object> notePayload = Map.of(
                "note", "Hero Card는 스키마 버전 관리 없이 title/subtitle/text/buttons 만으로 가장 가볍게 구성할 수 있는 내장 카드입니다.",
                "graphAttachmentContentType", "application/vnd.microsoft.card.hero",
                "signedEntryUrl", signedPrimaryUrl
        );

        return createResponse(
                TeamsFormat.HERO_CARD,
                spec.resourceId(),
                signedPrimaryUrl,
                "Teams Bot / Outgoing Webhook 응답 또는 Graph API chatMessage (contentType: card.hero)",
                envelope,
                notePayload
        );
    }

    public FormatMessage buildAdaptiveCardFormat(String resourceId, CardBuildResult builtCard) {
        Objects.requireNonNull(resourceId, "resourceId must not be null");
        Objects.requireNonNull(builtCard, "builtCard must not be null");
        return createResponse(
                TeamsFormat.ADAPTIVE_CARD,
                resourceId,
                builtCard.signedDetailUrl(),
                "Workflows Incoming Webhook / Graph API / Teams Bot 전체 호환 표준",
                builtCard.teamsEnvelope(),
                builtCard.teamsEnvelope()
        );
    }

    private FormatMessage createResponse(
            TeamsFormat format,
            String resourceId,
            String signedUrl,
            String endpointHint,
            Map<String, Object> primary,
            Map<String, Object> workflows
    ) {
        return new FormatMessage(
                format,
                format.getDisplayName(),
                format.getSupportedChannels(),
                format.getRecommendedUseCase(),
                resourceId,
                signedUrl,
                endpointHint,
                Instant.now(clock),
                primary,
                workflows,
                toPrettyJson(primary),
                toPrettyJson(workflows)
        );
    }

    private String toPrettyJson(Object obj) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return String.valueOf(obj);
        }
    }

    private static String escapeHtml(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static ObjectMapper createDefaultObjectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
}
