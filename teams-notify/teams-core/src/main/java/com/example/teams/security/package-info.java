/**
 * MS Teams 알림 내 외부 이동 링크의 유입 진위 검증({@code HMAC-SHA256}) 및 Outgoing Webhook 요청 서명 검증을 담당하는 보안 패키지입니다.
 *
 * <h2>왜 서명된 링크가 필요한가요?</h2>
 * <p>사용자가 MS Teams 데스크톱·모바일 클라이언트의 카드 버튼({@code Action.OpenUrl})이나 HTML 링크를 클릭하면
 * 운영체제 기본 브라우저가 실행되면서 HTTP {@code Referer} 헤더가 전달되지 않습니다.
 * 따라서 단순 쿼리 파라미터({@code ?from=teams})만으로는 실제 Teams 알림을 클릭해 들어온 정상 접근인지,
 * 외부에서 URL을 추측·변조해 접속한 것인지 구분할 수 없습니다.</p>
 *
 * <h2>검증 메커니즘</h2>
 * <ul>
 *   <li>{@link com.example.teams.security.TeamsLinkSigner}는 알림 생성 시점에
 *       {@code resourceId|src|cardType|action|iat|exp} 정규화 문자열을 비밀키로 {@code HMAC-SHA256} 서명하여 URL에 부착합니다.</li>
 *   <li>수신 웹 서버 또는 API 게이트웨이는 {@link java.security.MessageDigest#isEqual(byte[], byte[])} 상수 시간 비교로
 *       위변조 여부와 만료 시각({@code exp})을 검증하여 {@link com.example.teams.security.LinkVerificationStatus} 4단계 상태를 판별합니다.</li>
 *   <li>{@link com.example.teams.security.OutgoingWebhookVerifier}는 Teams 표준 채널 {@code @멘션} 호출 시
 *       {@code Authorization: HMAC <base64>} 헤더를 검증합니다.</li>
 * </ul>
 *
 * @see com.example.teams.security.TeamsLinkSigner
 * @see com.example.teams.security.LinkVerificationResult
 * @see com.example.teams.security.LinkVerificationStatus
 * @see com.example.teams.security.OutgoingWebhookVerifier
 */
package com.example.teams.security;
