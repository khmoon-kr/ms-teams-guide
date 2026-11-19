# 07. 빌링 플랫폼 × MS Teams 운영 연동 Java(Spring Boot) 구현 가이드

이 문서는 [`teams-notify`](./teams-notify) 디렉터리에 구현된 **MS Teams 알림·서명 링크 Java 17 라이브러리(`TeamsNotifier`) 및 빌링 데모 웹 애플리케이션**의 구조, 동작 원리, 실행 방법 및 API 호출 예시를 설명합니다.

---

## 1. 무엇을 해결하는 프로젝트인가요?

빌링(결제) 플랫폼을 운영할 때 MS Teams를 단순 알림방이 아니라 **가벼운 운영 콘솔 허브**로 활용할 수 있도록 다음 4가지를 구현했습니다.

1. **결제 이벤트 REST API → Adaptive Card 자동 발송**
   - **결제 승인(`PAYMENT_AUTHORIZED`)**, **매입 완료/실패(`CAPTURE_SUCCEEDED` / `CAPTURE_FAILED`)**, **환불 요청/완료(`REFUND_REQUESTED` / `REFUND_COMPLETED`)** 데이터를 REST API로 받으면 금액(`₩128,000`), 한국 시각(KST), 상태별 테마가 적용된 Adaptive Card(v1.4) JSON을 생성해 Teams Workflows Webhook으로 전송합니다.
2. **카드 링크 클릭 → 우리 웹 시스템 진입 시 "Teams 공식 링크 유입" 검증 (`HMAC-SHA256`)**
   - Teams 데스크톱/모바일 앱에서 링크(`Action.OpenUrl`)를 클릭하면 외부 브라우저가 열려 HTTP `Referer` 헤더가 남지 않습니다.
   - 이를 해결하기 위해 카드 생성 시점에 **`txId|src|cardType|action|iat|exp` 원문을 서버 비밀키로 `HMAC-SHA256` 서명한 `sig` 파라미터**를 URL에 부착합니다.
   - 사용자가 링크를 클릭해 `/ops/transactions/{txId}` 화면에 들어오면 **4가지 유입 상태**를 판별하고, **`VERIFIED_TEAMS`(검증 성공)일 때만 화면 내 [수동 매입 확정] / [환불 승인·실행] 버튼을 활성화**합니다.
3. **고액 환불 승인 콜백 (`POST /api/v1/teams/callbacks/refund-decision`)**
   - 50만 원 이상 환불(또는 `requireApproval: true`) 요청 시 `REFUND_REQUESTED` 카드를 발송하고, 검증된 링크 페이지 또는 Teams Workflows 양방향 콜백을 통해 최종 승인/반려를 처리합니다.
4. **채널 `@멘션` 거래 실시간 조회 (`POST /api/v1/teams/outgoing-webhook`)**
   - Teams 표준 채널의 Outgoing Webhook을 연결하면 `@빌링조회 TX-20261119-1000` 입력만으로 원장 상태를 Adaptive Card로 즉시 응답합니다.

---

## 2. Teams 링크 유입 검증(`TeamsLinkSigner`) 동작 구조

### 2.1 카드에 삽입되는 서명 링크 예시

```text
http://localhost:8080/ops/transactions/TX-20261119-1000
  ?src=msteams
  &cardType=PAYMENT_AUTHORIZED
  &action=view
  &iat=1795079000
  &exp=1795082600
  &sig=8xZp9N0b... (Base64URL HMAC-SHA256)
```

### 2.2 검증 결과 4단계 (`LinkVerificationStatus`)

| 상태 코드 | 판별 조건 | 검증 페이지(`/ops/transactions/{txId}`) 동작 |
| :--- | :--- | :--- |
| **`VERIFIED_TEAMS`** | `src=msteams` + 상수 시간(`MessageDigest.isEqual`) 서명 일치 + `now <= exp` | 초록색 **"Teams 공식 링크 검증 완료"** 배너 표시 및 **매입/환불 운영 버튼 활성화** |
| **`DIRECT_UNVERIFIED`** | `src`, `sig` 등 서명 쿼리 없이 URL로 직접 접속 | 황색 **"일반 직접 접속"** 배너 표시 (원장 조회만 허용, 운영 변경 버튼 잠금) |
| **`EXPIRED`** | 서명은 일치하나 `exp`(기본 60분) 경과 | 적색 **"만료된 Teams 카드 링크"** 경고 및 운영 버튼 잠금 |
| **`INVALID_SIGNATURE`** | 주소창에서 `txId`, `action`, `exp`, `sig` 중 1글자라도 변조하거나 필수값 누락 | 적색 **"서명 불일치 / 파라미터 위변조 감지"** 경고 및 운영 버튼 잠금 |

> [!TIP]
> 별도의 React/Vue SPA 프론트엔드에서 검증 결과만 JSON으로 받고 싶다면 `GET /api/v1/teams/verify-link?txId=...&src=...&cardType=...&action=...&iat=...&exp=...&sig=...` API를 그대로 호출하면 됩니다.

---

## 3. 멀티 모듈 아키텍처 (라이브러리 vs 테스트용 데모 앱 분리)

다른 프로젝트에서 도메인 종속적인 이름이나 테스트용 컨트롤러 없이 **범용 MS Teams 알림 라이브러리로 바로 가져다 쓸 수 있도록** 간결한 네이밍의 3개 모듈로 구성되어 있습니다.

```text
teams-notify/                               (Parent POM)
├── teams-core/                             [순수 Java 17 라이브러리 JAR — Spring 의존성 0개]
│   ├── TeamsNotifier                       (단일 진입점 Facade)
│   ├── TeamsSettings                       (Builder 기반 설정, entryPathTemplate 커스텀 지원)
│   ├── TransactionView / TransactionSnapshot (외부 프로젝트 자체 엔티티/DTO 연동 인터페이스)
│   ├── TeamsCardFactory                    (Adaptive Card v1.4 + 커스텀 카드 조립기)
│   ├── TeamsFormatFactory                  (Rich HTML, Activity Feed, Urgent+@Tag, Receipt, Hero)
│   ├── TeamsLinkSigner / OutgoingWebhookVerifier (JDK 표준 HmacSHA256 서명·검증)
│   └── TeamsWebhookClient                  (JDK 17 HttpClient 기반 다중 채널 전송기)
│
├── teams-spring-boot-starter/              [Spring Boot 3 AutoConfiguration 스타터 JAR]
│   ├── TeamsProperties                     (application.yml의 teams.* 자동 바인딩)
│   └── TeamsAutoConfiguration              (@ConditionalOnMissingBean 기반 자동 빈 등록)
│
└── teams-demo-app/                         [테스트·검증용 REST API & Thymeleaf 웹 콘솔 앱]
    └── 시뮬레이터(/ops), 6종 포맷 비교(/ops/formats), 서명 링크 검증 화면(/ops/transactions/{txId})
```

### 3.1 다른 프로젝트에서 라이브러리로 가져다 쓰는 방법

먼저 로컬 Maven 저장소(`~/.m2`) 또는 사내 Nexus/GitHub Packages에 라이브러리를 배포(`mvn clean install` / `mvn deploy`)합니다.

#### 케이스 A: 다른 Spring Boot 3 프로젝트에서 사용 (`teams-spring-boot-starter`)

1) `pom.xml` (또는 `build.gradle`) 의존성 추가:
```xml
<dependency>
    <groupId>com.example</groupId>
    <artifactId>teams-spring-boot-starter</artifactId>
    <version>0.1.0</version>
</dependency>
```

2) 사용하는 서비스의 `application.yml` 설정:
```yaml
teams:
  webhook-url: ${TEAMS_WEBHOOK_URL:}
  base-url: https://admin.our-service.com
  entry-path-template: /admin/payments/{txId}   # 우리 서비스의 실제 상세 화면 경로로 지정
  link-secret: ${TEAMS_LINK_SECRET}
  link-ttl-minutes: 30
  max-outbox-size: 0                            # 실서비스에서 메모리 Outbox 보관이 불필요하면 0
```

3) 비즈니스 코드에서 `@Autowired` / 생성자 주입으로 `TeamsNotifier` 즉시 사용:
```java
@Service
@RequiredArgsConstructor
public class PaymentSettlementService {

    private final TeamsNotifier teamsNotifier; // 자동 빈 등록됨!

    public void onPaymentCaptured(OrderPaymentEntity entity) {
        var snapshot = TransactionSnapshot.builder()
                .txId(entity.getTxNo())
                .orderId(entity.getOrderNo())
                .merchantName(entity.getStoreName())
                .amount(entity.getPaidAmount())
                .paymentMethod(entity.getCardName())
                .authCode(entity.getApprovalNo())
                .approvedAt(entity.getApprovedAt())
                .status(TransactionStatus.CAPTURED)
                .build();

        // 1) 기본 Adaptive Card 발송
        teamsNotifier.notifyCaptureResult(snapshot, true, Instant.now());

        // 2) 임의의 자체 어드민 URL에 Teams 유입 검증 서명만 붙이고 싶을 때
        String signedAdminUrl = teamsNotifier.signCustomUrl(
                "https://admin.our-service.com/settlements/991?tab=detail",
                entity.getTxNo(), "SETTLEMENT_ALERT", "approve"
        );
    }
}
```

#### 케이스 B: Spring이 없는 일반 Java 17 / 배치 / 타 프레임워크에서 사용 (`teams-core`)

`teams-core`는 `jackson-databind`와 `slf4j-api`만 참조하며 Spring 의존성이 전혀 없습니다.

```java
TeamsNotifier teams = TeamsNotifier.create(
        TeamsSettings.builder()
                .webhookUrl(System.getenv("TEAMS_WEBHOOK_URL"))
                .baseUrl("https://admin.our-service.com")
                .entryPathTemplate("/admin/payments/{txId}")
                .linkSecret(System.getenv("TEAMS_LINK_SECRET"))
                .maxOutboxSize(0)
                .build()
);

// 검증 시: 쿼리 파라미터 Map만 넘기면 VERIFIED_TEAMS / EXPIRED / INVALID_SIGNATURE 즉시 판별
LinkVerificationResult verified = teams.verifySignedQueryMap("TX-1001", queryParamMap);
```

---

### 3.2 테스트용 데모 서버 실행 방법 (`teams-demo-app`)

Teams Webhook URL이 없어도 Dry-Run 시뮬레이터 모드로 바로 실행해 모든 카드 JSON과 링크 유입 검증을 테스트할 수 있습니다.

```bash
cd teams-notify
mvn clean install
java -jar teams-demo-app/target/teams-demo-app-0.1.0.jar
```

실제 MS Teams 채팅/채널 웹훅을 연결하려면 환경변수만 지정해 기동합니다:

```bash
export TEAMS_WEBHOOK_URL="https://defaultxxxx.environment.api.powerplatform.com:443/powerautomate/automations/direct/workflows/..."
export APP_BASE_URL="https://admin.company.com"
export TEAMS_LINK_SECRET="prod-32byte-random-hmac-secret-key"

java -jar teams-demo-app/target/teams-demo-app-0.1.0.jar
```

---

## 4. REST API 호출 예시 (cURL)

### 4.1 결제 승인 알림 발송 (`POST /api/v1/billing/events/authorize`)

```bash
curl -X POST http://localhost:8080/api/v1/billing/events/authorize \
  -H "Content-Type: application/json" \
  -d '{
    "orderId": "ORD-20261119-0042",
    "merchantName": "클라우드 빌링 엔터프라이즈",
    "amount": 320000,
    "paymentMethod": "현대카드 (4009-****-****-1190)"
  }'
```

응답 JSON에는 생성된 `txId`, 서명된 링크(`signedDetailUrl`, `signedActionUrl`), 그리고 Teams로 발송된 전체 Adaptive Card 페이로드(`teamsWebhookPayload`)가 함께 반환됩니다.

### 4.2 매입 성공 / 매입 실패 알림 (`POST /api/v1/billing/events/capture`)

```bash
# 매입 실패 경보 카드 발송 예시
curl -X POST http://localhost:8080/api/v1/billing/events/capture \
  -H "Content-Type: application/json" \
  -d '{
    "txId": "TX-20261119-1000",
    "success": false,
    "failureReason": "PG_TIMEOUT (매입 게이트웨이 응답 지연)"
  }'
```

### 4.3 환불 요청 / 즉시 환불 (`POST /api/v1/billing/events/refund`)

50만 원 이상이거나 `"requireApproval": true`이면 **환불 승인 대기(`REFUND_REQUESTED`)** 카드가 발송되며, 그 외에는 즉시 **환불 완료(`REFUND_COMPLETED`)** 카드가 발송됩니다.

```bash
curl -X POST http://localhost:8080/api/v1/billing/events/refund \
  -H "Content-Type: application/json" \
  -d '{
    "txId": "TX-20261119-1000",
    "refundAmount": 50000,
    "reason": "고객 요청 부분 환불",
    "requestedBy": "CS팀 이영희",
    "requireApproval": true
  }'
```

### 4.4 환불 승인/반려 콜백 (`POST /api/v1/teams/callbacks/refund-decision`)

```bash
curl -X POST http://localhost:8080/api/v1/teams/callbacks/refund-decision \
  -H "Content-Type: application/json" \
  -d '{
    "txId": "TX-20261119-1000",
    "approved": true,
    "actor": "결제운영팀장",
    "comment": "부분 환불 승인 완료"
  }'
```

### 4.5 Teams 채널 Outgoing Webhook 조회 (`POST /api/v1/teams/outgoing-webhook`)

```bash
curl -X POST http://localhost:8080/api/v1/teams/outgoing-webhook \
  -H "Content-Type: application/json" \
  -d '{
    "type": "message",
    "text": "<at>빌링조회</at> TX-20261119-1000"
  }'
```

---

## 5. Adaptive Card 외 5가지 Teams 알림 방식 비교 API 및 화면

어댑티브 카드 외에도 Teams에서 지원하는 **리치 HTML 표 메시지**, **활동 피드 푸시(🔔)**, **Urgent 반복 알림 + `@팀태그`**, **Receipt 영수증 카드**, **Hero 심플 카드**의 실제 렌더링 모양과 백엔드 JSON을 바로 확인할 수 있습니다.

- **시각적 비교 웹 화면**: `http://localhost:8080/ops/formats`
  - 각 알림 방식 안의 링크/버튼에도 동일하게 `HMAC-SHA256` 서명이 들어가 있어 클릭 시 `cardType` 별로(`HTML_MESSAGE`, `ACTIVITY_FEED`, `URGENT_TAG_MESSAGE`, `RECEIPT_CARD`, `HERO_CARD`) 유입 경로까지 구분해 검증합니다.

### 포맷별 REST API 목록

| 알림 방식 | Method & Path | 설명 |
| :--- | :--- | :--- |
| **포맷 목록 조회** | `GET /api/v1/teams/formats` | 지원되는 6가지 알림 방식 및 권장 용도 목록 |
| **6종 일괄 생성 비교** | `GET /api/v1/teams/formats/compare-all?txId=...` | 지정 거래 기준으로 6가지 방식의 JSON 일괄 반환 |
| **리치 HTML 표 + `@멘션`** | `POST /api/v1/teams/formats/html-message` | Graph `chatMessage`(`contentType: "html"`) 및 Workflows `Post message` 페이로드 생성 |
| **활동 피드 알림 (🔔)** | `POST /api/v1/teams/formats/activity-feed` | Graph `sendActivityNotification` 및 Workflows `Post a feed notification` 페이로드 생성 |
| **Urgent 반복 푸시 + `@팀태그`** | `POST /api/v1/teams/formats/urgent-alert` | `importance: "urgent"`(2분 간격 최대 20분 반복 알림) + `teamworkTagIdentity` 페이로드 생성 |
| **영수증 카드 (Receipt Card)** | `POST /api/v1/teams/formats/receipt-card` | 공급가액·부가세(VAT)·합계가 분리된 `application/vnd.microsoft.card.receipt` JSON 생성 |
| **심플 요약 카드 (Hero Card)** | `POST /api/v1/teams/formats/hero-card` | `title` / `subtitle` / `text` / `buttons` 구조의 `application/vnd.microsoft.card.hero` JSON 생성 |

```bash
# 예: 영수증 카드(Receipt Card) JSON 및 서명 링크 생성 확인
curl -X POST http://localhost:8080/api/v1/teams/formats/receipt-card \
  -H "Content-Type: application/json" \
  -d '{ "txId": "TX-20261119-1000" }'
```
