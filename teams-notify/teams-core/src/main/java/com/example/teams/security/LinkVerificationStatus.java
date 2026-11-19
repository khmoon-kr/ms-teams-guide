package com.example.teams.security;

/**
 * MS Teams 알림 내 링크를 통해 웹 시스템에 진입했을 때의 서명 검증 결과 상태입니다.
 *
 * <p>웹 컨트롤러나 인터셉터에서는 {@link #isTrustedTeamsEntry()}(또는
 * {@link LinkVerificationResult#isVerified()})가 {@code true}일 때만
 * 민감한 운영 액션(승인, 반려, 설정 변경 등)을 허용하고, 그 외 상태에서는 읽기 전용 조회만 허용하거나
 * 재인증을 요구하는 방식으로 활용할 수 있습니다.</p>
 *
 * @see TeamsLinkSigner
 * @see LinkVerificationResult
 */
public enum LinkVerificationStatus {

    /**
     * {@code src=msteams} 출처 확인, 상수 시간 {@code HMAC-SHA256} 서명 일치, 유효기간({@code exp}) 이내 조건을
     * 모두 통과한 정상 Teams 알림 유입 상태입니다.
     */
    VERIFIED_TEAMS("Teams 공식 링크 검증 완료", true),

    /**
     * 서명 관련 쿼리 파라미터({@code src}, {@code sig}, {@code iat}, {@code exp}) 없이
     * 브라우저 주소창 입력이나 일반 북마크를 통해 직접 접속한 상태입니다.
     */
    DIRECT_UNVERIFIED("일반 직접 접속 (Teams 링크 서명 없음)", false),

    /**
     * {@code HMAC-SHA256} 서명 자체는 유효하지만 발급 시 지정된 만료 시각({@code exp})이 경과한 상태입니다.
     */
    EXPIRED("만료된 Teams 카드 링크", false),

    /**
     * 필수 검증 파라미터가 누락되었거나, URL의 식별자·액션·만료시각·서명({@code sig}) 중 하나라도
     * 임의로 수정되어 서명이 일치하지 않는 위변조 의심 상태입니다.
     */
    INVALID_SIGNATURE("서명 불일치 / 파라미터 위변조 감지", false);

    private final String badgeLabel;
    private final boolean trustedTeamsEntry;

    LinkVerificationStatus(String badgeLabel, boolean trustedTeamsEntry) {
        this.badgeLabel = badgeLabel;
        this.trustedTeamsEntry = trustedTeamsEntry;
    }

    /**
     * UI 배너나 감사 로그에 바로 표시할 수 있는 한국어 상태 요약 문구를 반환합니다.
     *
     * @return 한국어 상태 라벨
     */
    public String getBadgeLabel() {
        return badgeLabel;
    }

    /**
     * 신뢰할 수 있는 정식 MS Teams 서명 링크 유입({@link #VERIFIED_TEAMS})인지 여부를 반환합니다.
     *
     * @return 정식 Teams 서명 검증 통과 시 {@code true}, 그 외 {@code false}
     */
    public boolean isTrustedTeamsEntry() {
        return trustedTeamsEntry;
    }
}
