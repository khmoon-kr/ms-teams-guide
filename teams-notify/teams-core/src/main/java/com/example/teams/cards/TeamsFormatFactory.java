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
 * Adaptive Card 외에 MS Teams가 지원하는 5가지 대안 알림 포맷(리치 HTML 표 메시지, 활동 피드 벨 알림,
 * 긴급 반복 푸시 + {@code @팀태그}, 영수증 카드, 히어로 카드)의 표준 JSON 페이로드를 생성하는 팩토리입니다.
 *
 * <p>모든 생성 메서드는 내부 진입 링크에 {@link TeamsLinkSigner}의 {@code HMAC-SHA256} 서명과
 * 해당 {@link TeamsFormat} 식별자를 자동으로 포함시키므로, 사용자가 어떤 알림 포맷의 링크를 클릭해
 * 웹 시스템에 진입했는지까지 수신 측에서 정확히 식별할 수 있습니다.</p>
 *
 * @see TeamsFormat
 * @see FormatMessage
 */
public class TeamsFormatFactory {

    /**
     * 리치 HTML 표({@code <table>}) 및 개인 {@code @멘션} 메시지 생성을 위한 불변 입력 스펙입니다.
     *
     * @param resourceId         대상 리소스 식별자 ({@code null} 불가)
     * @param subject            채널 게시물 제목
     * @param badgeTitle         본문 첫머리 강조 말머리 (예: {@code "[정산 알림]"})
     * @param mentionDisplayName {@code <at>} 태그에 표시할 수신자 이름
     * @param mentionUserId      Microsoft Entra ID(AAD) 사용자 Object ID (Graph API 전송 시 사용)
     * @param mentionEmail       수신자 이메일 주소 (Workflows 멘션 토큰 조회 시 사용)
     * @param introText          인사말 뒤 안내 문구
     * @param rows               HTML 표에 순서대로 렌더링할 키-값 행 목록
     * @param linkLabel          하단 서명 링크 앵커 텍스트
     * @param action             서명 링크에 기록할 의도 액션 (생략 시 {@code "view"})
     */
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
        /**
         * 필수 인자 검증 및 방어적 불변 복사를 수행하는 정규 생성자입니다.
         *
         * @param resourceId         대상 리소스 식별자 ({@code null} 불가)
         * @param subject            채널 게시물 제목
         * @param badgeTitle         본문 첫머리 강조 말머리 (예: {@code "[정산 알림]"})
         * @param mentionDisplayName {@code <at>} 태그에 표시할 수신자 이름
         * @param mentionUserId      Microsoft Entra ID(AAD) 사용자 Object ID (Graph API 전송 시 사용)
         * @param mentionEmail       수신자 이메일 주소 (Workflows 멘션 토큰 조회 시 사용)
         * @param introText          인사말 뒤 안내 문구
         * @param rows               HTML 표에 순서대로 렌더링할 키-값 행 목록
         * @param linkLabel          하단 서명 링크 앵커 텍스트
         * @param action             서명 링크에 기록할 의도 액션 (생략 시 {@code "view"})
         */
        public HtmlMessageSpec {
            Objects.requireNonNull(resourceId, "resourceId must not be null");
            rows = rows != null ? List.copyOf(rows) : List.of();
            action = (action != null && !action.isBlank()) ? action : "view";
        }
    }

    /**
     * MS Teams 좌측 상단 활동 피드(🔔) 푸시 알림 생성을 위한 불변 입력 스펙입니다.
     *
     * @param resourceId         대상 리소스 식별자 ({@code null} 불가)
     * @param topicLabel         활동 피드 출처 제목
     * @param activityType       Teams 앱 매니페스트에 정의된 {@code activityTypes} 식별자
     * @param recipientEmail     알림 수신 대상자 이메일
     * @param previewText        OS 배너 및 활동 목록에 노출될 미리보기 요약문
     * @param templateParameters Graph {@code sendActivityNotification} 치환 파라미터 맵
     * @param action             클릭 이동 링크에 기록할 의도 액션 (생략 시 {@code "view"})
     */
    public record ActivityFeedSpec(
            String resourceId,
            String topicLabel,
            String activityType,
            String recipientEmail,
            String previewText,
            Map<String, String> templateParameters,
            String action
    ) {
        /**
         * 필수 인자 검증 및 방어적 불변 복사를 수행하는 정규 생성자입니다.
         *
         * @param resourceId         대상 리소스 식별자 ({@code null} 불가)
         * @param topicLabel         활동 피드 출처 제목
         * @param activityType       Teams 앱 매니페스트에 정의된 {@code activityTypes} 식별자
         * @param recipientEmail     알림 수신 대상자 이메일
         * @param previewText        OS 배너 및 활동 목록에 노출될 미리보기 요약문
         * @param templateParameters Graph {@code sendActivityNotification} 치환 파라미터 맵
         * @param action             클릭 이동 링크에 기록할 의도 액션 (생략 시 {@code "view"})
         */
        public ActivityFeedSpec {
            Objects.requireNonNull(resourceId, "resourceId must not be null");
            templateParameters = templateParameters != null
                    ? Collections.unmodifiableMap(new LinkedHashMap<>(templateParameters))
                    : Map.of();
            action = (action != null && !action.isBlank()) ? action : "view";
        }
    }

    /**
     * 긴급({@code importance: "urgent"} — 2분 간격 최대 20분 반복 알림) 및 {@code @팀태그} 호출 메시지 생성을 위한 불변 스펙입니다.
     *
     * @param resourceId  대상 리소스 식별자 ({@code null} 불가)
     * @param teamTagName 호출할 Teams 팀 태그 표시 이름 (예: {@code "운영당직"})
     * @param teamTagId   Graph {@code teamworkTagIdentity} 태그 ID
     * @param headline    긴급 호출 본문 헤드라인 문구
     * @param details     글머리 기호({@code <ul>})로 나열할 상세 정보 목록
     * @param linkLabel   하단 긴급 조치 서명 링크 문구
     * @param action      서명 링크에 기록할 의도 액션 (생략 시 {@code "view"})
     */
    public record UrgentAlertSpec(
            String resourceId,
            String teamTagName,
            String teamTagId,
            String headline,
            List<Fact> details,
            String linkLabel,
            String action
    ) {
        /**
         * 필수 인자 검증 및 방어적 불변 복사를 수행하는 정규 생성자입니다.
         *
         * @param resourceId  대상 리소스 식별자 ({@code null} 불가)
         * @param teamTagName 호출할 Teams 팀 태그 표시 이름 (예: {@code "운영당직"})
         * @param teamTagId   Graph {@code teamworkTagIdentity} 태그 ID
         * @param headline    긴급 호출 본문 헤드라인 문구
         * @param details     글머리 기호({@code <ul>})로 나열할 상세 정보 목록
         * @param linkLabel   하단 긴급 조치 서명 링크 문구
         * @param action      서명 링크에 기록할 의도 액션 (생략 시 {@code "view"})
         */
        public UrgentAlertSpec {
            Objects.requireNonNull(resourceId, "resourceId must not be null");
            details = details != null ? List.copyOf(details) : List.of();
            action = (action != null && !action.isBlank()) ? action : "view";
        }
    }

    /**
     * 영수증 카드({@code ReceiptCardSpec}) 내 개별 품목·항목 행을 나타내는 불변 레코드입니다.
     *
     * @param title    항목명 ({@code null} 불가)
     * @param subtitle 항목 보조 설명
     * @param price    표시 단가·금액 문자열
     * @param quantity 수량 문자열 (예: {@code "1"})
     */
    public record ReceiptItem(String title, String subtitle, String price, String quantity) {
        /**
         * 필수 인자 검증 및 방어적 불변 복사를 수행하는 정규 생성자입니다.
         *
         * @param title    항목명 ({@code null} 불가)
         * @param subtitle 항목 보조 설명
         * @param price    표시 단가·금액 문자열
         * @param quantity 수량 문자열 (예: {@code "1"})
         */
        public ReceiptItem {
            Objects.requireNonNull(title, "title must not be null");
        }
    }

    /**
     * MS Teams 내장 영수증·명세 카드({@code application/vnd.microsoft.card.receipt}) 생성을 위한 불변 스펙입니다.
     *
     * @param resourceId  대상 리소스 식별자 ({@code null} 불가)
     * @param title       영수증 상단 제목
     * @param facts       상단 메타데이터 키-값 목록
     * @param items       명세 품목 목록
     * @param taxText     부가세·세액 표시 문자열
     * @param totalText   최종 합계 금액 표시 문자열
     * @param buttonTitle 하단 서명 링크 버튼 캡션
     * @param action      서명 링크에 기록할 의도 액션 (생략 시 {@code "view"})
     */
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
        /**
         * 필수 인자 검증 및 방어적 불변 복사를 수행하는 정규 생성자입니다.
         *
         * @param resourceId  대상 리소스 식별자 ({@code null} 불가)
         * @param title       영수증 상단 제목
         * @param facts       상단 메타데이터 키-값 목록
         * @param items       명세 품목 목록
         * @param taxText     부가세·세액 표시 문자열
         * @param totalText   최종 합계 금액 표시 문자열
         * @param buttonTitle 하단 서명 링크 버튼 캡션
         * @param action      서명 링크에 기록할 의도 액션 (생략 시 {@code "view"})
         */
        public ReceiptCardSpec {
            Objects.requireNonNull(resourceId, "resourceId must not be null");
            facts = facts != null ? List.copyOf(facts) : List.of();
            items = items != null ? List.copyOf(items) : List.of();
            action = (action != null && !action.isBlank()) ? action : "view";
        }
    }

    /**
     * MS Teams 내장 히어로 카드({@code application/vnd.microsoft.card.hero}) 생성을 위한 불변 스펙입니다.
     *
     * @param resourceId           대상 리소스 식별자 ({@code null} 불가)
     * @param title                카드 주 제목
     * @param subtitle             카드 부제목
     * @param text                 요약 본문 텍스트
     * @param primaryButtonTitle   첫 번째 서명 링크 버튼 제목
     * @param primaryAction        첫 번째 버튼의 의도 액션 (생략 시 {@code "view"})
     * @param secondaryButtonTitle 두 번째 서명 링크 버튼 제목 (생략 또는 공백이면 버튼 1개만 생성)
     * @param secondaryAction      두 번째 버튼의 의도 액션
     */
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
        /**
         * 필수 인자 검증 및 방어적 불변 복사를 수행하는 정규 생성자입니다.
         *
         * @param resourceId           대상 리소스 식별자 ({@code null} 불가)
         * @param title                카드 주 제목
         * @param subtitle             카드 부제목
         * @param text                 요약 본문 텍스트
         * @param primaryButtonTitle   첫 번째 서명 링크 버튼 제목
         * @param primaryAction        첫 번째 버튼의 의도 액션 (생략 시 {@code "view"})
         * @param secondaryButtonTitle 두 번째 서명 링크 버튼 제목 (생략 또는 공백이면 버튼 1개만 생성)
         * @param secondaryAction      두 번째 버튼의 의도 액션
         */
        public HeroCardSpec {
            Objects.requireNonNull(resourceId, "resourceId must not be null");
            primaryAction = (primaryAction != null && !primaryAction.isBlank()) ? primaryAction : "view";
        }
    }

    private final TeamsLinkSigner linkSigner;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    /**
     * 기본 Jackson {@link ObjectMapper}와 시스템 UTC 시계를 사용하여 포맷 팩토리를 생성합니다.
     *
     * @param linkSigner 링크 서명기 ({@code null} 불가)
     */
    public TeamsFormatFactory(TeamsLinkSigner linkSigner) {
        this(linkSigner, createDefaultObjectMapper(), Clock.systemUTC());
    }

    /**
     * 지정된 {@link ObjectMapper}와 시스템 UTC 시계를 사용하여 포맷 팩토리를 생성합니다.
     *
     * @param linkSigner   링크 서명기 ({@code null} 불가)
     * @param objectMapper JSON 포맷팅에 사용할 매퍼 ({@code null}이면 기본 매퍼 생성)
     */
    public TeamsFormatFactory(TeamsLinkSigner linkSigner, ObjectMapper objectMapper) {
        this(linkSigner, objectMapper, Clock.systemUTC());
    }

    /**
     * 모든 의존성을 명시적으로 주입받아 포맷 팩토리를 생성합니다.
     *
     * @param linkSigner   링크 서명기 ({@code null} 불가)
     * @param objectMapper JSON 포맷팅에 사용할 매퍼 ({@code null}이면 기본 매퍼 생성)
     * @param clock        생성 시각({@code generatedAt}) 기록용 시계 ({@code null} 불가)
     */
    public TeamsFormatFactory(TeamsLinkSigner linkSigner, ObjectMapper objectMapper, Clock clock) {
        this.linkSigner = Objects.requireNonNull(linkSigner, "linkSigner must not be null");
        this.objectMapper = objectMapper != null ? objectMapper : createDefaultObjectMapper();
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * 리치 HTML 표({@code <table>})와 사용자 {@code @멘션}, 서명된 링크가 포함된 메시지 페이로드를 생성합니다.
     *
     * @param spec HTML 메시지 스펙 ({@code null} 불가)
     * @return Graph API 및 Workflows용 페이로드가 담긴 {@link FormatMessage}
     */
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

    /**
     * MS Teams 활동 피드(🔔) 벨 푸시 알림 페이로드를 생성합니다.
     *
     * @param spec 활동 피드 알림 스펙 ({@code null} 불가)
     * @return Graph {@code sendActivityNotification} 및 Workflows 피드 알림 규격의 {@link FormatMessage}
     */
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

    /**
     * {@code importance: "urgent"} 반복 푸시와 {@code @팀태그} 멘션이 결합된 긴급 경보 페이로드를 생성합니다.
     *
     * @param spec 긴급 경보 스펙 ({@code null} 불가)
     * @return Graph 긴급 메시지 및 Workflows 팀 태그 연동 규격의 {@link FormatMessage}
     */
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

    /**
     * MS Teams 내장 영수증 카드({@code application/vnd.microsoft.card.receipt}) 페이로드를 생성합니다.
     *
     * @param spec 영수증 카드 스펙 ({@code null} 불가)
     * @return Bot / Outgoing Webhook / Graph 첨부 규격의 {@link FormatMessage}
     */
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

    /**
     * MS Teams 내장 히어로 카드({@code application/vnd.microsoft.card.hero}) 페이로드를 생성합니다.
     *
     * @param spec 히어로 카드 스펙 ({@code null} 불가)
     * @return Bot / Outgoing Webhook / Graph 첨부 규격의 {@link FormatMessage}
     */
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

    /**
     * 이미 조립된 {@link CardBuildResult}를 다른 알림 포맷과 동일한 {@link FormatMessage} 구조로 래핑합니다.
     *
     * @param resourceId 대상 리소스 식별자 ({@code null} 불가)
     * @param builtCard  조립 완료된 Adaptive Card 결과 ({@code null} 불가)
     * @return {@link TeamsFormat#ADAPTIVE_CARD} 유형의 {@link FormatMessage}
     */
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
