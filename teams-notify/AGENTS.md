# `teams-notify` Library Coding Rules

이 디렉터리는 다른 서비스에서 의존성으로 가져다 쓰는 공용 MS Teams 알림 라이브러리(`teams-core`, `teams-spring-boot-starter`)와 검증용 데모 앱(`teams-demo-app`)으로 구성되어 있습니다. 코드를 추가·수정할 때 아래 규칙을 반드시 준수하세요. (상세 가이드: [`./docs/CODING_RULES.md`](./docs/CODING_RULES.md))

1. **도메인 중립적·간결한 네이밍**
   - `teams-core`, `teams-spring-boot-starter`의 클래스·패키지·프로퍼티 이름 및 구현 내부에 특정 도메인 용어(`Billing...`, `Order...`, `Refund...` 등)를 절대 넣지 마세요.
   - 진입점은 `TeamsNotifier`, 설정 프리픽스는 `teams.*`처럼 역할 중심의 짧은 이름을 유지하세요.
2. **엄격한 모듈 경계 (`teams-core` 순수성 & Split Package 금지)**
   - `teams-core`에는 `org.springframework.*`, `jakarta.servlet.*` 등 프레임워크 의존성을 절대 추가하지 마세요 (`jackson-databind` + `slf4j-api` + 표준 JDK 17만 허용).
   - `teams-core`와 `teams-spring-boot-starter`의 `pom.xml`에는 `spring-boot-maven-plugin`(`repackage`)을 절대 넣지 마세요.
   - 모듈 간 패키지 중복(Split Package)이 생기지 않도록 Starter 전용 클래스는 `com.example.teams.autoconfigure`에 두세요.
3. **Spring Boot Starter 규칙 (`teams-spring-boot-starter`)**
   - `@ComponentScan`을 사용하지 말고 `TeamsAutoConfiguration` 내부에 명시적 `@Bean` 메서드로만 빈을 등록하세요.
   - 모든 `@Bean` 메서드에는 `@ConditionalOnMissingBean`을 선언하고, 소비 앱의 `Clock`, `ObjectMapper`, `HttpClient` 빈을 `ObjectProvider`로 우선 존중하세요.
4. **불변성 · 스레드 안전성 · 리소스 보호**
   - 설정(`TeamsSettings`)과 입출력 모델은 방어적 복사(`List.copyOf`, `Collections.unmodifiableMap`)를 포함한 불변 구조로 설계하세요.
   - `System.setProperty`, `TimeZone.setDefault` 등 JVM 전역 상태를 변경하지 마세요.
   - 모든 HTTP 요청에 타임아웃을 강제하고, 인메모리 보관 컬렉션에는 상한선(`maxOutboxSize`, `0`일 때 비활성화)을 두세요.
5. **보안 및 시간 테스트 가능성**
   - HMAC 비교 시 `MessageDigest.isEqual`을 사용하고, 시크릿 키 최소 길이(16B) 검증 및 예외 로그 내 웹훅 URL 마스킹을 유지하세요.
   - 시간 의존 로직은 `java.time.Clock` 주입 생성자를 열어 두세요.
6. **문서화 필수 (`package-info.java` · Javadoc · 설정 메타데이터)**
   - 라이브러리의 모든 패키지에는 `package-info.java`를 작성하고, 모든 `public` 타입·레코드 컴포넌트·메서드에 `javadoc -Xdoclint:all -Werror` 기준 무경고 Javadoc을 유지하세요.
   - `teams.*` 프로퍼티 변경 시 `META-INF/spring-configuration-metadata.json`도 함께 동기화하세요.
