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
 * 비즈니스 도메인에 종속되지 않는 범용 MS Teams 알림 전송·멀티 포맷 카드 조립·{@code HMAC-SHA256} 서명 링크 검증을 위한
 * 라이브러리 대표 진입점(Facade)입니다.
 *
 * <h2>스레드 안전성(Thread-Safety)</h2>
 * <p>{@code TeamsNotifier} 및 하위 컴포넌트들은 모두 스레드 안전하게 동작하므로
 * 요청마다 새로 생성하지 않고 애플리케이션 전역에서 싱글톤 빈으로 공유해 사용합니다.</p>
 *
 * <h2>사용 예시</h2>
 * <pre>{@code
 * // 1) Spring Boot 환경: teams-spring-boot-starter 의존성 추가 후 생성자 주입
 * private final TeamsNotifier teams;
 *
 * // 2) 일반 Java 17 환경: 팩토리 메서드로 즉시 생성
 * TeamsNotifier teams = TeamsNotifier.create(
 *     TeamsSettings.builder()
 *         .webhookUrl(System.getenv("TEAMS_WEBHOOK_URL"))
 *         .baseUrl("https://admin.our-service.com")
 *         .entryPathTemplate("/admin/orders/{id}")
 *         .linkSecret(System.getenv("TEAMS_LINK_SECRET"))
 *         .build()
 * );
 *
 * // 선언적 Adaptive Card 조립 및 전송
 * AdaptiveCardSpec spec = AdaptiveCardSpec.builder()
 *     .eventType("ORDER_CREATED")
 *     .badgeLabel("NEW ORDER")
 *     .headline("₩128,000")
 *     .addFact("주문번호", "ORD-1001")
 *     .addAction(teams.cards().signedEntryButton("상세 보기", "ORD-1001", "ORDER_CREATED", "view"))
 *     .build();
 * teams.sendCard("ORD-1001", spec);
 * }</pre>
 *
 * @see TeamsSettings
 * @see AdaptiveCardSpec
 * @see TeamsCardFactory
 * @see TeamsFormatFactory
 * @see TeamsLinkSigner
 */
public class TeamsNotifier {

    private final TeamsSettings settings;
    private final TeamsLinkSigner linkSigner;
    private final OutgoingWebhookVerifier outgoingWebhookVerifier;
    private final TeamsCardFactory cardFactory;
    private final TeamsFormatFactory formatFactory;
    private final TeamsWebhookClient webhookClient;

    /**
     * 구성된 하위 컴포넌트들을 주입받아 {@link TeamsNotifier}를 생성합니다.
     *
     * @param settings                런타임 설정 ({@code null} 불가)
     * @param linkSigner              링크 서명·검증기 ({@code null} 불가)
     * @param outgoingWebhookVerifier Outgoing Webhook 헤더 검증기 ({@code null} 불가)
     * @param cardFactory             Adaptive Card 조립 팩토리 ({@code null} 불가)
     * @param formatFactory           6종 멀티 포맷 팩토리 ({@code null} 불가)
     * @param webhookClient           웹훅 HTTP 전송 클라이언트 ({@code null} 불가)
     */
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

    /**
     * 순수 Java 환경에서 {@link TeamsSettings} 하나로 모든 내부 컴포넌트를 기본 초기화하여 인스턴스를 생성합니다.
     *
     * @param settings Teams 설정 객체 ({@code null} 불가)
     * @return 초기화된 {@link TeamsNotifier} 인스턴스
     */
    public static TeamsNotifier create(TeamsSettings settings) {
        return create(settings, Clock.systemUTC());
    }

    /**
     * 지정된 {@link Clock}(단위 테스트 등 결정론적 시각 제어용)으로 모든 내부 컴포넌트를 초기화하여 생성합니다.
     *
     * @param settings Teams 설정 객체 ({@code null} 불가)
     * @param clock    서명 만료 및 생성 시각 계산에 사용할 시계 ({@code null} 불가)
     * @return 초기화된 {@link TeamsNotifier} 인스턴스
     */
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
     * 선언적 {@link AdaptiveCardSpec}을 Adaptive Card v1.4 봉투로 조립한 뒤 기본 설정된 웹훅 채널로 발송합니다.
     *
     * @param resourceId 발송 추적용 리소스 식별자
     * @param spec       조립할 카드 스펙 ({@code null} 불가)
     * @return 발송 결과 및 페이로드 스냅샷이 담긴 {@link TeamsDispatchRecord}
     */
    public TeamsDispatchRecord sendCard(String resourceId, AdaptiveCardSpec spec) {
        return webhookClient.sendCard(resourceId, cardFactory.buildCard(spec));
    }

    /**
     * 미리 조립된 {@link CardBuildResult}를 기본 설정된 웹훅 채널로 발송합니다.
     *
     * @param resourceId 발송 추적용 리소스 식별자
     * @param builtCard  조립 완료된 카드 결과 ({@code null} 불가)
     * @return 발송 결과 및 페이로드 스냅샷이 담긴 {@link TeamsDispatchRecord}
     */
    public TeamsDispatchRecord sendCard(String resourceId, CardBuildResult builtCard) {
        return webhookClient.sendCard(resourceId, builtCard);
    }

    /**
     * 기본 설정 URL 대신 호출 시 지정한 별도의 Teams 웹훅 URL로 카드를 분기 발송합니다.
     *
     * @param targetWebhookUrl 전송할 대상 웹훅 URL
     * @param resourceId       발송 추적용 리소스 식별자
     * @param spec             조립할 카드 스펙 ({@code null} 불가)
     * @return 발송 결과 및 페이로드 스냅샷이 담긴 {@link TeamsDispatchRecord}
     */
    public TeamsDispatchRecord sendCardTo(String targetWebhookUrl, String resourceId, AdaptiveCardSpec spec) {
        return webhookClient.sendCardToWebhook(targetWebhookUrl, resourceId, cardFactory.buildCard(spec));
    }

    /**
     * 일반 텍스트 또는 HTML 본문을 {@link TeamsSettings#getMessageWebhookUrl()}로 전송합니다.
     * Workflows 측 {@code Post message in a chat or channel} 액션이 {@code triggerBody()?['message']}를 수신하는 구조와 호환됩니다.
     *
     * @param resourceId 발송 추적용 리소스 식별자
     * @param message    전송할 텍스트 또는 HTML 본문 ({@code null} 불가)
     * @return 발송 결과 및 페이로드 스냅샷이 담긴 {@link TeamsDispatchRecord}
     */
    public TeamsDispatchRecord sendMessage(String resourceId, String message) {
        return webhookClient.sendMessage(resourceId, "[메시지 발송] " + resourceId, message);
    }

    /**
     * 일반 텍스트 또는 HTML 본문과 요약 문구를 {@link TeamsSettings#getMessageWebhookUrl()}로 전송합니다.
     *
     * @param resourceId      발송 추적용 리소스 식별자
     * @param summary         로그 및 Outbox 표시용 요약 문구
     * @param message         전송할 텍스트 또는 HTML 본문 ({@code null} 불가)
     * @param signedDetailUrl 본문에 포함된 서명 링크 URL (선택, {@code null} 가능)
     * @return 발송 결과 및 페이로드 스냅샷이 담긴 {@link TeamsDispatchRecord}
     */
    public TeamsDispatchRecord sendMessage(String resourceId, String summary, String message, String signedDetailUrl) {
        return webhookClient.sendMessageToWebhook(
                settings.getMessageWebhookUrl(), resourceId, summary, message, signedDetailUrl);
    }

    /**
     * 지정한 별도의 메시지 전용 Teams 웹훅 URL로 {@code {"message": "..."}} 페이로드를 전송합니다.
     *
     * @param targetWebhookUrl 전송할 대상 웹훅 URL
     * @param resourceId       발송 추적용 리소스 식별자
     * @param summary          로그 및 Outbox 표시용 요약 문구
     * @param message          전송할 텍스트 또는 HTML 본문 ({@code null} 불가)
     * @return 발송 결과 및 페이로드 스냅샷이 담긴 {@link TeamsDispatchRecord}
     */
    public TeamsDispatchRecord sendMessageTo(String targetWebhookUrl, String resourceId, String summary, String message) {
        return webhookClient.sendMessageToWebhook(targetWebhookUrl, resourceId, summary, message, null);
    }

    /**
     * 임의의 커스텀 JSON 맵 페이로드를 기본 설정된 웹훅 채널로 전송합니다.
     *
     * @param resourceId 발송 추적용 리소스 식별자
     * @param eventType  이벤트 유형 식별자
     * @param summary    로그 요약 문구
     * @param payload    전송할 JSON 객체 맵
     * @return 발송 결과 및 페이로드 스냅샷이 담긴 {@link TeamsDispatchRecord}
     */
    public TeamsDispatchRecord sendRawPayload(
            String resourceId,
            String eventType,
            String summary,
            Map<String, Object> payload
    ) {
        return webhookClient.sendRawPayload(settings.getWebhookUrl(), resourceId, eventType, summary, payload);
    }

    /**
     * 기본 진입 경로 템플릿({@code entryPathTemplate})을 기준으로 {@code HMAC-SHA256} 서명된 URL을 생성합니다.
     *
     * @param resourceId 대상 리소스 식별자 ({@code null} 불가)
     * @param cardType   알림·카드 유형 식별자
     * @param action     의도 액션 식별자 (예: {@code "view"}, {@code "approve"})
     * @return 서명 쿼리 파라미터가 부착된 전체 URL
     */
    public String createSignedUrl(String resourceId, String cardType, String action) {
        return linkSigner.createSignedEntryUrl(resourceId, cardType, action);
    }

    /**
     * 임의의 외부 웹 URL에 Teams 유입 검증용 {@code HMAC-SHA256} 서명 파라미터를 덧붙입니다.
     *
     * @param targetUrl  대상 웹 URL ({@code null} 불가)
     * @param resourceId 검증 시 대조할 리소스 식별자 ({@code null} 불가)
     * @param cardType   알림·카드 유형 식별자
     * @param action     의도 액션 식별자
     * @return 서명 쿼리 파라미터가 추가된 전체 URL
     */
    public String signCustomUrl(String targetUrl, String resourceId, String cardType, String action) {
        return linkSigner.signCustomUrl(targetUrl, resourceId, cardType, action);
    }

    /**
     * 웹 컨트롤러에 전달된 개별 쿼리 파라미터들로부터 정식 MS Teams 링크 유입 여부를 검증합니다.
     *
     * @param resourceId 현재 접근 경로의 리소스 식별자
     * @param src        {@code src} 파라미터
     * @param cardType   {@code cardType} 파라미터
     * @param action     {@code action} 파라미터
     * @param iat        {@code iat} 발급 시각(Epoch 초)
     * @param exp        {@code exp} 만료 시각(Epoch 초)
     * @param sig        {@code sig} Base64URL 서명값
     * @return 4단계 검증 상태와 사유를 담은 {@link LinkVerificationResult}
     */
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

    /**
     * 쿼리 파라미터 {@link Map}으로부터 정식 MS Teams 링크 유입 여부를 검증합니다.
     *
     * @param resourceId  현재 접근 경로의 리소스 식별자
     * @param queryParams 쿼리 파라미터 맵
     * @return 4단계 검증 상태와 사유를 담은 {@link LinkVerificationResult}
     */
    public LinkVerificationResult verifySignedQueryMap(String resourceId, Map<String, String> queryParams) {
        return linkSigner.verifyQueryMap(resourceId, queryParams);
    }

    /**
     * 현재 적용된 불변 {@link TeamsSettings}를 반환합니다.
     *
     * @return {@link TeamsSettings}
     */
    public TeamsSettings settings() {
        return settings;
    }

    /**
     * 고급 서명·검증 기능을 제공하는 {@link TeamsLinkSigner}를 반환합니다.
     *
     * @return {@link TeamsLinkSigner}
     */
    public TeamsLinkSigner linkSigner() {
        return linkSigner;
    }

    /**
     * 표준 채널 Outgoing Webhook 서명 검증기인 {@link OutgoingWebhookVerifier}를 반환합니다.
     *
     * @return {@link OutgoingWebhookVerifier}
     */
    public OutgoingWebhookVerifier outgoingWebhookVerifier() {
        return outgoingWebhookVerifier;
    }

    /**
     * Adaptive Card 조립 및 버튼 서명 헬퍼를 제공하는 {@link TeamsCardFactory}를 반환합니다.
     *
     * @return {@link TeamsCardFactory}
     */
    public TeamsCardFactory cards() {
        return cardFactory;
    }

    /**
     * HTML·활동 피드·긴급 태그·영수증·히어로 카드 등 6종 포맷 생성기인 {@link TeamsFormatFactory}를 반환합니다.
     *
     * @return {@link TeamsFormatFactory}
     */
    public TeamsFormatFactory formats() {
        return formatFactory;
    }

    /**
     * 저수준 웹훅 전송 및 인메모리 Outbox 제어를 담당하는 {@link TeamsWebhookClient}를 반환합니다.
     *
     * @return {@link TeamsWebhookClient}
     */
    public TeamsWebhookClient webhookClient() {
        return webhookClient;
    }
}
