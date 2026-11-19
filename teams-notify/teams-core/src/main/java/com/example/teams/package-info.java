/**
 * MS Teams 알림 전송, 멀티 포맷 메시지 조립, 외부 이동 링크 서명 검증을 통합 제공하는 루트 패키지입니다.
 *
 * <h2>개요</h2>
 * <p>이 패키지의 대표 진입점(Facade)인 {@link com.example.teams.TeamsNotifier}는
 * Spring 프레임워크나 특정 비즈니스 도메인에 종속되지 않는 순수 Java 17 API를 제공합니다.
 * 내부 컴포넌트들은 모두 무상태(Stateless) 또는 스레드 안전(Thread-Safe)하게 설계되어 있으므로
 * 애플리케이션 전역에서 단일 인스턴스(Singleton)로 공유하여 사용하는 것을 권장합니다.</p>
 *
 * <h2>하위 패키지 구성</h2>
 * <ul>
 *   <li>{@link com.example.teams.config} — 불변 설정 객체({@link com.example.teams.config.TeamsSettings}) 및 빌더</li>
 *   <li>{@link com.example.teams.cards} — Adaptive Card v1.4 및 6종 MS Teams 알림 포맷 선언적 조립기</li>
 *   <li>{@link com.example.teams.client} — JDK 17 {@link java.net.http.HttpClient} 기반 Workflows Incoming Webhook 전송기</li>
 *   <li>{@link com.example.teams.security} — 외부 브라우저 이동 링크용 {@code HMAC-SHA256} 서명·검증 및 Outgoing Webhook 검증기</li>
 *   <li>{@link com.example.teams.domain} — 지원하는 MS Teams 알림 포맷 카탈로그({@link com.example.teams.domain.TeamsFormat})</li>
 * </ul>
 *
 * <h2>빠른 시작 예시 (Standalone Java)</h2>
 * <pre>{@code
 * TeamsSettings settings = TeamsSettings.builder()
 *     .webhookUrl(System.getenv("TEAMS_WEBHOOK_URL"))
 *     .baseUrl("https://admin.example.com")
 *     .entryPathTemplate("/ops/orders/{id}")
 *     .linkSecret(System.getenv("TEAMS_LINK_SECRET"))
 *     .maxOutboxSize(0)
 *     .build();
 *
 * TeamsNotifier teams = TeamsNotifier.create(settings);
 * }</pre>
 *
 * @see com.example.teams.TeamsNotifier
 * @see com.example.teams.config.TeamsSettings
 */
package com.example.teams;
