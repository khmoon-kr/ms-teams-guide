package com.example.billingteams.domain;

public enum TeamsNotificationFormat {
    HTML_MESSAGE(
            "일반 리치 HTML 메시지 (표 · 서식 · @멘션)",
            "Workflows (Post message) / Graph API / Bot",
            "카드 프레임 없이 가벼운 결제 로그, 정산 표(<table>), 하이퍼링크를 깔끔하게 남길 때"
    ),
    ACTIVITY_FEED(
            "활동 피드 푸시 알림 (🔔 좌측 상단 종 모양 + OS 배너)",
            "Workflows (Post a feed notification) / Graph API",
            "채팅방 대화에 묻히지 않고 특정 결재자·담당자의 알림창과 모바일 푸시로 직접 꽂아줄 때"
    ),
    URGENT_TAG_MESSAGE(
            "긴급(Urgent) 반복 푸시 + @팀태그 호출 메시지",
            "Graph API (importance: urgent/high) / Workflows (@Tag)",
            "PG 전면 장애나 고액 이상결제처럼 담당자가 읽을 때까지(2분 간격 최대 20분) 깨워야 할 때"
    ),
    RECEIPT_CARD(
            "영수증 전용 카드 (Receipt Card)",
            "Graph API / Teams Bot (contentType: card.receipt)",
            "공급가액·부가세(VAT)·품목별 단가·합계가 나뉜 청구서/영수증 형태 알림을 보낼 때"
    ),
    HERO_CARD(
            "심플 요약 카드 (Hero Card)",
            "Graph API / Teams Bot / Outgoing Webhook (contentType: card.hero)",
            "복잡한 JSON 스키마 없이 제목·부제목·요약 문구와 버튼 1~2개만 빠르게 보낼 때"
    ),
    ADAPTIVE_CARD(
            "어댑티브 카드 (Adaptive Card v1.4 — 기본 표준)",
            "Workflows Webhook / Graph API / Teams Bot 전체 지원",
            "컬러 헤더, FactSet, 입력 폼, 다중 액션 버튼 등 가장 풍부한 운영 카드를 구성할 때"
    );

    private final String displayName;
    private final String supportedChannels;
    private final String recommendedUseCase;

    TeamsNotificationFormat(String displayName, String supportedChannels, String recommendedUseCase) {
        this.displayName = displayName;
        this.supportedChannels = supportedChannels;
        this.recommendedUseCase = recommendedUseCase;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getSupportedChannels() {
        return supportedChannels;
    }

    public String getRecommendedUseCase() {
        return recommendedUseCase;
    }
}
