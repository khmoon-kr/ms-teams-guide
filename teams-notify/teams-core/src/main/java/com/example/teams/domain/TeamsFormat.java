package com.example.teams.domain;

/**
 * MS Teams 생태계(Workflows, Microsoft Graph API, Teams Bot, Outgoing Webhook)에서 활용 가능한
 * 대표 알림 포맷 6종의 카탈로그 열거형입니다.
 *
 * <p>각 상수는 UI 표시 이름({@link #getDisplayName()}), 전송 가능한 채널 범위({@link #getSupportedChannels()}),
 * 그리고 실무 권장 활용 시나리오({@link #getRecommendedUseCase()}) 정보를 함께 제공합니다.</p>
 *
 * @see com.example.teams.cards.TeamsFormatFactory
 * @see com.example.teams.cards.TeamsCardFactory
 */
public enum TeamsFormat {

    /**
     * HTML {@code <table>}, 서식 태그, {@code <at>} 멘션, {@code <a href>} 링크로 구성되는 일반 채팅·채널 메시지입니다.
     */
    HTML_MESSAGE(
            "일반 리치 HTML 메시지 (표 · 서식 · @멘션)",
            "Workflows (Post message) / Graph API / Bot",
            "카드 프레임 없이 가벼운 운영 로그, 데이터 표(<table>), 하이퍼링크를 깔끔하게 남길 때"
    ),

    /**
     * Teams 좌측 상단 활동(🔔) 벨 탭과 모바일·데스크톱 OS 푸시 배너를 직접 울리는 피드 알림입니다.
     */
    ACTIVITY_FEED(
            "활동 피드 푸시 알림 (🔔 좌측 상단 종 모양 + OS 배너)",
            "Workflows (Post a feed notification) / Graph API",
            "채팅방 대화에 묻히지 않고 특정 승인권자·담당자의 알림창과 모바일 푸시로 직접 전달할 때"
    ),

    /**
     * {@code importance: "urgent"}(수신자가 읽을 때까지 2분 간격 최대 20분 반복 알림)와 {@code @팀태그} 멘션을 결합한 경보 메시지입니다.
     */
    URGENT_TAG_MESSAGE(
            "긴급(Urgent) 반복 푸시 + @팀태그 호출 메시지",
            "Graph API (importance: urgent/high) / Workflows (@Tag)",
            "심각한 장애나 즉시 조치가 필요한 경보처럼 담당자가 읽을 때까지(2분 간격 최대 20분) 깨워야 할 때"
    ),

    /**
     * 항목 목록, 세액, 합계 금액을 정형화된 영수증·명세 레이아웃으로 렌더링하는 내장 카드({@code application/vnd.microsoft.card.receipt})입니다.
     */
    RECEIPT_CARD(
            "영수증·명세 전용 카드 (Receipt Card)",
            "Graph API / Teams Bot (contentType: card.receipt)",
            "항목별 금액·세액·합계가 구분된 명세서/영수증 형태 알림을 보낼 때"
    ),

    /**
     * 제목, 부제목, 요약 본문과 1~2개의 버튼만으로 가볍게 구성하는 내장 카드({@code application/vnd.microsoft.card.hero})입니다.
     */
    HERO_CARD(
            "심플 요약 카드 (Hero Card)",
            "Graph API / Teams Bot / Outgoing Webhook (contentType: card.hero)",
            "복잡한 JSON 스키마 없이 제목·부제목·요약 문구와 버튼 1~2개만 빠르게 보낼 때"
    ),

    /**
     * 컬러 배너 컨테이너, {@code FactSet}, 액션 버튼을 자유롭게 배치할 수 있는 MS Teams 표준 카드(Adaptive Card v1.4)입니다.
     */
    ADAPTIVE_CARD(
            "어댑티브 카드 (Adaptive Card v1.4 — 기본 표준)",
            "Workflows Webhook / Graph API / Teams Bot 전체 지원",
            "컬러 헤더, FactSet, 입력 폼, 다중 액션 버튼 등 가장 풍부한 운영 카드를 구성할 때"
    );

    private final String displayName;
    private final String supportedChannels;
    private final String recommendedUseCase;

    TeamsFormat(String displayName, String supportedChannels, String recommendedUseCase) {
        this.displayName = displayName;
        this.supportedChannels = supportedChannels;
        this.recommendedUseCase = recommendedUseCase;
    }

    /**
     * 포맷의 한국어 표시 이름을 반환합니다.
     *
     * @return 포맷 표시 이름
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * 해당 포맷을 전송할 수 있는 MS Teams 연동 수단(Workflows, Graph API, Bot 등) 요약 문자열을 반환합니다.
     *
     * @return 지원 채널 설명
     */
    public String getSupportedChannels() {
        return supportedChannels;
    }

    /**
     * 어떤 운영·알림 시나리오에서 이 포맷을 선택하면 좋은지에 대한 권장 가이드를 반환합니다.
     *
     * @return 권장 활용 시나리오 설명
     */
    public String getRecommendedUseCase() {
        return recommendedUseCase;
    }
}
