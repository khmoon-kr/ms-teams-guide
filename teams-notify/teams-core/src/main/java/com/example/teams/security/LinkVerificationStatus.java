package com.example.teams.security;

public enum LinkVerificationStatus {
    VERIFIED_TEAMS("Teams 공식 링크 검증 완료", true),
    DIRECT_UNVERIFIED("일반 직접 접속 (Teams 링크 서명 없음)", false),
    EXPIRED("만료된 Teams 카드 링크", false),
    INVALID_SIGNATURE("서명 불일치 / 파라미터 위변조 감지", false);

    private final String badgeLabel;
    private final boolean trustedTeamsEntry;

    LinkVerificationStatus(String badgeLabel, boolean trustedTeamsEntry) {
        this.badgeLabel = badgeLabel;
        this.trustedTeamsEntry = trustedTeamsEntry;
    }

    public String getBadgeLabel() {
        return badgeLabel;
    }

    public boolean isTrustedTeamsEntry() {
        return trustedTeamsEntry;
    }
}
