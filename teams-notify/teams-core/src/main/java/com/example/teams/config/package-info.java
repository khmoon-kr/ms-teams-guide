/**
 * MS Teams 연동 클라이언트의 불변(Immutable) 런타임 설정을 정의하는 패키지입니다.
 *
 * <h2>핵심 설계 원칙</h2>
 * <ul>
 *   <li><b>불변성 보장</b>: {@link com.example.teams.config.TeamsSettings}는 생성 이후 내부 필드가 변경되지 않으므로
 *       멀티스레드 환경에서 안전하게 공유됩니다.</li>
 *   <li><b>생성 시점 Fail-Fast 검증</b>: {@code linkSecret}이 비어 있거나 최소 보안 길이(16바이트) 미만이면
 *       {@link com.example.teams.config.TeamsSettings.Builder#build()} 호출 시 즉시 {@link java.lang.IllegalArgumentException}을 발생시켜
 *       취약한 설정으로 애플리케이션이 기동되는 것을 차단합니다.</li>
 *   <li><b>Dry-Run 및 메모리 상한 지원</b>: {@code webhookUrl}이 비어 있으면 네트워크 호출 대신 인메모리 Outbox에만 기록하며,
 *       실서비스 운영 환경에서는 {@code maxOutboxSize(0)}으로 설정하여 메모리 적재를 완전히 비활성화할 수 있습니다.</li>
 * </ul>
 *
 * @see com.example.teams.config.TeamsSettings
 */
package com.example.teams.config;
