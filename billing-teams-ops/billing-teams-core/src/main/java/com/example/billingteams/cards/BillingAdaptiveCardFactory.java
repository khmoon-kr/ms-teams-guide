package com.example.billingteams.cards;

import com.example.billingteams.domain.BillingEventType;
import com.example.billingteams.domain.BillingTransactionView;
import com.example.billingteams.security.TeamsLinkSigner;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 빌링 도메인 이벤트(승인·매입·환불·조회) 및 커스텀 운영 알림을 MS Teams Adaptive Card v1.4 규격으로 생성하는 순수 Java 팩토리.
 */
public class BillingAdaptiveCardFactory {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter KST_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'KST'");

    public record CardBuildResult(
            BillingEventType eventType,
            String summaryText,
            String signedDetailUrl,
            String signedActionUrl,
            Map<String, Object> teamsEnvelope
    ) {}

    private final TeamsLinkSigner linkSigner;

    public BillingAdaptiveCardFactory(TeamsLinkSigner linkSigner) {
        this.linkSigner = Objects.requireNonNull(linkSigner, "linkSigner must not be null");
    }

    public CardBuildResult buildPaymentAuthorizedCard(BillingTransactionView tx) {
        String detailUrl = linkSigner.createSignedTransactionUrl(tx.getTxId(), BillingEventType.PAYMENT_AUTHORIZED.name(), "view");
        String refundUrl = linkSigner.createSignedTransactionUrl(tx.getTxId(), BillingEventType.PAYMENT_AUTHORIZED.name(), "refund");

        List<Map<String, String>> facts = List.of(
                fact("거래 ID", tx.getTxId()),
                fact("주문번호", tx.getOrderId()),
                fact("가맹점 / 서비스", tx.getMerchantName()),
                fact("결제수단", tx.getPaymentMethod()),
                fact("승인번호", tx.getAuthCode()),
                fact("결제 시각", formatKst(tx.getApprovedAt()))
        );

        List<Map<String, Object>> actions = List.of(
                openUrlAction("거래 상세 · 매입/환불 콘솔", detailUrl),
                openUrlAction("환불 처리 바로가기", refundUrl)
        );

        Map<String, Object> card = buildCardDocument(
                "emphasis",
                "PAYMENT AUTHORIZED · 결제 승인",
                formatKrw(tx.getAmount()),
                "Accent",
                tx.getMerchantName() + " 신규 결제가 정상 승인되었습니다.",
                facts,
                actions
        );

        return new CardBuildResult(
                BillingEventType.PAYMENT_AUTHORIZED,
                String.format("[결제 승인] %s · %s (%s)", tx.getMerchantName(), formatKrw(tx.getAmount()), tx.getTxId()),
                detailUrl,
                refundUrl,
                wrapTeamsWebhookEnvelope(card)
        );
    }

    public CardBuildResult buildCaptureResultCard(BillingTransactionView tx, boolean success, Instant processedAt) {
        BillingEventType eventType = success ? BillingEventType.CAPTURE_SUCCEEDED : BillingEventType.CAPTURE_FAILED;
        String detailUrl = linkSigner.createSignedTransactionUrl(tx.getTxId(), eventType.name(), "view");
        String actionUrl = linkSigner.createSignedTransactionUrl(tx.getTxId(), eventType.name(), success ? "refund" : "capture");

        List<Map<String, String>> facts = new ArrayList<>();
        facts.add(fact("거래 ID", tx.getTxId()));
        facts.add(fact("주문번호", tx.getOrderId()));
        facts.add(fact("매입 대상 금액", formatKrw(tx.getAmount())));
        facts.add(fact("결제수단", tx.getPaymentMethod()));
        facts.add(fact("처리 시각", formatKst(processedAt)));
        if (!success && tx.getLastFailureReason() != null) {
            facts.add(fact("실패 사유", tx.getLastFailureReason()));
        }

        List<Map<String, Object>> actions = success
                ? List.of(
                        openUrlAction("거래 상세 보기", detailUrl),
                        openUrlAction("환불 처리 화면", actionUrl)
                )
                : List.of(
                        openUrlAction("수동 재매입 · 원인 확인", actionUrl),
                        openUrlAction("거래 상세 보기", detailUrl)
                );

        Map<String, Object> card = buildCardDocument(
                success ? "good" : "attention",
                success ? "CAPTURE COMPLETED · 매입 완료" : "CAPTURE FAILED · 매입 실패 경보",
                formatKrw(tx.getAmount()),
                success ? "Good" : "Attention",
                success
                        ? "PG 매입 확정이 완료되어 정산 예정 상태로 전환되었습니다."
                        : "PG 매입 요청이 실패했습니다. 서명된 운영 링크를 통해 원인을 확인하고 수동 재매입을 진행하세요.",
                facts,
                actions
        );

        return new CardBuildResult(
                eventType,
                String.format("[%s] %s · %s (%s)",
                        success ? "매입 완료" : "매입 실패", tx.getMerchantName(), formatKrw(tx.getAmount()), tx.getTxId()),
                detailUrl,
                actionUrl,
                wrapTeamsWebhookEnvelope(card)
        );
    }

    public CardBuildResult buildRefundCard(
            BillingTransactionView tx,
            BillingEventType eventType,
            long targetRefundAmount,
            String reason,
            String actor
    ) {
        String detailUrl = linkSigner.createSignedTransactionUrl(tx.getTxId(), eventType.name(), "view");
        String refundActionUrl = linkSigner.createSignedTransactionUrl(tx.getTxId(), eventType.name(), "refund");

        String containerStyle;
        String badgeText;
        String amountColor;
        String description;

        if (eventType == BillingEventType.REFUND_REQUESTED) {
            containerStyle = "warning";
            badgeText = "REFUND APPROVAL REQUIRED · 환불 승인 대기";
            amountColor = "Warning";
            description = "환불 승인 대기 건이 접수되었습니다. 아래 인증 링크로 진입하여 승인 또는 반려를 확정하세요.";
        } else if (eventType == BillingEventType.REFUND_REJECTED) {
            containerStyle = "emphasis";
            badgeText = "REFUND REJECTED · 환불 반려";
            amountColor = "Default";
            description = "요청된 환불 건이 반려 처리되어 기존 결제 상태가 유지됩니다.";
        } else {
            containerStyle = "good";
            badgeText = "REFUND COMPLETED · 환불 완료";
            amountColor = "Good";
            description = "환불 처리가 정상 완료되었습니다.";
        }

        List<Map<String, String>> facts = List.of(
                fact("거래 ID", tx.getTxId()),
                fact("주문번호", tx.getOrderId()),
                fact("원결제 금액", formatKrw(tx.getAmount())),
                fact(eventType == BillingEventType.REFUND_REQUESTED ? "환불 요청 금액" : "이번 처리 금액", formatKrw(targetRefundAmount)),
                fact("환불 후 잔액", formatKrw(tx.getRemainingAmount())),
                fact("담당 / 요청자", actor != null ? actor : "-"),
                fact("사유 / 코멘트", reason != null ? reason : "-"),
                fact("시각", formatKst(Instant.now()))
        );

        List<Map<String, Object>> actions = (eventType == BillingEventType.REFUND_REQUESTED)
                ? List.of(
                        openUrlAction("Teams 인증 링크로 환불 승인/반려", refundActionUrl),
                        openUrlAction("거래 원장 조회", detailUrl)
                )
                : List.of(
                        openUrlAction("거래 원장 상세 확인", detailUrl)
                );

        Map<String, Object> card = buildCardDocument(
                containerStyle,
                badgeText,
                formatKrw(targetRefundAmount),
                amountColor,
                description,
                facts,
                actions
        );

        return new CardBuildResult(
                eventType,
                String.format("[%s] %s · %s (%s)", eventType.getTitle(), tx.getMerchantName(), formatKrw(targetRefundAmount), tx.getTxId()),
                detailUrl,
                refundActionUrl,
                wrapTeamsWebhookEnvelope(card)
        );
    }

    public CardBuildResult buildTransactionInquiryCard(BillingTransactionView tx) {
        String detailUrl = linkSigner.createSignedTransactionUrl(tx.getTxId(), BillingEventType.TRANSACTION_INQUIRY.name(), "view");
        String refundUrl = linkSigner.createSignedTransactionUrl(tx.getTxId(), BillingEventType.TRANSACTION_INQUIRY.name(), "refund");

        List<Map<String, String>> facts = List.of(
                fact("현재 상태", tx.getStatus().getLabel() + " (" + tx.getStatus().name() + ")"),
                fact("거래 ID / 주문번호", tx.getTxId() + " / " + tx.getOrderId()),
                fact("가맹점", tx.getMerchantName()),
                fact("승인 금액 / 잔액", formatKrw(tx.getAmount()) + " / 잔액 " + formatKrw(tx.getRemainingAmount())),
                fact("누적 환불액", formatKrw(tx.getRefundedAmount())),
                fact("승인 시각", formatKst(tx.getApprovedAt()))
        );

        Map<String, Object> card = buildCardDocument(
                "emphasis",
                "BILLING LOOKUP · 거래 실시간 조회",
                formatKrw(tx.getRemainingAmount()),
                "Accent",
                "요청하신 거래의 실시간 원장 상태입니다.",
                facts,
                List.of(
                        openUrlAction("운영 콘솔에서 열기", detailUrl),
                        openUrlAction("환불/매입 처리", refundUrl)
                )
        );

        return new CardBuildResult(
                BillingEventType.TRANSACTION_INQUIRY,
                String.format("[거래 조회] %s · %s", tx.getTxId(), tx.getStatus().getLabel()),
                detailUrl,
                refundUrl,
                wrapTeamsWebhookEnvelope(card)
        );
    }

    /**
     * 외부 서비스에서 빌링 외 일반 운영/정산 알림 카드를 동일한 디자인 시스템으로 직접 조립할 수 있도록 공개된 빌더 메서드입니다.
     */
    public Map<String, Object> buildCardDocument(
            String bannerStyle,
            String badgeLabel,
            String headlineAmount,
            String amountColor,
            String subtitle,
            List<Map<String, String>> facts,
            List<Map<String, Object>> actions
    ) {
        Map<String, Object> headerContainer = new LinkedHashMap<>();
        headerContainer.put("type", "Container");
        headerContainer.put("style", bannerStyle);
        headerContainer.put("bleed", true);
        headerContainer.put("items", List.of(
                Map.of(
                        "type", "TextBlock",
                        "text", badgeLabel,
                        "weight", "Bolder",
                        "size", "Small",
                        "wrap", true
                ),
                Map.of(
                        "type", "TextBlock",
                        "text", headlineAmount,
                        "weight", "Bolder",
                        "size", "ExtraLarge",
                        "color", amountColor,
                        "spacing", "None"
                ),
                Map.of(
                        "type", "TextBlock",
                        "text", subtitle,
                        "isSubtle", true,
                        "wrap", true,
                        "spacing", "Small"
                )
        ));

        Map<String, Object> factSet = new LinkedHashMap<>();
        factSet.put("type", "FactSet");
        factSet.put("separator", true);
        factSet.put("spacing", "Medium");
        factSet.put("facts", facts);

        Map<String, Object> footerNote = Map.of(
                "type", "TextBlock",
                "text", "🔒 하단 버튼 링크에는 MS Teams 유입 검증용 HMAC-SHA256 서명 및 만료 시각이 포함되어 있습니다.",
                "size", "Small",
                "isSubtle", true,
                "wrap", true,
                "spacing", "Medium"
        );

        Map<String, Object> card = new LinkedHashMap<>();
        card.put("$schema", "http://adaptivecards.io/schemas/adaptive-card.json");
        card.put("type", "AdaptiveCard");
        card.put("version", "1.4");
        card.put("msteams", Map.of("width", "Full"));
        card.put("body", List.of(headerContainer, factSet, footerNote));
        card.put("actions", actions);
        return card;
    }

    public Map<String, Object> wrapTeamsWebhookEnvelope(Map<String, Object> adaptiveCard) {
        Map<String, Object> attachment = new LinkedHashMap<>();
        attachment.put("contentType", "application/vnd.microsoft.card.adaptive");
        attachment.put("contentUrl", null);
        attachment.put("content", adaptiveCard);

        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("type", "message");
        envelope.put("attachments", List.of(attachment));
        return envelope;
    }

    public static Map<String, String> fact(String title, String value) {
        Map<String, String> item = new LinkedHashMap<>();
        item.put("title", title);
        item.put("value", value != null ? value : "-");
        return item;
    }

    public static Map<String, Object> openUrlAction(String title, String url) {
        Map<String, Object> action = new LinkedHashMap<>();
        action.put("type", "Action.OpenUrl");
        action.put("title", title);
        action.put("url", url);
        return action;
    }

    public static String formatKrw(long amount) {
        return String.format("₩%,d", amount);
    }

    public static String formatKst(Instant instant) {
        if (instant == null) {
            return "-";
        }
        return KST_FMT.format(instant.atZone(KST));
    }
}
