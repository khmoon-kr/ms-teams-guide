/**
 * MS Teams Workflows Incoming Webhook 엔드포인트로 JSON 페이로드를 전송하는 HTTP 클라이언트 패키지입니다.
 *
 * <h2>동작 특징 및 주의사항</h2>
 * <ul>
 *   <li><b>순수 JDK 전송 계층</b>: Spring {@code RestClient}/{@code WebClient} 없이 표준 JDK 17
 *       {@link java.net.http.HttpClient}만을 사용하여 외부 전이 의존성을 최소화합니다.</li>
 *   <li><b>Power Automate 웹훅 헤더 호환성</b>: MS Teams Workflows의 기본 {@code Anyone} 트리거는 요청에
 *       {@code Authorization} 헤더가 포함될 경우 인증 방식 충돌로 요청을 거부하므로,
 *       {@link com.example.teams.client.TeamsWebhookClient}는 오직 {@code Content-Type: application/json; charset=UTF-8} 헤더만 전송합니다.</li>
 *   <li><b>장애 격리 및 민감정보 마스킹</b>: 연결·응답 타임아웃을 강제하고, 전송 실패 시 예외를 외부로 전파해 호출 측 본체 트랜잭션을 깨뜨리는 대신
 *       상태 코드와 마스킹된 오류 요약({@code [REDACTED_URL]})을 {@link com.example.teams.client.TeamsWebhookClient.TeamsDispatchRecord}로 반환합니다.</li>
 * </ul>
 *
 * @see com.example.teams.client.TeamsWebhookClient
 */
package com.example.teams.client;
