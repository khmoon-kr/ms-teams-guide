package com.example.teams.service;

import com.example.teams.TeamsNotifier;
import com.example.teams.client.TeamsWebhookClient.TeamsDispatchRecord;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MS Teams Adaptive Card v1.4로 구현할 수 있는 6가지 서로 다른 시각적 레이아웃 스타일을 생성하고
 * 1번 웹후크({@code teams.webhook-url})로 발송하는 데모 쇼케이스 서비스입니다.
 */
@Service
public class AdaptiveCardShowcaseService {

    public record CardStyleInfo(
            String id,
            String name,
            String badge,
            String description
    ) {}

    private static final List<CardStyleInfo> STYLES = List.of(
            new CardStyleInfo("kpi-dashboard", "KPI 3단 대시보드형", "ColumnSet · Full Width",
                    "일일 매출·승인율·장애 건수를 3단 컬러 KPI 박스로 배치한 와이드 대시보드 카드"),
            new CardStyleInfo("receipt", "영수증 · 품목 명세서형", "좌우 정렬 · 합계 구분선",
                    "품목별 금액을 좌/우로 정렬하고 공급가액·VAT·총액을 분리한 영수증 스타일 카드"),
            new CardStyleInfo("incident-timeline", "장애 워룸 · 타임라인형", "Bleed 배너 · ShowCard",
                    "상단 풀-블리드 경고 배너, 시간대별 타임라인, 접이식 대응 런북을 담은 장애 대응 카드"),
            new CardStyleInfo("approval-form", "접이식 승인/반려 폼형", "ShowCard · Input 폼",
                    "버튼 클릭 시 카드 안에서 코멘트·사유 선택 입력 폼이 펼쳐지는 인터랙티브 승인 카드"),
            new CardStyleInfo("release-changelog", "배포 공지 · 릴리즈 노트형", "Markdown · 2열 메타",
                    "버전·환경 배지와 마크다운 변경 내역 불릿, 모니터링 링크를 담은 배포 공지 카드"),
            new CardStyleInfo("compact-digest", "컴팩트 다건 요약 리스트형", "멀티행 리스트 · 알림압축",
                    "여러 건의 이벤트를 1장의 카드에 표 형태로 압축해 알림 피로를 줄이는 다이제스트 카드")
    );

    private final TeamsNotifier teamsNotifier;

    public AdaptiveCardShowcaseService(TeamsNotifier teamsNotifier) {
        this.teamsNotifier = teamsNotifier;
    }

    public List<CardStyleInfo> listStyles() {
        return STYLES;
    }

    public TeamsDispatchRecord sendStyle(String styleId) {
        String normalized = styleId != null ? styleId.trim().toLowerCase() : "";
        String nowKst = teamsNotifier.cards().formatTimestamp(Instant.now());
        String sampleTxId = "TX-20261119-1000";
        String signedUrl = teamsNotifier.createSignedUrl(sampleTxId, "STYLE_" + normalized.toUpperCase().replace('-', '_'), "view");

        Map<String, Object> card = switch (normalized) {
            case "kpi-dashboard" -> buildKpiDashboardCard(nowKst, signedUrl);
            case "receipt" -> buildReceiptStyleCard(nowKst, signedUrl);
            case "incident-timeline" -> buildIncidentTimelineCard(nowKst, signedUrl);
            case "approval-form" -> buildApprovalFormCard(nowKst, signedUrl);
            case "release-changelog" -> buildReleaseChangelogCard(nowKst, signedUrl);
            case "compact-digest" -> buildCompactDigestCard(nowKst, signedUrl);
            default -> throw new IllegalArgumentException("지원하지 않는 카드 스타일입니다: " + styleId);
        };

        CardStyleInfo info = STYLES.stream()
                .filter(s -> s.id().equals(normalized))
                .findFirst()
                .orElseThrow();

        return teamsNotifier.sendRawPayload(
                "STYLE-" + normalized.toUpperCase(),
                "ADAPTIVE_STYLE_" + normalized.toUpperCase().replace('-', '_'),
                "[스타일 쇼케이스] " + info.name(),
                wrapEnvelope(card)
        );
    }

    public List<TeamsDispatchRecord> sendAllStyles() {
        return STYLES.stream()
                .map(style -> sendStyle(style.id()))
                .toList();
    }

    // -------------------------------------------------------------------------
    // 1. KPI 3단 대시보드형 (Full Width + 3단 컬러 Container)
    // -------------------------------------------------------------------------
    private Map<String, Object> buildKpiDashboardCard(String nowKst, String signedUrl) {
        return adaptiveCard(true, List.of(
                container("emphasis", true, List.of(
                        columnSet(List.of(
                                column("stretch", List.of(
                                        text("BILLING DAILY KPI", "Small", "Bolder", "Accent", false),
                                        text("📊 일일 결제 운영 대시보드", "Large", "Bolder", null, true)
                                )),
                                column("auto", List.of(
                                        text("기준: " + nowKst, "Small", null, null, false, true, "Right")
                                ))
                        ))
                )),
                columnSet(List.of(
                        column("stretch", List.of(
                                container("good", false, List.of(
                                        text("오늘 승인 총액", "Small", null, null, false, true, null),
                                        text("₩48.2M", "ExtraLarge", "Bolder", "Good", false),
                                        text("▲ 전일 대비 +12.4%", "Small", "Bolder", "Good", false)
                                ))
                        )),
                        column("stretch", List.of(
                                container("accent", false, List.of(
                                        text("PG 승인 성공률", "Small", null, null, false, true, null),
                                        text("99.42%", "ExtraLarge", "Bolder", "Accent", false),
                                        text("총 1,284건 중 1,277건", "Small", null, null, false, true, null)
                                ))
                        )),
                        column("stretch", List.of(
                                container("attention", false, List.of(
                                        text("매입 실패 / 지연", "Small", null, null, false, true, null),
                                        text("7건", "ExtraLarge", "Bolder", "Attention", false),
                                        text("자동 재시도 4건 대기", "Small", "Bolder", "Attention", false)
                                ))
                        ))
                )),
                container("default", false, List.of(
                        text("PG사별 응답 현황", "Small", "Bolder", null, false),
                        columnSet(List.of(
                                column("stretch", List.of(text("• 토스페이먼츠: **142ms** (정상)", "Small", null, null, true))),
                                column("stretch", List.of(text("• KCP: **189ms** (정상)", "Small", null, null, true))),
                                column("stretch", List.of(text("• 이니시스: **612ms** (주의)", "Small", null, "Warning", true)))
                        ))
                ))
        ), List.of(
                openUrlAction("대시보드 상세 열기", signedUrl, "positive")
        ));
    }

    // -------------------------------------------------------------------------
    // 2. 영수증 · 품목 명세서형 (좌우 정렬 + 구분선 + 합계)
    // -------------------------------------------------------------------------
    private Map<String, Object> buildReceiptStyleCard(String nowKst, String signedUrl) {
        return adaptiveCard(false, List.of(
                columnSet(List.of(
                        column("stretch", List.of(
                                text("🧾 전자 결제 영수증", "Large", "Bolder", null, false),
                                text("클라우드 빌링 엔터프라이즈 · ORD-20261124-0042", "Small", null, null, true, true, null)
                        )),
                        column("auto", List.of(
                                text("결제완료", "Small", "Bolder", "Good", false, false, "Right")
                        ))
                )),
                receiptRow("Enterprise 플랜 (월간 구독 × 1)", "₩100,000", false, true),
                receiptRow("트래픽 초과 사용량 (120GB)", "₩16,364", false, false),
                receiptRow("공급가액", "₩116,364", false, true),
                receiptRow("부가세 (VAT 10%)", "₩11,636", false, false),
                container("emphasis", false, List.of(
                        columnSet(List.of(
                                column("stretch", List.of(text("총 결제 금액", "Medium", "Bolder", null, false))),
                                column("auto", List.of(text("₩128,000", "ExtraLarge", "Bolder", "Accent", false, false, "Right")))
                        ))
                )),
                text("결제수단: 현대카드 (4009-****-****-1190) · 일시: " + nowKst, "Small", null, null, true, true, null)
        ), List.of(
                openUrlAction("영수증 원장 확인", signedUrl, null)
        ));
    }

    // -------------------------------------------------------------------------
    // 3. 장애 워룸 · 타임라인 & 접이식 런북형
    // -------------------------------------------------------------------------
    private Map<String, Object> buildIncidentTimelineCard(String nowKst, String signedUrl) {
        return adaptiveCard(true, List.of(
                container("attention", true, List.of(
                        text("🚨 [P1 장애 경보] PG 매입 게이트웨이 타임아웃 급증", "Large", "Bolder", "Attention", true),
                        text("발생 시각: " + nowKst + " · 영향 범위: 카드 자동 매입 배치 (prod-billing-02)", "Small", null, null, true)
                )),
                text("**진행 타임라인**", "Medium", "Bolder", null, false),
                timelineRow("14:02", "🔴", "PG 응답 지연 5초 초과 감지 (실패율 18.4%)"),
                timelineRow("14:04", "🟡", "서킷 브레이커 OPEN — 자동 재시도 큐로 우회 전환"),
                timelineRow("14:07", "🔵", "온콜 담당자(@김민수) 워룸 진입 및 PG사 상태 확인 중"),
                factSet(List.of(
                        fact("인시던트 ID", "INC-20261124-09"),
                        fact("영향 거래 수", "23건 (자동 재시도 대기 중)")
                ))
        ), List.of(
                showCardAction("📖 대응 런북 펼치기", adaptiveCard(false, List.of(
                        text("**1단계**: PG 상태 페이지 장애 공지 여부 확인\n**2단계**: `billing-capture-worker` 동시성 10 → 3으로 축소\n**3단계**: 정상화 후 DLQ 일괄 재처리 실행",
                                "Small", null, null, true)
                ), List.of())),
                openUrlAction("운영 콘솔 바로가기", signedUrl, "destructive")
        ));
    }

    // -------------------------------------------------------------------------
    // 4. 접이식 승인/반려 폼형 (Action.ShowCard + Input.Text + Input.ChoiceSet)
    // -------------------------------------------------------------------------
    private Map<String, Object> buildApprovalFormCard(String nowKst, String signedUrl) {
        return adaptiveCard(false, List.of(
                columnSet(List.of(
                        column("auto", List.of(
                                Map.of(
                                        "type", "Image",
                                        "url", "https://adaptivecards.io/content/cats/1.png",
                                        "size", "Small",
                                        "style", "Person",
                                        "altText", "요청자 프로필"
                                )
                        )),
                        column("stretch", List.of(
                                text("고액 환불 승인 요청", "Medium", "Bolder", "Warning", false),
                                text("요청자: CS팀 이영희 · " + nowKst, "Small", null, null, true, true, null)
                        ))
                )),
                container("warning", false, List.of(
                        text("**요청 금액: ₩650,000** (부분 환불 · 50만 원 이상 승인 필수)", "Default", null, null, true),
                        text("사유: 고객사 요금제 다운그레이드 요청에 따른 잔여 기간 일할 환불", "Small", null, null, true)
                )),
                factSet(List.of(
                        fact("거래번호", "TX-20261119-1000"),
                        fact("가맹점", "클라우드 빌링 엔터프라이즈")
                ))
        ), List.of(
                showCardAction("✅ 승인 의견 작성", adaptiveCard(false, List.of(
                        Map.of("type", "Input.Text", "id", "approveComment", "label", "승인 코멘트 (선택)", "placeholder", "예: 계약서 확인 완료, 환불 승인합니다.", "isMultiline", true)
                ), List.of(
                        openUrlAction("승인 페이지에서 확정하기", signedUrl, "positive")
                ))),
                showCardAction("❌ 반려 사유 선택", adaptiveCard(false, List.of(
                        Map.of(
                                "type", "Input.ChoiceSet",
                                "id", "rejectReason",
                                "label", "반려 사유",
                                "style", "compact",
                                "value", "POLICY",
                                "choices", List.of(
                                        Map.of("title", "환불 정책 기한 초과", "value", "POLICY"),
                                        Map.of("title", "증빙 서류 부족", "value", "DOCS"),
                                        Map.of("title", "금액 산정 오류", "value", "AMOUNT")
                                )
                        ),
                        Map.of("type", "Input.Text", "id", "rejectNote", "label", "상세 사유", "placeholder", "반려 상세 내용을 입력하세요.")
                ), List.of(
                        openUrlAction("반려 페이지에서 확정하기", signedUrl, "destructive")
                ))),
                openUrlAction("원장 상세 보기", signedUrl, null)
        ));
    }

    // -------------------------------------------------------------------------
    // 5. 배포 공지 · 릴리즈 노트형 (Markdown + 2열 메타)
    // -------------------------------------------------------------------------
    private Map<String, Object> buildReleaseChangelogCard(String nowKst, String signedUrl) {
        return adaptiveCard(true, List.of(
                container("accent", true, List.of(
                        text("🚀 Production 배포 완료 — teams-notify v0.2.0", "Large", "Bolder", "Accent", true),
                        text("배포 시각: " + nowKst + " · 무중단 롤링 배포 완료 (소요 1분 42초)", "Small", null, null, true)
                )),
                columnSet(List.of(
                        column("stretch", List.of(
                                text("환경", "Small", null, null, false, true, null),
                                text("**Production (ap-northeast-2)**", "Default", null, null, false)
                        )),
                        column("stretch", List.of(
                                text("커밋 / 배포자", "Small", null, null, false, true, null),
                                text("`de69877` · 빌링플랫폼팀", "Default", null, null, false)
                        ))
                )),
                container("emphasis", false, List.of(
                        text("**주요 변경 내역**", "Small", "Bolder", null, false),
                        text("- **신규**: 1번(카드)·2번(HTML 메시지) 듀얼 웹후크 동시 지원\n- **개선**: Adaptive Card 6종 스타일 쇼케이스 API 및 웹 UI 추가\n- **수정**: 서명 링크 만료 시각 KST 포맷팅 정합성 보완",
                                "Small", null, null, true)
                ))
        ), List.of(
                openUrlAction("운영 시뮬레이터 열기", signedUrl, "positive"),
                openUrlAction("릴리즈 상세 보기", signedUrl, null)
        ));
    }

    // -------------------------------------------------------------------------
    // 6. 컴팩트 다건 요약 리스트형 (멀티행 다이제스트)
    // -------------------------------------------------------------------------
    private Map<String, Object> buildCompactDigestCard(String nowKst, String signedUrl) {
        return adaptiveCard(true, List.of(
                columnSet(List.of(
                        column("stretch", List.of(
                                text("📋 최근 1시간 주요 결제 이벤트 요약 (4건)", "Medium", "Bolder", null, true)
                        )),
                        column("auto", List.of(
                                text(nowKst, "Small", null, null, false, true, "Right")
                        ))
                )),
                digestRow("🟢 승인", "TX-1001 · 클라우드 정기결제", "₩240,000", "Good", true),
                digestRow("🔴 실패", "TX-1002 · PG 타임아웃 재시도", "₩85,000", "Attention", true),
                digestRow("🟡 대기", "TX-1003 · 고액 환불 승인 대기", "₩650,000", "Warning", true),
                digestRow("🔵 완료", "TX-1004 · 부분 환불 처리 완료", "₩50,000", "Accent", true)
        ), List.of(
                openUrlAction("전체 거래 목록 확인", signedUrl, null)
        ));
    }

    // -------------------------------------------------------------------------
    // Adaptive Card v1.4 JSON 조립 헬퍼
    // -------------------------------------------------------------------------
    private Map<String, Object> wrapEnvelope(Map<String, Object> adaptiveCard) {
        Map<String, Object> attachment = new LinkedHashMap<>();
        attachment.put("contentType", "application/vnd.microsoft.card.adaptive");
        attachment.put("contentUrl", null);
        attachment.put("content", adaptiveCard);

        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("type", "message");
        envelope.put("attachments", List.of(attachment));
        return envelope;
    }

    private Map<String, Object> adaptiveCard(boolean fullWidth, List<Object> body, List<Object> actions) {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("$schema", "http://adaptivecards.io/schemas/adaptive-card.json");
        card.put("type", "AdaptiveCard");
        card.put("version", "1.4");
        if (fullWidth) {
            card.put("msteams", Map.of("width", "Full"));
        }
        card.put("body", body);
        if (actions != null && !actions.isEmpty()) {
            card.put("actions", actions);
        }
        return card;
    }

    private Map<String, Object> container(String style, boolean bleed, List<Object> items) {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("type", "Container");
        if (style != null) {
            c.put("style", style);
        }
        if (bleed) {
            c.put("bleed", true);
        }
        c.put("items", items);
        return c;
    }

    private Map<String, Object> columnSet(List<Object> columns) {
        Map<String, Object> cs = new LinkedHashMap<>();
        cs.put("type", "ColumnSet");
        cs.put("columns", columns);
        return cs;
    }

    private Map<String, Object> column(String width, List<Object> items) {
        Map<String, Object> col = new LinkedHashMap<>();
        col.put("type", "Column");
        col.put("width", width);
        col.put("items", items);
        return col;
    }

    private Map<String, Object> text(String value, String size, String weight, String color, boolean wrap) {
        return text(value, size, weight, color, wrap, false, null);
    }

    private Map<String, Object> text(
            String value,
            String size,
            String weight,
            String color,
            boolean wrap,
            boolean subtle,
            String horizontalAlignment
    ) {
        Map<String, Object> tb = new LinkedHashMap<>();
        tb.put("type", "TextBlock");
        tb.put("text", value);
        if (size != null) {
            tb.put("size", size);
        }
        if (weight != null) {
            tb.put("weight", weight);
        }
        if (color != null) {
            tb.put("color", color);
        }
        if (wrap) {
            tb.put("wrap", true);
        }
        if (subtle) {
            tb.put("isSubtle", true);
        }
        if (horizontalAlignment != null) {
            tb.put("horizontalAlignment", horizontalAlignment);
        }
        return tb;
    }

    private Map<String, Object> factSet(List<Map<String, String>> facts) {
        Map<String, Object> fs = new LinkedHashMap<>();
        fs.put("type", "FactSet");
        fs.put("facts", facts);
        return fs;
    }

    private Map<String, String> fact(String title, String value) {
        Map<String, String> f = new LinkedHashMap<>();
        f.put("title", title);
        f.put("value", value);
        return f;
    }

    private Map<String, Object> receiptRow(String label, String amount, boolean bold, boolean separator) {
        Map<String, Object> cs = columnSet(List.of(
                column("stretch", List.of(text(label, "Small", bold ? "Bolder" : null, null, true))),
                column("auto", List.of(text(amount, "Small", bold ? "Bolder" : null, null, false, false, "Right")))
        ));
        if (separator) {
            cs.put("separator", true);
        }
        return cs;
    }

    private Map<String, Object> timelineRow(String time, String icon, String desc) {
        Map<String, Object> cs = columnSet(List.of(
                column("55px", List.of(text(time, "Small", "Bolder", null, false, true, null))),
                column("auto", List.of(text(icon, "Small", null, null, false))),
                column("stretch", List.of(text(desc, "Small", null, null, true)))
        ));
        cs.put("spacing", "Small");
        return cs;
    }

    private Map<String, Object> digestRow(String badge, String detail, String amount, String color, boolean separator) {
        Map<String, Object> cs = columnSet(List.of(
                column("68px", List.of(text(badge, "Small", "Bolder", color, false))),
                column("stretch", List.of(text(detail, "Small", null, null, true))),
                column("auto", List.of(text(amount, "Small", "Bolder", null, false, false, "Right")))
        ));
        if (separator) {
            cs.put("separator", true);
        }
        return cs;
    }

    private Map<String, Object> openUrlAction(String title, String url, String style) {
        Map<String, Object> act = new LinkedHashMap<>();
        act.put("type", "Action.OpenUrl");
        act.put("title", title);
        act.put("url", url);
        if (style != null) {
            act.put("style", style);
        }
        return act;
    }

    private Map<String, Object> showCardAction(String title, Map<String, Object> innerCard) {
        Map<String, Object> act = new LinkedHashMap<>();
        act.put("type", "Action.ShowCard");
        act.put("title", title);
        act.put("card", innerCard);
        return act;
    }
}
