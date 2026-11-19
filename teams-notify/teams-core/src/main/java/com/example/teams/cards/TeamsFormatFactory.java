package com.example.teams.cards;

import com.example.teams.domain.TransactionView;
import com.example.teams.domain.TeamsFormat;
import com.example.teams.dto.FormatShowcaseResponse;
import com.example.teams.security.TeamsLinkSigner;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 어댑티브 카드 외 5가지 대안 MS Teams 알림 포맷(Rich HTML, Activity Feed, Urgent+@Tag, Receipt Card, Hero Card)과
 * 서명된 유입 링크를 생성하는 순수 Java 멀티 포맷 팩토리입니다.
 */
public class TeamsFormatFactory {

    private final TeamsLinkSigner linkSigner;
    private final TeamsCardFactory adaptiveCardFactory;
    private final ObjectMapper objectMapper;

    public TeamsFormatFactory(
            TeamsLinkSigner linkSigner,
            TeamsCardFactory adaptiveCardFactory
    ) {
        this(linkSigner, adaptiveCardFactory, createDefaultObjectMapper());
    }

    public TeamsFormatFactory(
            TeamsLinkSigner linkSigner,
            TeamsCardFactory adaptiveCardFactory,
            ObjectMapper objectMapper
    ) {
        this.linkSigner = Objects.requireNonNull(linkSigner, "linkSigner must not be null");
        this.adaptiveCardFactory = Objects.requireNonNull(adaptiveCardFactory, "adaptiveCardFactory must not be null");
        this.objectMapper = objectMapper != null ? objectMapper : createDefaultObjectMapper();
    }

    public FormatShowcaseResponse buildHtmlMessage(TransactionView tx, String recipientEmail, String customNote) {
        String email = (recipientEmail != null && !recipientEmail.isBlank()) ? recipientEmail.trim() : "billing-oncall@company.com";
        String note = (customNote != null && !customNote.isBlank()) ? customNote.trim() : "정산 대사 대상 결제 건입니다.";
        String signedUrl = linkSigner.createSignedTransactionUrl(tx.getTxId(), TeamsFormat.HTML_MESSAGE.name(), "view");

        String htmlContent = """
                <div>
                  <p><b>💳 [빌링 알림]</b> <at id="0">결제담당자</at>님, 신규 거래 내역이 도착했습니다. (%s)</p>
                  <table border="1" cellpadding="6" cellspacing="0" style="border-collapse:collapse; border-color:#d0d7de;">
                    <tr><th align="left">거래 ID / 주문번호</th><td><code>%s</code> / %s</td></tr>
                    <tr><th align="left">가맹점</th><td>%s</td></tr>
                    <tr><th align="left">결제 금액 / 상태</th><td><b>%s</b> (%s)</td></tr>
                    <tr><th align="left">결제 시각</th><td>%s</td></tr>
                  </table>
                  <p style="margin-top:8px;">👉 <a href="%s">Teams 인증 서명 링크로 원장 열기</a></p>
                </div>
                """.formatted(
                note,
                tx.getTxId(),
                tx.getOrderId(),
                tx.getMerchantName(),
                TeamsCardFactory.formatKrw(tx.getAmount()),
                tx.getStatus().getLabel(),
                TeamsCardFactory.formatKst(tx.getApprovedAt()),
                signedUrl
        ).trim();

        Map<String, Object> graphPayload = new LinkedHashMap<>();
        graphPayload.put("subject", "[빌링 알림] " + tx.getMerchantName());
        graphPayload.put("importance", "normal");
        graphPayload.put("body", Map.of(
                "contentType", "html",
                "content", htmlContent
        ));
        graphPayload.put("mentions", List.of(Map.of(
                "id", 0,
                "mentionText", "결제담당자",
                "mentioned", Map.of("user", Map.of(
                        "userIdentityType", "aadUser",
                        "id", "87d349ed-44d7-43e1-9a83-5f2406dee5bd",
                        "displayName", "결제담당자 (" + email + ")"
                ))
        )));

        Map<String, Object> workflowsPayload = new LinkedHashMap<>();
        workflowsPayload.put("notificationFormat", "HTML_MESSAGE");
        workflowsPayload.put("mentionEmail", email);
        workflowsPayload.put("txId", tx.getTxId());
        workflowsPayload.put("htmlBody", htmlContent.replace("<at id=\"0\">결제담당자</at>", "@{outputs('Get_an_@mention_token_for_a_user')?['body/atMention']}"));
        workflowsPayload.put("signedEntryUrl", signedUrl);

        return createResponse(
                TeamsFormat.HTML_MESSAGE,
                tx.getTxId(),
                signedUrl,
                "Graph API: POST /chats/{chatId}/messages 또는 Workflows: Post message in a chat or channel",
                graphPayload,
                workflowsPayload
        );
    }

    public FormatShowcaseResponse buildActivityFeedAlert(TransactionView tx, String recipientEmail, String customNote) {
        String email = (recipientEmail != null && !recipientEmail.isBlank()) ? recipientEmail.trim() : "approver@company.com";
        String preview = (customNote != null && !customNote.isBlank())
                ? customNote.trim()
                : String.format("%s %s 환불/정산 확인 필요 (%s)",
                        tx.getMerchantName(), TeamsCardFactory.formatKrw(tx.getAmount()), tx.getTxId());
        String signedUrl = linkSigner.createSignedTransactionUrl(tx.getTxId(), TeamsFormat.ACTIVITY_FEED.name(), "refund");

        Map<String, Object> graphActivityPayload = new LinkedHashMap<>();
        graphActivityPayload.put("topic", Map.of(
                "source", "text",
                "value", "빌링 운영 센터 · " + tx.getTxId(),
                "webUrl", "https://teams.microsoft.com/l/entity/{teamsAppId}/billingOpsTab?webUrl=" + signedUrl
        ));
        graphActivityPayload.put("activityType", "billingApprovalRequired");
        graphActivityPayload.put("previewText", Map.of("content", preview));
        graphActivityPayload.put("templateParameters", List.of(
                Map.of("name", "merchantName", "value", tx.getMerchantName()),
                Map.of("name", "amount", "value", TeamsCardFactory.formatKrw(tx.getAmount()))
        ));

        Map<String, Object> workflowsFeedPayload = new LinkedHashMap<>();
        workflowsFeedPayload.put("workflowAction", "Post a feed notification (Microsoft Teams 표준 커넥터)");
        workflowsFeedPayload.put("postAs", "Flow bot");
        workflowsFeedPayload.put("notificationType", "Send to a chat / channel");
        workflowsFeedPayload.put("recipient", email);
        workflowsFeedPayload.put("customText", "🔔 [빌링 긴급 확인] " + preview);
        workflowsFeedPayload.put("signedEntryUrl", signedUrl);

        return createResponse(
                TeamsFormat.ACTIVITY_FEED,
                tx.getTxId(),
                signedUrl,
                "Graph API: POST /users/{userId}/teamwork/sendActivityNotification 또는 Workflows: Post a feed notification",
                graphActivityPayload,
                workflowsFeedPayload
        );
    }

    public FormatShowcaseResponse buildUrgentTagAlert(TransactionView tx, String teamTagName, String customNote) {
        String tagName = (teamTagName != null && !teamTagName.isBlank()) ? teamTagName.trim() : "결제운영당직";
        String reason = (customNote != null && !customNote.isBlank())
                ? customNote.trim()
                : (tx.getLastFailureReason() != null ? tx.getLastFailureReason() : "PG 매입 응답 지연 임계치 초과");
        String signedUrl = linkSigner.createSignedTransactionUrl(tx.getTxId(), TeamsFormat.URGENT_TAG_MESSAGE.name(), "capture");

        String urgentHtml = """
                <p><b style="color:#b91c1c;">🚨 [긴급 · URGENT]</b> <at id="0">%s</at> 즉시 확인 바랍니다!</p>
                <ul>
                  <li><b>대상 거래:</b> <code>%s</code> (%s · <b>%s</b>)</li>
                  <li><b>경보 사유:</b> %s</li>
                  <li><b>조치 링크:</b> <a href="%s">서명된 긴급 운영 콘솔 바로 열기</a></li>
                </ul>
                """.formatted(
                tagName,
                tx.getTxId(),
                tx.getMerchantName(),
                TeamsCardFactory.formatKrw(tx.getAmount()),
                reason,
                signedUrl
        ).trim();

        Map<String, Object> graphUrgentPayload = new LinkedHashMap<>();
        graphUrgentPayload.put("importance", "urgent");
        graphUrgentPayload.put("body", Map.of(
                "contentType", "html",
                "content", urgentHtml
        ));
        graphUrgentPayload.put("mentions", List.of(Map.of(
                "id", 0,
                "mentionText", tagName,
                "mentioned", Map.of("tag", Map.of(
                        "@odata.type", "#microsoft.graph.teamworkTagIdentity",
                        "id", "tag-billing-oncall-01",
                        "displayName", tagName
                ))
        )));

        Map<String, Object> workflowsTagPayload = new LinkedHashMap<>();
        workflowsTagPayload.put("step1_action", "Get an @mention token for a team tag (태그명: " + tagName + ")");
        workflowsTagPayload.put("step2_action", "Post message in a chat or channel");
        workflowsTagPayload.put("txId", tx.getTxId());
        workflowsTagPayload.put("alertReason", reason);
        workflowsTagPayload.put("signedEntryUrl", signedUrl);

        return createResponse(
                TeamsFormat.URGENT_TAG_MESSAGE,
                tx.getTxId(),
                signedUrl,
                "Graph API: POST /chats/{chatId}/messages (importance: \"urgent\") / 채널 @팀태그 멘션",
                graphUrgentPayload,
                workflowsTagPayload
        );
    }

    public FormatShowcaseResponse buildReceiptCard(TransactionView tx) {
        String signedUrl = linkSigner.createSignedTransactionUrl(tx.getTxId(), TeamsFormat.RECEIPT_CARD.name(), "view");
        long supplyAmount = Math.round(tx.getAmount() / 1.1);
        long vatAmount = tx.getAmount() - supplyAmount;

        Map<String, Object> receiptContent = new LinkedHashMap<>();
        receiptContent.put("title", tx.getMerchantName() + " 결제 영수증");
        receiptContent.put("facts", List.of(
                Map.of("key", "거래 ID", "value", tx.getTxId()),
                Map.of("key", "주문번호", "value", tx.getOrderId()),
                Map.of("key", "결제수단", "value", tx.getPaymentMethod()),
                Map.of("key", "승인번호", "value", tx.getAuthCode()),
                Map.of("key", "결제 시각", "value", TeamsCardFactory.formatKst(tx.getApprovedAt()))
        ));
        receiptContent.put("items", List.of(
                Map.of(
                        "title", tx.getMerchantName(),
                        "subtitle", "청구 주기: 월간 정기 구독 (공급가액)",
                        "price", TeamsCardFactory.formatKrw(supplyAmount),
                        "quantity", "1"
                )
        ));
        receiptContent.put("tax", TeamsCardFactory.formatKrw(vatAmount));
        receiptContent.put("total", TeamsCardFactory.formatKrw(tx.getAmount()));
        receiptContent.put("buttons", List.of(
                Map.of(
                        "type", "openUrl",
                        "title", "영수증 원장 · 환불 콘솔 열기 (Teams 서명됨)",
                        "value", signedUrl
                )
        ));

        Map<String, Object> botOrOutgoingEnvelope = new LinkedHashMap<>();
        botOrOutgoingEnvelope.put("type", "message");
        botOrOutgoingEnvelope.put("attachments", List.of(Map.of(
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
                tx.getTxId(),
                signedUrl,
                "Teams Bot / Outgoing Webhook 응답 또는 Graph API chatMessage (contentType: card.receipt)",
                botOrOutgoingEnvelope,
                notePayload
        );
    }

    public FormatShowcaseResponse buildHeroCard(TransactionView tx) {
        String signedViewUrl = linkSigner.createSignedTransactionUrl(tx.getTxId(), TeamsFormat.HERO_CARD.name(), "view");
        String signedRefundUrl = linkSigner.createSignedTransactionUrl(tx.getTxId(), TeamsFormat.HERO_CARD.name(), "refund");

        Map<String, Object> heroContent = new LinkedHashMap<>();
        heroContent.put("title", "💳 " + TeamsCardFactory.formatKrw(tx.getAmount()) + " · " + tx.getStatus().getLabel());
        heroContent.put("subtitle", tx.getMerchantName() + " | " + tx.getTxId() + " (" + tx.getOrderId() + ")");
        heroContent.put("text", "결제수단: " + tx.getPaymentMethod()
                + " · 잔액: " + TeamsCardFactory.formatKrw(tx.getRemainingAmount())
                + " · 승인시각: " + TeamsCardFactory.formatKst(tx.getApprovedAt()));
        heroContent.put("buttons", List.of(
                Map.of("type", "openUrl", "title", "상세 원장 보기 (서명 링크)", "value", signedViewUrl),
                Map.of("type", "openUrl", "title", "환불 처리 화면", "value", signedRefundUrl)
        ));

        Map<String, Object> botOrOutgoingEnvelope = new LinkedHashMap<>();
        botOrOutgoingEnvelope.put("type", "message");
        botOrOutgoingEnvelope.put("attachments", List.of(Map.of(
                "contentType", "application/vnd.microsoft.card.hero",
                "content", heroContent
        )));

        Map<String, Object> notePayload = Map.of(
                "note", "Hero Card는 스키마 버전 관리 없이 title/subtitle/text/buttons 만으로 가장 가볍게 구성할 수 있는 내장 카드입니다.",
                "graphAttachmentContentType", "application/vnd.microsoft.card.hero",
                "signedEntryUrl", signedViewUrl
        );

        return createResponse(
                TeamsFormat.HERO_CARD,
                tx.getTxId(),
                signedViewUrl,
                "Teams Bot / Outgoing Webhook 응답 또는 Graph API chatMessage (contentType: card.hero)",
                botOrOutgoingEnvelope,
                notePayload
        );
    }

    public FormatShowcaseResponse buildAdaptiveCardComparison(TransactionView tx) {
        var built = adaptiveCardFactory.buildPaymentAuthorizedCard(tx);
        return createResponse(
                TeamsFormat.ADAPTIVE_CARD,
                tx.getTxId(),
                built.signedDetailUrl(),
                "Workflows Incoming Webhook / Graph API / Teams Bot 전체 호환 표준",
                built.teamsEnvelope(),
                built.teamsEnvelope()
        );
    }

    private FormatShowcaseResponse createResponse(
            TeamsFormat format,
            String txId,
            String signedUrl,
            String endpointHint,
            Map<String, Object> primary,
            Map<String, Object> workflows
    ) {
        return new FormatShowcaseResponse(
                format,
                format.getDisplayName(),
                format.getSupportedChannels(),
                format.getRecommendedUseCase(),
                txId,
                signedUrl,
                endpointHint,
                Instant.now(),
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

    private static ObjectMapper createDefaultObjectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
}
