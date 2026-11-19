package com.example.teams;

import com.example.teams.cards.TeamsCardFactory;
import com.example.teams.cards.TeamsFormatFactory;
import com.example.teams.client.TeamsWebhookClient;
import com.example.teams.client.TeamsWebhookClient.TeamsDispatchRecord;
import com.example.teams.config.TeamsSettings;
import com.example.teams.domain.EventType;
import com.example.teams.domain.TransactionView;
import com.example.teams.security.LinkVerificationResult;
import com.example.teams.security.OutgoingWebhookVerifier;
import com.example.teams.security.TeamsLinkSigner;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * 다른 애플리케이션에서 MS Teams 알림·카드 생성·서명 링크 검증 기능을
 * 단일 진입점(Facade)으로 손쉽게 사용할 수 있도록 제공하는 라이브러리 대표 클라이언트입니다.
 *
 * <pre>{@code
 * // 1) 일반 Java 프로젝트에서 1줄 초기화
 * TeamsNotifier teams = TeamsNotifier.create(
 *     TeamsSettings.builder()
 *         .webhookUrl("https://...")
 *         .baseUrl("https://admin.mybilling.com")
 *         .entryPathTemplate("/admin/payments/{txId}")
 *         .linkSecret(System.getenv("TEAMS_LINK_SECRET"))
 *         .build()
 * );
 *
 * // 2) Spring Boot 프로젝트에서는 @Autowired TeamsNotifier 바로 주입!
 * }</pre>
 */
public class TeamsNotifier {

    private final TeamsSettings settings;
    private final TeamsLinkSigner linkSigner;
    private final OutgoingWebhookVerifier outgoingWebhookVerifier;
    private final TeamsCardFactory cardFactory;
    private final TeamsFormatFactory formatFactory;
    private final TeamsWebhookClient webhookClient;

    public TeamsNotifier(
            TeamsSettings settings,
            TeamsLinkSigner linkSigner,
            OutgoingWebhookVerifier outgoingWebhookVerifier,
            TeamsCardFactory cardFactory,
            TeamsFormatFactory formatFactory,
            TeamsWebhookClient webhookClient
    ) {
        this.settings = Objects.requireNonNull(settings);
        this.linkSigner = Objects.requireNonNull(linkSigner);
        this.outgoingWebhookVerifier = Objects.requireNonNull(outgoingWebhookVerifier);
        this.cardFactory = Objects.requireNonNull(cardFactory);
        this.formatFactory = Objects.requireNonNull(formatFactory);
        this.webhookClient = Objects.requireNonNull(webhookClient);
    }

    public static TeamsNotifier create(TeamsSettings settings) {
        TeamsLinkSigner signer = new TeamsLinkSigner(settings);
        OutgoingWebhookVerifier verifier = new OutgoingWebhookVerifier(settings);
        TeamsCardFactory cards = new TeamsCardFactory(signer);
        TeamsFormatFactory formats = new TeamsFormatFactory(signer, cards);
        TeamsWebhookClient client = new TeamsWebhookClient(settings);
        return new TeamsNotifier(settings, signer, verifier, cards, formats, client);
    }

    public TeamsDispatchRecord notifyPaymentAuthorized(TransactionView tx) {
        return webhookClient.sendCard(tx.getTxId(), cardFactory.buildPaymentAuthorizedCard(tx));
    }

    public TeamsDispatchRecord notifyCaptureResult(TransactionView tx, boolean success, Instant processedAt) {
        return webhookClient.sendCard(tx.getTxId(), cardFactory.buildCaptureResultCard(tx, success, processedAt));
    }

    public TeamsDispatchRecord notifyRefund(
            TransactionView tx,
            EventType eventType,
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

    public TeamsSettings settings() {
        return settings;
    }

    public TeamsLinkSigner linkSigner() {
        return linkSigner;
    }

    public OutgoingWebhookVerifier outgoingWebhookVerifier() {
        return outgoingWebhookVerifier;
    }

    public TeamsCardFactory cards() {
        return cardFactory;
    }

    public TeamsFormatFactory formats() {
        return formatFactory;
    }

    public TeamsWebhookClient webhookClient() {
        return webhookClient;
    }
}
