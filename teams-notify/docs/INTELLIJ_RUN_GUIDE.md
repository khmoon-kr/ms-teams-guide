# IntelliJ IDEA 실행 및 환경설정 가이드 (`teams-demo-app`)

이 저장소는 루트 디렉터리(`ms-teams-guide/`)가 아닌 하위 [`teams-notify/pom.xml`](../pom.xml)에 멀티 모듈 Maven 프로젝트가 구성되어 있습니다.
IntelliJ IDEA에서 데모 애플리케이션(`teams-demo-app`)을 열었을 때 의존성 인식, JDK 17 지정, 환경변수 주입 및 기동 방법을 안내합니다.

---

## 1. Maven 프로젝트 인식 확인

루트 폴더(`ms-teams-guide`)를 IntelliJ에서 열었을 때 `TeamsDemoApplication` 옆에 실행 아이콘(`▶`)이 보이지 않거나 `org.springframework...` import가 빨갛게 표시된다면 Maven 모듈이 아직 연결되지 않은 상태입니다.

1. 좌측 **Project** 트리에서 [`teams-notify/pom.xml`](../pom.xml)을 우클릭 → **Add as Maven Project**를 클릭합니다.
   - 이미 등록되어 있다면 우측 사이드바의 **Maven** 도구 창을 열고 새로고침 아이콘(**Reload All Maven Projects**)을 클릭합니다.
2. Maven 탭 아래에 다음 3개 모듈이 정상 로드되었는지 확인합니다.
   - `teams-notify` (Parent)
   - `teams-core`
   - `teams-spring-boot-starter`
   - `teams-demo-app`

> [!TIP]
> 문서(`docs/`) 없이 Java 프로젝트만 단독으로 작업할 때는 **File → Open**에서 `ms-teams-guide/teams-notify` 폴더를 직접 열어도 즉시 Maven 멀티 모듈로 인식됩니다.

---

## 2. JDK 17 설정 확인

[`teams-notify`](../README.md)의 모든 모듈은 **Java 17** 기준으로 컴파일·실행됩니다.

1. **File → Project Structure** (`Ctrl + Alt + Shift + S` / macOS `⌘ + ;`) → **Project** 이동
   - **SDK**: **Java 17** 지정 (예: `corretto-17`, `temurin-17`, `Oracle OpenJDK 17` 등)
   - **Language level**: **17 - Sealed types, always-strict floating-point semantics**
2. **Settings** (`Ctrl + Alt + S` / macOS `⌘ + ,`) → **Build, Execution, Deployment → Build Tools → Maven**
   - **Importing → JDK for importer**: **Project SDK (17)** 확인
   - **Runner → JRE**: **Use Project JDK (17)** 확인

---

## 3. 실행 구성(Run/Debug Configuration) 설정 및 기동

### 방법 A: 환경변수 없이 바로 실행 (로컬 Dry-Run 시뮬레이터 모드)

[`teams-demo-app/src/main/resources/application.yml`](../teams-demo-app/src/main/resources/application.yml)에 기본값이 모두 설정되어 있어 **별도의 MS Teams Webhook URL 없이도 즉시 실행**됩니다. (`webhook-url`이 비어 있으면 외부 호출 대신 메모리 Outbox에 적재하여 시뮬레이터 화면에서 바로 확인할 수 있습니다.)

1. [`TeamsDemoApplication.java`](../teams-demo-app/src/main/java/com/example/teams/TeamsDemoApplication.java) 파일을 엽니다.
2. 클래스 선언부 또는 `main` 메서드 좌측 여백의 초록색 **`▶` → Run 'TeamsDemoApplication.main()'**을 클릭합니다.

---

### 방법 B: 실제 MS Teams Webhook 연동 / 포트·시크릿 변경

실제 Teams 채널·채팅방으로 카드를 발송하거나 로컬 포트를 바꾸려면 **Run/Debug Configuration**에 환경변수를 추가합니다.

1. 우측 상단 실행 위젯 드롭다운(`TeamsDemoApplication`) → **Edit Configurations...** 클릭
   - 실행 구성이 아직 없다면 좌측 상단 **`+`** 클릭 → **Spring Boot** *(IntelliJ Community 에디션은 **Application**)* 선택
2. 기본 실행 항목을 아래와 같이 설정합니다.
   - **Name**: `TeamsDemoApplication`
   - **Module (Use classpath of module)**: `teams-demo-app` (`Java 17`)
   - **Main class**: `com.example.teams.TeamsDemoApplication`
3. **Environment variables** 입력란에 필요한 변수를 지정합니다.
   - *(입력란이 보이지 않으면 우측 상단 **Modify options → Add environment variables** 체크)*

| 환경변수 | 필수 여부 | 기본값 | 설명 |
| :--- | :---: | :--- | :--- |
| `TEAMS_WEBHOOK_URL` | 선택 | *(빈 문자열)* | 비워두면 로컬 Dry-Run 모드로 동작. 실제 발송 시 Teams Workflows Webhook URL 입력 |
| `PORT` | 선택 | `8080` | 서버 실행 포트 (`8080` 포트 충돌 시 `8081` 등으로 변경) |
| `APP_BASE_URL` | 선택 | `http://localhost:8080` | 카드 내 `HMAC-SHA256` 서명 버튼이 가리킬 베이스 도메인 (ngrok 등 외부 터널 사용 시 변경) |
| `TEAMS_LINK_SECRET` | 선택 | `dev-teams-notify-hmac-secret-key-change-in-prod` | 서명 링크 생성·검증 비밀키 (직접 지정 시 **최소 16바이트 이상** 필수) |
| `TEAMS_LINK_TTL_MINUTES` | 선택 | `60` | 서명 링크 유효 시간(분) |
| `TEAMS_OUTGOING_WEBHOOK_SECRET` | 선택 | *(빈 문자열)* | Teams 표준 채널 Outgoing Webhook 연동 시 발급받은 Base64 HMAC 시크릿 |

**IntelliJ `Environment variables` 한 줄 입력 예시** (세미콜론 `;` 구분):

```text
PORT=8080;APP_BASE_URL=http://localhost:8080;TEAMS_WEBHOOK_URL=https://defaultxxxx.environment.api.powerplatform.com:443/powerautomate/automations/direct/workflows/...
```

4. **Apply → OK** 저장 후 상단 초록색 **`▶` (Run)** 또는 **벌레 아이콘 (Debug)**을 눌러 기동합니다.

---

## 4. 기동 후 접속 및 동작 확인

콘솔에 `Started TeamsDemoApplication` 로그가 출력되면 브라우저에서 아래 주소로 접속합니다.

- **빌링 이벤트 발송 시뮬레이터 & 서명 링크 4단계 검증 콘솔**: `http://localhost:8080/ops` (또는 `http://localhost:8080/`)
- **6종 Teams 알림 포맷(Adaptive / HTML / Activity Feed / Urgent / Receipt / Hero) 비교 화면**: `http://localhost:8080/ops/formats`
- **REST API 단독 테스트**: [`teams-notify/postman/`](../postman/teams-notify-local.postman_collection.json) 컬렉션을 Postman에 Import하여 19개 시나리오 실행 ([상세 안내](../README.md#4-postman-컬렉션--rest-api-호출-예시))

---

## 5. 자주 겪는 문제 해결 (Troubleshooting)

- **`teams-core` 또는 `teams-spring-boot-starter` 클래스를 찾을 수 없다고 나오는 경우**
  1. 우측 **Maven** 탭에서 **Reload All Maven Projects** 실행
  2. 상단 메뉴 **Build → Rebuild Project** 실행
- **`Web server failed to start. Port 8080 was already in use.` 오류가 발생하는 경우**
  - **Edit Configurations... → Environment variables**에 `PORT=8081`과 `APP_BASE_URL=http://localhost:8081`을 함께 지정하고 재실행합니다.
- **`linkSecret` 길이 검증 예외(`IllegalArgumentException`)가 발생하는 경우**
  - `TEAMS_LINK_SECRET` 환경변수를 지정할 때는 보안 규칙상 UTF-8 기준 **최소 16바이트 이상**의 문자열을 입력해야 합니다.
