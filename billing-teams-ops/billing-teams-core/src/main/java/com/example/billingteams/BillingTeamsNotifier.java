package com.example.billingteams;

import com.example.billingteams.cards.BillingAdaptiveCardFactory;
import com.example.billingteams.cards.TeamsMultiFormatNotificationFactory;
import com.example.billingteams.client.TeamsWorkflowWebhookClient;
import com.example.billingteams.client.TeamsWorkflowWebhookClient.TeamsDispatchRecord;
import com.example.billingteams.config.TeamsClientSettings;
import com.example.billingteams.domain.BillingEventType;
import com.example.billingteams.domain.BillingTransactionView;
import com.example.billingteams.security.LinkVerificationResult;
import com.example.billingteams.security.OutgoingWebhookVerifier;
import com.example.billingteams.security.TeamsLinkSigner;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * 다른 애플리케이션에서 MS Teams 알림·카드 생성·서명 링크 검증 기능을
 * 단일 진입점(Facade)으로 손쉽게 사용할 수 있도록 제공하는 라이브러리 대표 클라이언트입니다.
 *
 * <pre>{@code
 * // 1) 일반 Java 프로젝트에서 1줄 초기화
 * BillingTeamsNotifier teams = BillingTeamsNotifier.create(
 *     TeamsClientSettings.builder()
 *         .webhookUrl("https://...")
 *         .baseUrl("https://admin.mybilling.com")
 *         .entryPathTemplate("/admin/payments/{txId}")
 *         .linkSecret(System.getenv("TEAMS_LINK_SECRET"))
 *         .build()
 * );
 *
 * // 2) Spring Boot 프로젝트에서는 @Autowired BillingTeamsNotifier 바로 주입!
 * }</pre>
 */
public class BillingTeamsNotifier {

    private final TeamsClientSettings settings;
    private final TeamsLinkSigner linkSigner;
    private final OutgoingWebhookVerifier outgoingWebhookVerifier;
    private final BillingAdaptiveCardFactory cardFactory;
    private final TeamsMultiFormatNotificationFactory formatFactory;
    private final TeamsWorkflowWebhookClient webhookClient;

    public BillingTeamsNotifier(
            TeamsClientSettings settings,
            TeamsLinkSigner linkSigner,
            OutgoingWebhookVerifier outgoingWebhookVerifier,
            BillingAdaptiveCardFactory cardFactory,
            TeamsMultiFormatNotificationFactory formatFactory,
            TeamsWorkflowWebhookClient webhookClient
    ) {
        this.settings = Objects.requireNonNull(settings);
        this.linkSigner = Objects.requireNonNull(linkSigner);
        this.outgoingWebhookVerifier = Objects.requireNonNull(outgoingWebhookVerifier);
        this.cardFactory = Objects.requireNonNull(cardFactory);
        this.formatFactory = Objects.requireNonNull(formatFactory);
        this.webhookClient = Objects.requireNonNull(webhookClient);
    }

    public static BillingTeamsNotifier create(TeamsClientSettings settings) {
        TeamsLinkSigner signer = new TeamsLinkSigner(settings);
        OutgoingWebhookVerifier verifier = new OutgoingWebhookVerifier(settings);
        BillingAdaptiveCardFactory cards = new BillingAdaptiveCardFactory(signer);
        TeamsMultiFormatNotificationFactory formats = new TeamsMultiFormatNotificationFactory(signer, cards);
        TeamsWorkflowWebhookClient client = new TeamsWorkflowWebhookClient(settings);
        return new BillingTeamsNotifier(settings, signer, verifier, cards, formats, client);
    }

    public TeamsDispatchRecord notifyPaymentAuthorized(BillingTransactionView tx) {
        return webhookClient.sendCard(tx.getTxId(), cardFactory.buildPaymentAuthorizedCard(tx));
    }

    public TeamsDispatchRecord notifyCaptureResult(BillingTransactionView tx, boolean success, Instant processedAt) {
        return webhookClient.sendCard(tx.getTxId(), cardFactory.buildCaptureResultCard(tx, success, processedAt));
    }

    public TeamsDispatchRecord notifyRefund(
            BillingTransactionView tx,
            BillingEventType eventType,
            long refundAmount,
            String reason,
            String actor
    ) {
        return webhookClient.sendCard(tx.getTxId(), cardFactory.buildRefundCard(tx, eventType, refundAmount, reason, actor));
    }

    public String createSignedUrl(String txId, String cardType, String action) {
        return linkSigner.createSignedTransactionUrl(txId, cardType, action);
    }

    public String signCustomUrl(String targetUrl, String resourceId, String cardType, String action) {
        return linkSigner.signCustomUrl(targetUrl, resourceId, cardType, action);
    }

    public LinkVerificationResult verifySignedLink(
            String txId,
            String src,
            String cardType,
            String action,
            Long iat,
            Long exp,
            String sig
    ) {
        return linkSigner.verify(txId, src, cardType, action, iat, exp, sig);
    }

    public LinkVerificationResult verifySignedQueryMap(String resourceId, Map<String, String> queryParams) {
        return linkSigner.verifyQueryMap(resourceId, queryParams);
    }

    public TeamsClientSettings settings() {
        return settings;
    }

    public TeamsLinkSigner linkSigner() {
        return linkSigner;
    }

    public OutgoingWebhookVerifier outgoingWebhookVerifier() {
        return outgoingWebhookVerifier;
    }

    public BillingAdaptiveCardFactory cards() {
        return cardFactory;
    }

    public TeamsMultiFormatNotificationFactory formats() {
        return formatFactory;
    }

    public TeamsWorkflowWebhookClient webhookClient() {
        return webhookClient;
    }
}
