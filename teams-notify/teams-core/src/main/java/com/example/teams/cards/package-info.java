/**
 * MS Teams Adaptive Card(v1.4) 및 5가지 대안 알림 포맷의 JSON 페이로드를 선언적으로 조립하는 패키지입니다.
 *
 * <h2>주요 컴포넌트</h2>
 * <ul>
 *   <li>{@link com.example.teams.cards.AdaptiveCardSpec} — 배너 스타일, 강조 문구, Fact 목록, 액션 버튼을 빌더 패턴으로 정의하는 불변 스펙</li>
 *   <li>{@link com.example.teams.cards.TeamsCardFactory} — {@link com.example.teams.cards.AdaptiveCardSpec}을 Teams 풀와이드({@code width: Full})
 *       Adaptive Card v1.4 및 Workflows 웹훅 봉투({@code attachments}) 구조로 변환</li>
 *   <li>{@link com.example.teams.cards.TeamsFormatFactory} — 리치 HTML 표 메시지, 활동 피드(🔔) 푸시, 긴급({@code urgent}) + {@code @팀태그},
 *       영수증 카드({@code card.receipt}), 히어로 카드({@code card.hero}) 페이로드 생성기</li>
 *   <li>{@link com.example.teams.cards.FormatMessage} — 생성된 원본 JSON 맵과 정렬된 문자열 및 서명된 진입 링크를 담는 방어적 복사 결과 레코드</li>
 * </ul>
 *
 * <h2>불변성 및 보안</h2>
 * <p>이 패키지의 모든 팩토리 메서드가 반환하는 {@link java.util.Map}과 {@link java.util.List}는 수정 불가능한(Unmodifiable) 뷰로 감싸져 있으며,
 * HTML 포맷 조립 시 외부 문자열 입력값은 자동으로 HTML 이스케이프 처리됩니다.</p>
 *
 * @see com.example.teams.cards.AdaptiveCardSpec
 * @see com.example.teams.cards.TeamsCardFactory
 * @see com.example.teams.cards.TeamsFormatFactory
 */
package com.example.teams.cards;
