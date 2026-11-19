package com.example.teams;

import com.example.teams.cards.AdaptiveCardSpec;
import com.example.teams.cards.TeamsCardFactory;
import com.example.teams.cards.TeamsCardFactory.CardBuildResult;
import com.example.teams.cards.TeamsFormatFactory;
import com.example.teams.client.TeamsWebhookClient;
import com.example.teams.client.TeamsWebhookClient.TeamsDispatchRecord;
import com.example.teams.config.TeamsSettings;
import com.example.teams.security.LinkVerificationResult;
import com.example.teams.security.OutgoingWebhookVerifier;
import com.example.teams.security.TeamsLinkSigner;

import java.time.Clock;
import java.util.Map;
import java.util.Objects;

/**
 * 도메인에 종속되지 않는 범용 MS Teams 알림·카드 조립·HMAC 서명 링크 검증 대표 진입점(Facade).
 *
 * <pre>{@code
 * // 1) 일반 Java 프로젝트에서 1줄 초기화
 * TeamsNotifier teams = TeamsNotifier.create(
 *     TeamsSettings.builder()
 *         .webhookUrl(System.getenv("TEAMS_WEBHOOK_URL"))
 *         .baseUrl("https://admin.our-service.com")
 *         .entryPathTemplate("/admin/items/{id}")
 *         .linkSecret(System.getenv("TEAMS_LINK_SECRET"))
 *         .build()
 * );
 *
 * // 2) Spring Boot 프로젝트에서는 @Autowired TeamsNotifier 즉시 주입
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
        this.settings = Objects.requireNonNull(settings, "settings must not be null");
        this.linkSigner = Objects.requireNonNull(linkSigner, "linkSigner must not be null");
        this.outgoingWebhookVerifier = Objects.requireNonNull(outgoingWebhookVerifier, "outgoingWebhookVerifier must not be null");
        this.cardFactory = Objects.requireNonNull(cardFactory, "cardFactory must not be null");
        this.formatFactory = Objects.requireNonNull(formatFactory, "formatFactory must not be null");
        this.webhookClient = Objects.requireNonNull(webhookClient, "webhookClient must not be null");
    }

    public static TeamsNotifier create(TeamsSettings settings) {
        return create(settings, Clock.systemUTC());
    }

    public static TeamsNotifier create(TeamsSettings settings, Clock clock) {
        Objects.requireNonNull(settings, "settings must not be null");
        Objects.requireNonNull(clock, "clock must not be null");
        TeamsLinkSigner signer = new TeamsLinkSigner(settings, clock);
        OutgoingWebhookVerifier verifier = new OutgoingWebhookVerifier(settings);
        TeamsCardFactory cards = new TeamsCardFactory(signer, settings.getDisplayZone());
        TeamsFormatFactory formats = new TeamsFormatFactory(signer, null, clock);
        TeamsWebhookClient client = new TeamsWebhookClient(settings);
        return new TeamsNotifier(settings, signer, verifier, cards, formats, client);
    }

    /**
     * 선언적 {@link AdaptiveCardSpec}을 조립하여 기본 설정된 Teams Webhook 채널로 즉시 발송합니다.
     */
    public TeamsDispatchRecord sendCard(String resourceId, AdaptiveCardSpec spec) {
        return webhookClient.sendCard(resourceId, cardFactory.buildCard(spec));
    }

    public TeamsDispatchRecord sendCard(String resourceId, CardBuildResult builtCard) {
        return webhookClient.sendCard(resourceId, builtCard);
    }

    /**
     * 채널별로 다른 Teams Webhook URL로 분기하여 카드를 발송합니다.
     */
    public TeamsDispatchRecord sendCardTo(String targetWebhookUrl, String resourceId, AdaptiveCardSpec spec) {
        return webhookClient.sendCardToWebhook(targetWebhookUrl, resourceId, cardFactory.buildCard(spec));
    }

    public TeamsDispatchRecord sendRawPayload(
            String resourceId,
            String eventType,
            String summary,
            Map<String, Object> payload
    ) {
        return webhookClient.sendRawPayload(settings.getWebhookUrl(), resourceId, eventType, summary, payload);
    }

    public String createSignedUrl(String resourceId, String cardType, String action) {
        return linkSigner.createSignedEntryUrl(resourceId, cardType, action);
    }

    public String signCustomUrl(String targetUrl, String resourceId, String cardType, String action) {
        return linkSigner.signCustomUrl(targetUrl, resourceId, cardType, action);
    }

    public LinkVerificationResult verifySignedLink(
            String resourceId,
            String src,
            String cardType,
            String action,
            Long iat,
            Long exp,
            String sig
    ) {
        return linkSigner.verify(resourceId, src, cardType, action, iat, exp, sig);
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
