package com.example.teams.service;

import com.example.teams.TeamsNotifier;
import com.example.teams.cards.AdaptiveCardSpec;
import com.example.teams.cards.AdaptiveCardSpec.BannerStyle;
import com.example.teams.cards.AdaptiveCardSpec.Fact;
import com.example.teams.cards.AdaptiveCardSpec.TextColor;
import com.example.teams.cards.FormatMessage;
import com.example.teams.cards.TeamsCardFactory;
import com.example.teams.cards.TeamsCardFactory.CardBuildResult;
import com.example.teams.cards.TeamsFormatFactory.ActivityFeedSpec;
import com.example.teams.cards.TeamsFormatFactory.HeroCardSpec;
import com.example.teams.cards.TeamsFormatFactory.HtmlMessageSpec;
import com.example.teams.cards.TeamsFormatFactory.ReceiptCardSpec;
import com.example.teams.cards.TeamsFormatFactory.ReceiptItem;
import com.example.teams.cards.TeamsFormatFactory.UrgentAlertSpec;
import com.example.teams.domain.BillingEventType;
import com.example.teams.domain.PaymentTransaction;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 데모 애플리케이션(빌링 도메인)이 범용 {@link TeamsNotifier} 라이브러리의 선언적 Spec API를 사용해
 * 자체 도메인 알림(Adaptive Card 및 6종 포맷)을 어떻게 구성하는지 보여주는 레퍼런스 컴포넌트입니다.
 */
@Component
public class BillingTeamsComposer {

    private final TeamsNotifier teams;

    public BillingTeamsComposer(TeamsNotifier teams) {
        this.teams = teams;
    }

    public CardBuildResult composeAuthorizedCard(PaymentTransaction tx) {
        String event = BillingEventType.PAYMENT_AUTHORIZED.name();
        AdaptiveCardSpec spec = AdaptiveCardSpec.builder()
                .eventType(event)
                .summaryText(String.format("[결제 승인] %s · %s (%s)",
                        tx.getMerchantName(), TeamsCardFactory.formatCurrencyKrw(tx.getAmount()), tx.getTxId()))
                .bannerStyle(BannerStyle.EMPHASIS)
                .badgeLabel("PAYMENT AUTHORIZED · 결제 승인")
                .headline(TeamsCardFactory.formatCurrencyKrw(tx.getAmount()))
                .headlineColor(TextColor.ACCENT)
                .subtitle(tx.getMerchantName() + " 신규 결제가 정상 승인되었습니다.")
                .addFact("거래 ID", tx.getTxId())
                .addFact("주문번호", tx.getOrderId())
                .addFact("가맹점 / 서비스", tx.getMerchantName())
                .addFact("결제수단", tx.getPaymentMethod())
                .addFact("승인번호", tx.getAuthCode())
                .addFact("결제 시각", teams.cards().formatTimestamp(tx.getApprovedAt()))
                .addAction(teams.cards().signedEntryButton("거래 상세 · 매입/환불 콘솔", tx.getTxId(), event, "view"))
                .addAction(teams.cards().signedEntryButton("환불 처리 바로가기", tx.getTxId(), event, "refund"))
                .build();
        return teams.cards().buildCard(spec);
    }

    public CardBuildResult composeCaptureCard(PaymentTransaction tx, boolean success, Instant processedAt) {
        BillingEventType eventType = success ? BillingEventType.CAPTURE_SUCCEEDED : BillingEventType.CAPTURE_FAILED;
        String event = eventType.name();

        AdaptiveCardSpec.Builder builder = AdaptiveCardSpec.builder()
                .eventType(event)
                .summaryText(String.format("[%s] %s · %s (%s)",
                        success ? "매입 완료" : "매입 실패",
                        tx.getMerchantName(),
                        TeamsCardFactory.formatCurrencyKrw(tx.getAmount()),
                        tx.getTxId()))
                .bannerStyle(success ? BannerStyle.GOOD : BannerStyle.ATTENTION)
                .badgeLabel(success ? "CAPTURE COMPLETED · 매입 완료" : "CAPTURE FAILED · 매입 실패 경보")
                .headline(TeamsCardFactory.formatCurrencyKrw(tx.getAmount()))
                .headlineColor(success ? TextColor.GOOD : TextColor.ATTENTION)
                .subtitle(success
                        ? "PG 매입 확정이 완료되어 정산 예정 상태로 전환되었습니다."
                        : "PG 매입 요청이 실패했습니다. 서명된 운영 링크를 통해 원인을 확인하고 수동 재매입을 진행하세요.")
                .addFact("거래 ID", tx.getTxId())
                .addFact("주문번호", tx.getOrderId())
                .addFact("매입 대상 금액", TeamsCardFactory.formatCurrencyKrw(tx.getAmount()))
                .addFact("결제수단", tx.getPaymentMethod())
                .addFact("처리 시각", teams.cards().formatTimestamp(processedAt));

        if (!success && tx.getLastFailureReason() != null) {
            builder.addFact("실패 사유", tx.getLastFailureReason());
        }

        if (success) {
            builder.addAction(teams.cards().signedEntryButton("거래 상세 보기", tx.getTxId(), event, "view"))
                    .addAction(teams.cards().signedEntryButton("환불 처리 화면", tx.getTxId(), event, "refund"));
        } else {
            builder.addAction(teams.cards().signedEntryButton("수동 재매입 · 원인 확인", tx.getTxId(), event, "capture"))
                    .addAction(teams.cards().signedEntryButton("거래 상세 보기", tx.getTxId(), event, "view"));
        }
        return teams.cards().buildCard(builder.build());
    }

    public CardBuildResult composeRefundCard(
            PaymentTransaction tx,
            BillingEventType eventType,
            long targetRefundAmount,
            String reason,
            String actor
    ) {
        String event = eventType.name();
        BannerStyle style;
        String badge;
        TextColor color;
        String desc;

        if (eventType == BillingEventType.REFUND_REQUESTED) {
            style = BannerStyle.WARNING;
            badge = "REFUND APPROVAL REQUIRED · 환불 승인 대기";
            color = TextColor.WARNING;
            desc = "환불 승인 대기 건이 접수되었습니다. 아래 인증 링크로 진입하여 승인 또는 반려를 확정하세요.";
        } else if (eventType == BillingEventType.REFUND_REJECTED) {
            style = BannerStyle.EMPHASIS;
            badge = "REFUND REJECTED · 환불 반려";
            color = TextColor.DEFAULT;
            desc = "요청된 환불 건이 반려 처리되어 기존 결제 상태가 유지됩니다.";
        } else {
            style = BannerStyle.GOOD;
            badge = "REFUND COMPLETED · 환불 완료";
            color = TextColor.GOOD;
            desc = "환불 처리가 정상 완료되었습니다.";
        }

        AdaptiveCardSpec.Builder builder = AdaptiveCardSpec.builder()
                .eventType(event)
                .summaryText(String.format("[%s] %s · %s (%s)",
                        eventType.getTitle(), tx.getMerchantName(), TeamsCardFactory.formatCurrencyKrw(targetRefundAmount), tx.getTxId()))
                .bannerStyle(style)
                .badgeLabel(badge)
                .headline(TeamsCardFactory.formatCurrencyKrw(targetRefundAmount))
                .headlineColor(color)
                .subtitle(desc)
                .addFact("거래 ID", tx.getTxId())
                .addFact("주문번호", tx.getOrderId())
                .addFact("원결제 금액", TeamsCardFactory.formatCurrencyKrw(tx.getAmount()))
                .addFact(eventType == BillingEventType.REFUND_REQUESTED ? "환불 요청 금액" : "이번 처리 금액",
                        TeamsCardFactory.formatCurrencyKrw(targetRefundAmount))
                .addFact("환불 후 잔액", TeamsCardFactory.formatCurrencyKrw(tx.getRemainingAmount()))
                .addFact("담당 / 요청자", actor)
                .addFact("사유 / 코멘트", reason)
                .addFact("시각", teams.cards().formatTimestamp(Instant.now()));

        if (eventType == BillingEventType.REFUND_REQUESTED) {
            builder.addAction(teams.cards().signedEntryButton("Teams 인증 링크로 환불 승인/반려", tx.getTxId(), event, "refund"))
                    .addAction(teams.cards().signedEntryButton("거래 원장 조회", tx.getTxId(), event, "view"));
        } else {
            builder.addAction(teams.cards().signedEntryButton("거래 원장 상세 확인", tx.getTxId(), event, "view"));
        }
        return teams.cards().buildCard(builder.build());
    }

    public CardBuildResult composeInquiryCard(PaymentTransaction tx) {
        String event = BillingEventType.TRANSACTION_INQUIRY.name();
        AdaptiveCardSpec spec = AdaptiveCardSpec.builder()
                .eventType(event)
                .summaryText(String.format("[거래 조회] %s · %s", tx.getTxId(), tx.getStatus().getLabel()))
                .bannerStyle(BannerStyle.EMPHASIS)
                .badgeLabel("BILLING LOOKUP · 거래 실시간 조회")
                .headline(TeamsCardFactory.formatCurrencyKrw(tx.getRemainingAmount()))
                .headlineColor(TextColor.ACCENT)
                .subtitle("요청하신 거래의 실시간 원장 상태입니다.")
                .addFact("현재 상태", tx.getStatus().getLabel() + " (" + tx.getStatus().name() + ")")
                .addFact("거래 ID / 주문번호", tx.getTxId() + " / " + tx.getOrderId())
                .addFact("가맹점", tx.getMerchantName())
                .addFact("승인 금액 / 잔액", TeamsCardFactory.formatCurrencyKrw(tx.getAmount())
                        + " / 잔액 " + TeamsCardFactory.formatCurrencyKrw(tx.getRemainingAmount()))
                .addFact("누적 환불액", TeamsCardFactory.formatCurrencyKrw(tx.getRefundedAmount()))
                .addFact("승인 시각", teams.cards().formatTimestamp(tx.getApprovedAt()))
                .addAction(teams.cards().signedEntryButton("운영 콘솔에서 열기", tx.getTxId(), event, "view"))
                .addAction(teams.cards().signedEntryButton("환불/매입 처리", tx.getTxId(), event, "refund"))
                .build();
        return teams.cards().buildCard(spec);
    }

    public FormatMessage composeHtmlFormat(PaymentTransaction tx, String recipientEmail, String customNote) {
        String note = (customNote != null && !customNote.isBlank()) ? customNote.trim() : "정산 대사 대상 결제 건입니다.";
        return teams.formats().buildHtmlMessage(new HtmlMessageSpec(
                tx.getTxId(),
                "[빌링 알림] " + tx.getMerchantName(),
                "💳 [빌링 알림]",
                "결제담당자",
                "87d349ed-44d7-43e1-9a83-5f2406dee5bd",
                recipientEmail != null ? recipientEmail : "billing-oncall@company.com",
                "신규 거래 내역이 도착했습니다. (" + note + ")",
                List.of(
                        new Fact("거래 ID / 주문번호", tx.getTxId() + " / " + tx.getOrderId()),
                        new Fact("가맹점", tx.getMerchantName()),
                        new Fact("결제 금액 / 상태", TeamsCardFactory.formatCurrencyKrw(tx.getAmount()) + " (" + tx.getStatus().getLabel() + ")"),
                        new Fact("결제 시각", teams.cards().formatTimestamp(tx.getApprovedAt()))
                ),
                "Teams 인증 서명 링크로 원장 열기",
                "view"
        ));
    }

    public FormatMessage composeActivityFeedFormat(PaymentTransaction tx, String recipientEmail, String customNote) {
        String preview = (customNote != null && !customNote.isBlank())
                ? customNote.trim()
                : String.format("%s %s 환불/정산 확인 필요 (%s)",
                        tx.getMerchantName(), TeamsCardFactory.formatCurrencyKrw(tx.getAmount()), tx.getTxId());
        return teams.formats().buildActivityFeed(new ActivityFeedSpec(
                tx.getTxId(),
                "빌링 운영 센터 · " + tx.getTxId(),
                "billingApprovalRequired",
                recipientEmail != null ? recipientEmail : "approver@company.com",
                preview,
                Map.of(
                        "merchantName", tx.getMerchantName(),
                        "amount", TeamsCardFactory.formatCurrencyKrw(tx.getAmount())
                ),
                "refund"
        ));
    }

    public FormatMessage composeUrgentFormat(PaymentTransaction tx, String teamTagName, String customNote) {
        String reason = (customNote != null && !customNote.isBlank())
                ? customNote.trim()
                : (tx.getLastFailureReason() != null ? tx.getLastFailureReason() : "PG 매입 응답 지연 임계치 초과");
        return teams.formats().buildUrgentAlert(new UrgentAlertSpec(
                tx.getTxId(),
                teamTagName != null ? teamTagName : "결제운영당직",
                "tag-billing-oncall-01",
                "즉시 확인 바랍니다!",
                List.of(
                        new Fact("대상 거래", tx.getTxId() + " (" + tx.getMerchantName() + " · " + TeamsCardFactory.formatCurrencyKrw(tx.getAmount()) + ")"),
                        new Fact("경보 사유", reason)
                ),
                "서명된 긴급 운영 콘솔 바로 열기",
                "capture"
        ));
    }

    public FormatMessage composeReceiptFormat(PaymentTransaction tx) {
        long supplyAmount = Math.round(tx.getAmount() / 1.1);
        long vatAmount = tx.getAmount() - supplyAmount;
        return teams.formats().buildReceiptCard(new ReceiptCardSpec(
                tx.getTxId(),
                tx.getMerchantName() + " 결제 영수증",
                List.of(
                        new Fact("거래 ID", tx.getTxId()),
                        new Fact("주문번호", tx.getOrderId()),
                        new Fact("결제수단", tx.getPaymentMethod()),
                        new Fact("승인번호", tx.getAuthCode()),
                        new Fact("결제 시각", teams.cards().formatTimestamp(tx.getApprovedAt()))
                ),
                List.of(new ReceiptItem(
                        tx.getMerchantName(),
                        "청구 주기: 월간 정기 구독 (공급가액)",
                        TeamsCardFactory.formatCurrencyKrw(supplyAmount),
                        "1"
                )),
                TeamsCardFactory.formatCurrencyKrw(vatAmount),
                TeamsCardFactory.formatCurrencyKrw(tx.getAmount()),
                "영수증 원장 · 환불 콘솔 열기 (Teams 서명됨)",
                "view"
        ));
    }

    public FormatMessage composeHeroFormat(PaymentTransaction tx) {
        return teams.formats().buildHeroCard(new HeroCardSpec(
                tx.getTxId(),
                "💳 " + TeamsCardFactory.formatCurrencyKrw(tx.getAmount()) + " · " + tx.getStatus().getLabel(),
                tx.getMerchantName() + " | " + tx.getTxId() + " (" + tx.getOrderId() + ")",
                "결제수단: " + tx.getPaymentMethod()
                        + " · 잔액: " + TeamsCardFactory.formatCurrencyKrw(tx.getRemainingAmount())
                        + " · 승인시각: " + teams.cards().formatTimestamp(tx.getApprovedAt()),
                "상세 원장 보기 (서명 링크)",
                "view",
                "환불 처리 화면",
                "refund"
        ));
    }

    public FormatMessage composeAdaptiveComparisonFormat(PaymentTransaction tx) {
        return teams.formats().buildAdaptiveCardFormat(tx.getTxId(), composeAuthorizedCard(tx));
    }
}
