package com.example.teams.security;

import java.time.Instant;

/**
 * {@link TeamsLinkSigner}가 수행한 URL 서명 및 유효기간 검증의 상세 결과를 담는 불변 레코드입니다.
 *
 * @param status           최종 판별된 4단계 검증 상태 ({@link LinkVerificationStatus})
 * @param message          검증 성공 또는 실패 사유를 설명하는 사용자·운영자 안내 메시지
 * @param source           요청 쿼리에 전달된 유입 출처 값 (정상일 경우 {@code "msteams"}, 직접 접속 시 {@code "direct"})
 * @param cardType         알림 발송 시 서명에 포함된 알림·카드 유형 식별자 (예: {@code "ORDER_CREATED"}, {@code "RECEIPT_CARD"})
 * @param action           링크에 부여된 의도 액션 식별자 (예: {@code "view"}, {@code "approve"})
 * @param issuedAt         링크 서명 발급 시각 ({@code iat} Epoch 초 변환값, 직접 접속 시 {@code null})
 * @param expiresAt        링크 서명 만료 시각 ({@code exp} Epoch 초 변환값, 직접 접속 시 {@code null})
 * @param canonicalPayload {@code HMAC-SHA256} 계산에 사용된 정규화 원문 문자열 (디버깅·감사 로그용, 누락 시 {@code null})
 * @see TeamsLinkSigner#verify(String, String, String, String, Long, Long, String)
 * @see TeamsLinkSigner#verifyQueryMap(String, java.util.Map)
 */
public record LinkVerificationResult(
        LinkVerificationStatus status,
        String message,
        String source,
        String cardType,
        String action,
        Instant issuedAt,
        Instant expiresAt,
        String canonicalPayload
) {
    /**
     * 서명과 만료 시각이 모두 유효한 정식 MS Teams 링크 유입({@link LinkVerificationStatus#VERIFIED_TEAMS})인지 확인합니다.
     *
     * @return 검증 통과 시 {@code true}, 직접 접속·만료·서명 불일치 시 {@code false}
     */
    public boolean isVerified() {
        return status == LinkVerificationStatus.VERIFIED_TEAMS;
    }
}
