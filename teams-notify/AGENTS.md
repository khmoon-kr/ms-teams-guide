# `teams-notify` Library Coding Rules

이 디렉터리는 다른 서비스에서 의존성으로 가져다 쓰는 공용 MS Teams 알림 라이브러리(`teams-core`, `teams-spring-boot-starter`)와 검증용 데모 앱(`teams-demo-app`)으로 구성되어 있습니다. 코드를 추가·수정할 때 아래 규칙을 반드시 준수하세요. (상세 가이드: `../08_java_library_coding_rules.md`)

1. **도메인 중립적·간결한 네이밍**
   - `teams-core`, `teams-spring-boot-starter`의 클래스·패키지·프로퍼티 이름에 특정 도메인 접두어(`Billing...`, `Order...` 등)를 절대 붙이지 마세요.
   - 진입점은 `TeamsNotifier`, 설정 프리픽스는 `teams.*`처럼 역할 중심의 짧은 이름을 유지하세요.
2. **엄격한 모듈 경계 (`teams-core` 순수성 유지)**
   - `teams-core`에는 `org.springframework.*`, `jakarta.servlet.*` 등 프레임워크 의존성을 절대 추가하지 마세요 (`jackson-databind` + `slf4j-api` + 표준 JDK 17만 허용).
   - `teams-core`와 `teams-spring-boot-starter`의 `pom.xml`에는 `spring-boot-maven-plugin`(`repackage`)을 절대 넣지 마세요.
3. **Spring Boot Starter 규칙 (`teams-spring-boot-starter`)**
   - `@ComponentScan`을 사용하지 말고 `TeamsAutoConfiguration` 내부에 명시적 `@Bean` 메서드로만 빈을 등록하세요.
   - 모든 `@Bean` 메서드에는 `@ConditionalOnMissingBean`을 반드시 선언해 사용처 프로젝트의 빈 재정의를 허용하세요.
4. **불변성 · 스레드 안전성 · 리소스 보호**
   - 설정(`TeamsSettings`)과 입출력 모델은 불변(Immutable)으로 설계하고, 싱글톤 빈에 요청별 가변 필드를 두지 마세요.
   - `System.setProperty`, `TimeZone.setDefault` 등 JVM 전역 상태를 변경하지 마세요.
   - 모든 HTTP 요청에 타임아웃을 강제하고, 인메모리 보관 컬렉션에는 상한선(`maxOutboxSize`, `0`일 때 비활성화)을 두세요.
5. **보안 및 시간 테스트 가능성**
   - HMAC 비교 시 `MessageDigest.isEqual`을 사용하고, 시크릿 키·전체 웹훅 URL을 로그에 남기지 마세요.
   - 시간 의존 로직은 `java.time.Clock` 주입 생성자를 열어 두세요.
