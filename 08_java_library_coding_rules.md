# 08. 공통 라이브러리(Java / Spring Boot Starter) 코딩 규칙

일반 서비스 애플리케이션과 달리 **여러 프로젝트가 의존성(`dependency`)으로 가져다 쓰는 공용 라이브러리**는 한 번 잘못 설계된 클래스명, 전이 의존성(Transitive Dependency), 전역 상태 변경이 라이브러리를 참조하는 모든 서비스에 전파됩니다.

이 문서는 [`teams-notify`](./teams-notify)를 비롯한 사내 공용 Java 17 / Spring Boot Starter 라이브러리가 반드시 지켜야 할 **7대 코딩 규칙과 체크리스트**를 정의합니다.

---

## 1. 네이밍 & 도메인 중립성 규칙 (Naming & Domain Neutrality)

### 1.1 특정 서비스·도메인 접두어 금지
라이브러리를 처음 도입한 프로젝트가 '빌링'이나 '주문'이더라도, 라이브러리 클래스명·패키지명·설정 키(`prefix`)에 특정 비즈니스 도메인 이름을 붙이지 않습니다.

- **Bad (도메인 종속 · 장황함)**
  - 클래스: `BillingTeamsNotifier`, `BillingTeamsProperties`, `TeamsMultiFormatNotificationFactory`
  - 프로퍼티: `billing.teams.webhook-url`
  - 아티팩트: `billing-teams-spring-boot-starter`
- **Good (역할 중심 · 간결함)**
  - 클래스: `TeamsNotifier`, `TeamsProperties`, `TeamsFormatFactory`, `TeamsWebhookClient`
  - 프로퍼티: `teams.webhook-url`
  - 아티팩트: `teams-core`, `teams-spring-boot-starter`

### 1.2 소비자 엔티티 비침투 (Consumer-Agnostic Input)
사용처(호출 측) 프로젝트의 JPA Entity나 내부 DTO가 라이브러리 전용 가변 클래스를 상속하도록 강제하지 않습니다.
- 입력 모델은 **읽기 전용 인터페이스(`TransactionView`)**와 **불변 레코드 + 빌더(`TransactionSnapshot`)** 쌍으로 제공합니다.

---

## 2. 모듈 분리 & 의존성 최소화 규칙 (Zero-Bloat Dependencies)

### 2.1 3-모듈 분리 원칙
공용 라이브러리는 반드시 아래 3개 계층으로 분리합니다.

| 모듈 | 패키징 | 허용 의존성 | 금지 사항 |
| :--- | :--- | :--- | :--- |
| **`*-core`** | 일반 JAR | JDK 표준 라이브러리, `jackson-databind`, `slf4j-api` | `org.springframework.*`, `jakarta.servlet.*`, 웹 서버, DB 드라이버 전이 금지 |
| **`*-spring-boot-starter`** | 일반 JAR | `*-core`, `spring-boot-autoconfigure` | 비즈니스 로직 직접 구현 금지(빈 조립·프로퍼티 바인딩만 담당), Boot Fat JAR 패키징 금지 |
| **`*-demo-app`** | 실행형 Fat JAR | `*-spring-boot-starter`, `spring-boot-starter-web` 등 | 다른 서비스가 이 모듈을 `<dependency>`로 참조하는 것 금지 |

> [!CAUTION]
> **`spring-boot-maven-plugin` (`repackage`) 주의**
> `*-core`와 `*-spring-boot-starter`의 `pom.xml`에는 절대 `spring-boot-maven-plugin`의 `repackage`를 선언하면 안 됩니다. 클래스가 `BOOT-INF/classes` 아래로 말려 들어가 다른 프로젝트에서 `import` 시 컴파일 에러가 발생합니다.

### 2.2 로깅 추상화 단일화
- 라이브러리 코드는 오직 `org.slf4j.Logger`(`slf4j-api`)만 사용합니다.
- `logback-classic`, `log4j-core` 같은 로깅 구현체는 반드시 `<scope>test</scope>`로만 선언하여 소비 애플리케이션의 로깅 프레임워크와 충돌(`SLF4J Multiple bindings`)하지 않게 합니다.
- `System.out.println` / `e.printStackTrace()` 사용을 금지합니다.

---

## 3. 공개 API 설계 & 불변성 규칙 (Public API & Immutability)

### 3.1 최소 공개 원칙 (Minimize Public Surface)
- 한 번 `public`으로 공개된 클래스와 메서드는 외부 프로젝트가 호출하는 순간 **하위 호환성 계약**이 됩니다.
- 외부에 노출할 대표 진입점(`TeamsNotifier` 등 Facade)과 확장 포인트만 `public`으로 두고, 내부 파서·직렬화 헬퍼 메서드는 `package-private` 또는 `private`으로 제한합니다.

### 3.2 불변 객체(Immutable) 및 Builder 패턴
- 설정 객체(`TeamsSettings`)와 응답/데이터 모델(`record`)은 생성 이후 상태가 바뀌지 않는 **불변(Immutable)** 구조로 작성합니다 (`setter` 노출 지양 — 단, Spring `@ConfigurationProperties` 바인딩 클래스는 예외).
- 선택적 설정값이 3개 이상인 객체는 생성자 오버로딩(Telescoping Constructor) 대신 `Builder` 패턴을 제공합니다.

### 3.3 하드코딩 제거 & 결정론적 시간(`Clock`) 주입
- URL 경로(`/ops/transactions/{txId}`), 타임아웃, TTL 등 환경마다 달라질 수 있는 값은 상수로 박지 않고 `TeamsSettings`의 기본값 + 오버라이드 옵션으로 열어 둡니다.
- 만료 시간(`exp`) 계산·검증 클래스(`TeamsLinkSigner` 등)는 `Clock.systemUTC()`를 기본으로 쓰되 생성자에서 `java.time.Clock`을 주입받을 수 있게 설계하여 `Thread.sleep()` 없이 단위 테스트가 가능해야 합니다.

---

## 4. Spring Boot Starter 작성 규칙 (AutoConfiguration Etiquette)

### 4.1 `@ComponentScan` 절대 금지
Starter에서 `@ComponentScan`을 선언하면 소비 애플리케이션의 컴포넌트 스캔 경계를 오염시키고 슬라이스 테스트(`@WebMvcTest` 등)를 깨뜨립니다.
- 모든 빈은 `@AutoConfiguration` 클래스(`TeamsAutoConfiguration`) 내부에 명시적인 `@Bean` 메서드로만 등록합니다.
- `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 파일에 자동 설정 클래스를 등록합니다.

### 4.2 모든 빈에 `@ConditionalOnMissingBean` 선언
사용하는 서비스에서 특정 동작(예: 자체 `TeamsLinkSigner`나 커스텀 프록시 `TeamsWebhookClient`)을 재정의하고 싶을 때 직접 `@Bean`만 선언하면 Starter 기본 빈이 자동으로 물러나야 합니다.

```java
@Bean
@ConditionalOnMissingBean
public TeamsLinkSigner teamsLinkSigner(TeamsSettings settings) {
    return new TeamsLinkSigner(settings);
}
```

### 4.3 소비 애플리케이션의 공용 인프라 빈 존중 (`ObjectProvider`)
`ObjectMapper` 등 소비 앱이 이미 커스터마이징해 둔 빈이 있다면 `ObjectProvider<ObjectMapper>`로 우선 주입받아 사용하고, 없을 때만 라이브러리 기본 인스턴스를 생성합니다.

---

## 5. 스레드 안전성 · 장애 격리 · 메모리 보호 (Resilience & Safety)

### 5.1 스레드 안전성 (Stateless & Thread-Safe)
라이브러리 빈(`TeamsNotifier`, `TeamsWebhookClient`, `TeamsLinkSigner`)은 수백 개의 요청 스레드가 동시에 호출합니다.
- 인스턴스 필드에 요청별 가변 상태를 저장하지 않습니다.
- JVM 전역 상태(`TimeZone.setDefault(...)`, `Locale.setDefault(...)`, `System.setProperty(...)`)를 절대 변경하지 않습니다.

### 5.2 본체 트랜잭션 보호 & 타임아웃 강제
- 모든 외부 HTTP 호출(`HttpClient`)에는 반드시 **`connectTimeout`**과 **`requestTimeout`**을 명시합니다(무한 대기 금지).
- `InterruptedException` 발생 시 삼키지 않고 반드시 `Thread.currentThread().interrupt()`를 호출해 스레드 인터럽트 플래그를 복원합니다.

### 5.3 무제한 인메모리 컬렉션 금지 (Bounded Memory)
- 디버깅용 발송 내역(`outbox`)이나 로컬 캐시를 메모리에 보관할 때는 반드시 최대 개수 상한(`maxOutboxSize`)을 두고, 운영 환경에서 완전히 끌 수 있는 옵션(`maxOutboxSize = 0`)을 제공합니다.

---

## 6. 보안 규칙 (Security Hygiene)

1. **상수 시간 서명 검증**: HMAC 서명 비교 시 타이밍 공격을 막기 위해 `String.equals()` 대신 `MessageDigest.isEqual(expected, actual)`을 사용합니다.
2. **민감정보 로깅 금지**: 토큰이 포함된 전체 Webhook URL, `linkSecret`, `Authorization` 헤더 원문은 `INFO`/`WARN`/`ERROR` 로그나 예외 메시지에 절대 노출하지 않습니다.
3. **생성 시점 Fail-Fast 검증**: `linkSecret` 등 필수 보안 키가 `null`이거나 빈 문자열(`isBlank()`)이면 객체 생성 시점에 즉시 `IllegalArgumentException`을 던져 잘못된 설정으로 서버가 기동되는 것을 막습니다.
4. **Teams Workflows 헤더 규칙 준수**: Power Automate `Anyone` 웹훅 트리거 호출 시 불필요한 `Authorization` 헤더를 붙이지 않고 `Content-Type: application/json; charset=UTF-8`만 전송합니다.

---

## 7. 테스트 & 하위 호환성 기준 (Testing & Versioning)

### 7.1 모듈별 3단계 필수 테스트
코드 변경 시 아래 3가지 테스트가 모두 통과해야 합니다.
1. **`*-core` 순수 단위 테스트**: Spring 컨텍스트 없이 JUnit 5만으로 실행되는 빠른 단위 테스트 및 아키텍처 의존성 검증(`CoreDependencyRuleTest`).
2. **`*-spring-boot-starter` 자동 설정 테스트**: `ApplicationContextRunner`를 이용해 `teams.*` 프로퍼티 바인딩과 `@ConditionalOnMissingBean` 백오프(양보) 동작을 검증(`TeamsAutoConfigurationTest`).
3. **`*-demo-app` 통합 테스트**: 실제 사용처 입장에서 Starter를 의존해 전체 플로우를 검증하는 E2E 테스트.

### 7.2 하위 호환성 유지 (Deprecation Cycle)
- 이미 배포된 `public` 메서드 시그니처를 변경하거나 제거할 때는 즉시 삭제하지 않고 최소 1개 마이너 버전 동안 `@Deprecated(since = "0.x.0", forRemoval = true)`와 대체 메서드 위임 코드를 유지합니다.

---

## PR 셀프 체크리스트

- [ ] 클래스명·설정 키에 특정 서비스 도메인 이름(`Billing*` 등)이 섞이지 않았는가?
- [ ] `*-core` 모듈에 `org.springframework.*` 등 프레임워크 의존성이 추가되지 않았는가?
- [ ] 새로 추가한 Starter `@Bean`에 `@ConditionalOnMissingBean`이 붙어 있는가? (`@ComponentScan` 미사용)
- [ ] 외부 네트워크 호출에 타임아웃이 설정되어 있고, 인메모리 컬렉션에 크기 제한(`0` 비활성화 포함)이 있는가?
- [ ] 시크릿 키나 전체 웹훅 URL이 로그에 찍히지 않는가?
- [ ] `mvn test` 실행 시 `core` / `starter` / `demo-app` 테스트와 아키텍처 규칙 테스트가 모두 통과하는가?
