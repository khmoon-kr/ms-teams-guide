package com.example.teams.cards;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 비즈니스 도메인에 구애받지 않고 어떤 서비스에서든 일관된 레이아웃의
 * MS Teams Adaptive Card(v1.4)를 선언적으로 조립할 수 있는 불변(Immutable) 스펙 객체입니다.
 *
 * <h2>사용 예시</h2>
 * <pre>{@code
 * AdaptiveCardSpec spec = AdaptiveCardSpec.builder()
 *     .eventType("DEPLOY_SUCCEEDED")
 *     .bannerStyle(AdaptiveCardSpec.BannerStyle.GOOD)
 *     .badgeLabel("DEPLOYMENT · 배포 완료")
 *     .headline("order-service v2.4.1")
 *     .headlineColor(AdaptiveCardSpec.TextColor.GOOD)
 *     .subtitle("운영 클러스터 롤링 배포가 정상 완료되었습니다.")
 *     .addFact("클러스터", "prod-ap-northeast-2")
 *     .addFact("소요 시간", "42s")
 *     .addAction("배포 이력 열기", signedUrl)
 *     .build();
 * }</pre>
 *
 * @see TeamsCardFactory#buildCard(AdaptiveCardSpec)
 * @see com.example.teams.TeamsNotifier#sendCard(String, AdaptiveCardSpec)
 */
public final class AdaptiveCardSpec {

    /**
     * 카드 최상단 헤더 컨테이너({@code Container.style})의 배경 강조 테마입니다.
     */
    public enum BannerStyle {
        /** 기본 배경 스타일 ({@code "default"}). */
        DEFAULT("default"),
        /** 은은한 회색·보라 톤 강조 스타일 ({@code "emphasis"}). */
        EMPHASIS("emphasis"),
        /** 성공·정상 완료를 나타내는 초록 톤 스타일 ({@code "good"}). */
        GOOD("good"),
        /** 주의·승인 대기를 나타내는 황색 톤 스타일 ({@code "warning"}). */
        WARNING("warning"),
        /** 오류·장애·긴급 경보를 나타내는 적색 톤 스타일 ({@code "attention"}). */
        ATTENTION("attention");

        private final String wireValue;

        BannerStyle(String wireValue) {
            this.wireValue = wireValue;
        }

        /**
         * Adaptive Card JSON 스키마에 기록되는 문자열 값을 반환합니다.
         *
         * @return 스키마 스타일 값
         */
        public String wireValue() {
            return wireValue;
        }
    }

    /**
     * 헤드라인 텍스트({@code TextBlock.color})에 적용할 시맨틱 색상입니다.
     */
    public enum TextColor {
        /** 기본 텍스트 색상 ({@code "Default"}). */
        DEFAULT("Default"),
        /** 브랜드 강조 색상 ({@code "Accent"}). */
        ACCENT("Accent"),
        /** 긍정·성공 색상 ({@code "Good"}). */
        GOOD("Good"),
        /** 경고·주의 색상 ({@code "Warning"}). */
        WARNING("Warning"),
        /** 위험·오류 색상 ({@code "Attention"}). */
        ATTENTION("Attention");

        private final String wireValue;

        TextColor(String wireValue) {
            this.wireValue = wireValue;
        }

        /**
         * Adaptive Card JSON 스키마에 기록되는 색상 문자열 값을 반환합니다.
         *
         * @return 스키마 색상 값
         */
        public String wireValue() {
            return wireValue;
        }
    }

    /**
     * Adaptive Card 본문 {@code FactSet}에 표시될 단일 키-값 항목입니다.
     *
     * @param title 항목 이름 ({@code null} 불가)
     * @param value 항목 값 ({@code null} 또는 공백이면 자동으로 {@code "-"}로 대체)
     */
    public record Fact(String title, String value) {
        /**
         * 필수 인자 검증 및 방어적 불변 복사를 수행하는 정규 생성자입니다.
         *
         * @param title 항목 이름 ({@code null} 불가)
         * @param value 항목 값 ({@code null} 또는 공백이면 자동으로 {@code "-"}로 대체)
         */
        public Fact {
            Objects.requireNonNull(title, "title must not be null");
            value = (value != null && !value.isBlank()) ? value : "-";
        }
    }

    /**
     * Adaptive Card 하단 {@code actions} 배열에 배치될 외부 링크 이동({@code Action.OpenUrl}) 버튼입니다.
     *
     * @param title 버튼 캡션 ({@code null} 불가)
     * @param url   클릭 시 열릴 전체 URL ({@code null} 불가, {@link TeamsCardFactory#signedEntryButton} 활용 권장)
     */
    public record ActionButton(String title, String url) {
        /**
         * 필수 인자 검증 및 방어적 불변 복사를 수행하는 정규 생성자입니다.
         *
         * @param title 버튼 캡션 ({@code null} 불가)
         * @param url   클릭 시 열릴 전체 URL ({@code null} 불가, {@link TeamsCardFactory#signedEntryButton} 활용 권장)
         */
        public ActionButton {
            Objects.requireNonNull(title, "title must not be null");
            Objects.requireNonNull(url, "url must not be null");
        }
    }

    private final String eventType;
    private final String summaryText;
    private final BannerStyle bannerStyle;
    private final String badgeLabel;
    private final String headline;
    private final TextColor headlineColor;
    private final String subtitle;
    private final String footerText;
    private final List<Fact> facts;
    private final List<ActionButton> actions;

    private AdaptiveCardSpec(Builder builder) {
        this.eventType = Objects.requireNonNull(builder.eventType, "eventType must not be null");
        this.badgeLabel = Objects.requireNonNull(builder.badgeLabel, "badgeLabel must not be null");
        this.headline = Objects.requireNonNull(builder.headline, "headline must not be null");
        this.summaryText = (builder.summaryText != null && !builder.summaryText.isBlank())
                ? builder.summaryText
                : badgeLabel + " · " + headline;
        this.bannerStyle = builder.bannerStyle != null ? builder.bannerStyle : BannerStyle.EMPHASIS;
        this.headlineColor = builder.headlineColor != null ? builder.headlineColor : TextColor.ACCENT;
        this.subtitle = builder.subtitle != null ? builder.subtitle : "";
        this.footerText = builder.footerText;
        this.facts = List.copyOf(builder.facts);
        this.actions = List.copyOf(builder.actions);
    }

    /**
     * 새 {@link AdaptiveCardSpec} 조립용 빌더를 생성합니다.
     *
     * @return 새 {@link Builder} 인스턴스
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 알림 이벤트 유형 식별자를 반환합니다.
     *
     * @return 이벤트 유형 문자열
     */
    public String getEventType() {
        return eventType;
    }

    /**
     * 발송 로그 및 Outbox 요약에 사용되는 한 줄 요약 문구를 반환합니다.
     *
     * @return 요약 문자열
     */
    public String getSummaryText() {
        return summaryText;
    }

    /**
     * 상단 배너 컨테이너 테마 스타일을 반환합니다.
     *
     * @return {@link BannerStyle}
     */
    public BannerStyle getBannerStyle() {
        return bannerStyle;
    }

    /**
     * 상단 배너의 소제목(카테고리 배지 문구)을 반환합니다.
     *
     * @return 배지 문구
     */
    public String getBadgeLabel() {
        return badgeLabel;
    }

    /**
     * 카드 중앙에 가장 크게 강조 표시되는 핵심 헤드라인 문구(금액, 버전, 상태 등)를 반환합니다.
     *
     * @return 헤드라인 문구
     */
    public String getHeadline() {
        return headline;
    }

    /**
     * 헤드라인 문구의 시맨틱 색상을 반환합니다.
     *
     * @return {@link TextColor}
     */
    public TextColor getHeadlineColor() {
        return headlineColor;
    }

    /**
     * 헤드라인 아래 표시되는 보조 설명 문구를 반환합니다.
     *
     * @return 보조 설명 문구 (없으면 빈 문자열)
     */
    public String getSubtitle() {
        return subtitle;
    }

    /**
     * 카드 하단에 표시되는 안내 바닥글을 반환합니다.
     *
     * @return 바닥글 문자열 ({@code null} 또는 빈 문자열이면 렌더링 생략)
     */
    public String getFooterText() {
        return footerText;
    }

    /**
     * 카드 본문 {@code FactSet}에 표시될 불변 키-값 목록을 반환합니다.
     *
     * @return 수정 불가능한 {@link Fact} 목록
     */
    public List<Fact> getFacts() {
        return facts;
    }

    /**
     * 카드 하단에 렌더링될 불변 액션 버튼 목록을 반환합니다.
     *
     * @return 수정 불가능한 {@link ActionButton} 목록
     */
    public List<ActionButton> getActions() {
        return actions;
    }

    /**
     * {@link AdaptiveCardSpec} 인스턴스를 선언적으로 조립하는 빌더입니다.
     */
    public static final class Builder {
        private String eventType = "NOTIFICATION";
        private String summaryText;
        private BannerStyle bannerStyle = BannerStyle.EMPHASIS;
        private String badgeLabel;
        private String headline;
        private TextColor headlineColor = TextColor.ACCENT;
        private String subtitle = "";
        private String footerText = "🔒 하단 버튼 링크에는 MS Teams 유입 검증용 HMAC-SHA256 서명 및 만료 시각이 포함되어 있습니다.";
        private final List<Fact> facts = new ArrayList<>();
        private final List<ActionButton> actions = new ArrayList<>();

        private Builder() {}

        /**
         * 이벤트 유형 코드를 설정합니다 (기본값 {@code "NOTIFICATION"}).
         *
         * @param eventType 이벤트 유형 식별자
         * @return 현재 빌더
         */
        public Builder eventType(String eventType) {
            this.eventType = eventType;
            return this;
        }

        /**
         * 로그 및 발송 내역 요약에 표시할 한 줄 요약문을 설정합니다.
         * 생략 시 {@code "{badgeLabel} · {headline}"} 형식으로 자동 생성됩니다.
         *
         * @param summaryText 한 줄 요약문
         * @return 현재 빌더
         */
        public Builder summaryText(String summaryText) {
            this.summaryText = summaryText;
            return this;
        }

        /**
         * 상단 배너 컨테이너의 배경 테마 스타일을 설정합니다.
         *
         * @param bannerStyle 배너 스타일
         * @return 현재 빌더
         */
        public Builder bannerStyle(BannerStyle bannerStyle) {
            this.bannerStyle = bannerStyle;
            return this;
        }

        /**
         * 상단 배너 첫 줄에 굵게 표시할 배지 라벨(필수)을 설정합니다.
         *
         * @param badgeLabel 배지 라벨 문구
         * @return 현재 빌더
         */
        public Builder badgeLabel(String badgeLabel) {
            this.badgeLabel = badgeLabel;
            return this;
        }

        /**
         * 가장 큰 글씨({@code ExtraLarge})로 강조할 핵심 헤드라인 문구(필수)를 설정합니다.
         *
         * @param headline 헤드라인 문구
         * @return 현재 빌더
         */
        public Builder headline(String headline) {
            this.headline = headline;
            return this;
        }

        /**
         * 핵심 헤드라인의 시맨틱 색상을 설정합니다.
         *
         * @param headlineColor 텍스트 색상
         * @return 현재 빌더
         */
        public Builder headlineColor(TextColor headlineColor) {
            this.headlineColor = headlineColor;
            return this;
        }

        /**
         * 헤드라인 하단의 보조 설명 문구를 설정합니다.
         *
         * @param subtitle 보조 설명
         * @return 현재 빌더
         */
        public Builder subtitle(String subtitle) {
            this.subtitle = subtitle;
            return this;
        }

        /**
         * 카드 하단 안내 문구를 설정합니다 ({@code null} 또는 빈 문자열 전달 시 바닥글을 숨김).
         *
         * @param footerText 바닥글 문구
         * @return 현재 빌더
         */
        public Builder footerText(String footerText) {
            this.footerText = footerText;
            return this;
        }

        /**
         * {@code FactSet}에 단일 키-값 항목을 순서대로 추가합니다.
         *
         * @param title 항목 이름 ({@code null} 불가)
         * @param value 항목 값
         * @return 현재 빌더
         */
        public Builder addFact(String title, String value) {
            this.facts.add(new Fact(title, value));
            return this;
        }

        /**
         * 기존 {@code FactSet} 목록을 주어진 목록으로 일괄 교체합니다.
         *
         * @param facts 교체할 {@link Fact} 목록
         * @return 현재 빌더
         */
        public Builder facts(List<Fact> facts) {
            this.facts.clear();
            if (facts != null) {
                this.facts.addAll(facts);
            }
            return this;
        }

        /**
         * 하단 액션 영역에 {@code Action.OpenUrl} 버튼을 추가합니다.
         *
         * @param title 버튼 제목 ({@code null} 불가)
         * @param url   이동할 URL ({@code null} 불가)
         * @return 현재 빌더
         */
        public Builder addAction(String title, String url) {
            this.actions.add(new ActionButton(title, url));
            return this;
        }

        /**
         * 미리 생성된 {@link ActionButton}(예: {@link TeamsCardFactory#signedEntryButton})을 추가합니다.
         *
         * @param button 추가할 액션 버튼 ({@code null} 불가)
         * @return 현재 빌더
         */
        public Builder addAction(ActionButton button) {
            this.actions.add(Objects.requireNonNull(button));
            return this;
        }

        /**
         * 기존 액션 버튼 목록을 주어진 목록으로 일괄 교체합니다.
         *
         * @param actions 교체할 {@link ActionButton} 목록
         * @return 현재 빌더
         */
        public Builder actions(List<ActionButton> actions) {
            this.actions.clear();
            if (actions != null) {
                this.actions.addAll(actions);
            }
            return this;
        }

        /**
         * 필수 필드를 검증하고 불변 {@link AdaptiveCardSpec} 인스턴스를 생성합니다.
         *
         * @return 생성된 {@link AdaptiveCardSpec}
         * @throws NullPointerException {@code eventType}, {@code badgeLabel}, {@code headline} 중 {@code null}이 있는 경우
         */
        public AdaptiveCardSpec build() {
            return new AdaptiveCardSpec(this);
        }
    }
}
