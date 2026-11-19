package com.example.teams.cards;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 도메인에 구애받지 않고 어떤 서비스에서든 일관된 디자인의 MS Teams Adaptive Card(v1.4)를
 * 선언적으로 조립할 수 있는 불변(Immutable) 스펙 객체입니다. (규칙 1 & 규칙 3.2)
 */
public final class AdaptiveCardSpec {

    public enum BannerStyle {
        DEFAULT("default"),
        EMPHASIS("emphasis"),
        GOOD("good"),
        WARNING("warning"),
        ATTENTION("attention");

        private final String wireValue;

        BannerStyle(String wireValue) {
            this.wireValue = wireValue;
        }

        public String wireValue() {
            return wireValue;
        }
    }

    public enum TextColor {
        DEFAULT("Default"),
        ACCENT("Accent"),
        GOOD("Good"),
        WARNING("Warning"),
        ATTENTION("Attention");

        private final String wireValue;

        TextColor(String wireValue) {
            this.wireValue = wireValue;
        }

        public String wireValue() {
            return wireValue;
        }
    }

    public record Fact(String title, String value) {
        public Fact {
            Objects.requireNonNull(title, "title must not be null");
            value = (value != null && !value.isBlank()) ? value : "-";
        }
    }

    public record ActionButton(String title, String url) {
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

    public static Builder builder() {
        return new Builder();
    }

    public String getEventType() {
        return eventType;
    }

    public String getSummaryText() {
        return summaryText;
    }

    public BannerStyle getBannerStyle() {
        return bannerStyle;
    }

    public String getBadgeLabel() {
        return badgeLabel;
    }

    public String getHeadline() {
        return headline;
    }

    public TextColor getHeadlineColor() {
        return headlineColor;
    }

    public String getSubtitle() {
        return subtitle;
    }

    public String getFooterText() {
        return footerText;
    }

    public List<Fact> getFacts() {
        return facts;
    }

    public List<ActionButton> getActions() {
        return actions;
    }

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

        public Builder eventType(String eventType) {
            this.eventType = eventType;
            return this;
        }

        public Builder summaryText(String summaryText) {
            this.summaryText = summaryText;
            return this;
        }

        public Builder bannerStyle(BannerStyle bannerStyle) {
            this.bannerStyle = bannerStyle;
            return this;
        }

        public Builder badgeLabel(String badgeLabel) {
            this.badgeLabel = badgeLabel;
            return this;
        }

        public Builder headline(String headline) {
            this.headline = headline;
            return this;
        }

        public Builder headlineColor(TextColor headlineColor) {
            this.headlineColor = headlineColor;
            return this;
        }

        public Builder subtitle(String subtitle) {
            this.subtitle = subtitle;
            return this;
        }

        public Builder footerText(String footerText) {
            this.footerText = footerText;
            return this;
        }

        public Builder addFact(String title, String value) {
            this.facts.add(new Fact(title, value));
            return this;
        }

        public Builder facts(List<Fact> facts) {
            this.facts.clear();
            if (facts != null) {
                this.facts.addAll(facts);
            }
            return this;
        }

        public Builder addAction(String title, String url) {
            this.actions.add(new ActionButton(title, url));
            return this;
        }

        public Builder addAction(ActionButton button) {
            this.actions.add(Objects.requireNonNull(button));
            return this;
        }

        public Builder actions(List<ActionButton> actions) {
            this.actions.clear();
            if (actions != null) {
                this.actions.addAll(actions);
            }
            return this;
        }

        public AdaptiveCardSpec build() {
            return new AdaptiveCardSpec(this);
        }
    }
}
