/**
 * {@code teams-core} 라이브러리를 Spring Boot 3 애플리케이션에 자동 구성(Auto-Configuration)하는 스타터 패키지입니다.
 *
 * <h2>자동 구성 원칙</h2>
 * <ul>
 *   <li>{@code @ComponentScan}을 사용하지 않고 {@link com.example.teams.autoconfigure.TeamsAutoConfiguration} 내부의
 *       명시적인 {@code @Bean} 메서드로만 빈을 등록하여 소비 애플리케이션의 컴포넌트 스캔 경계를 침범하지 않습니다.</li>
 *   <li>모든 등록 빈에 {@code @ConditionalOnMissingBean}이 선언되어 있으므로, 소비 애플리케이션에서 동일 타입의 커스텀 빈을
 *       직접 등록하면 스타터의 기본 빈은 자동으로 비활성화(Back-off)됩니다.</li>
 *   <li>소비 애플리케이션 컨텍스트에 {@link java.time.Clock}, {@link com.fasterxml.jackson.databind.ObjectMapper},
 *       {@link java.net.http.HttpClient} 빈이 존재하면 {@code ObjectProvider}를 통해 우선 재사용합니다.</li>
 * </ul>
 *
 * @see com.example.teams.autoconfigure.TeamsAutoConfiguration
 * @see com.example.teams.autoconfigure.TeamsProperties
 */
package com.example.teams.autoconfigure;
