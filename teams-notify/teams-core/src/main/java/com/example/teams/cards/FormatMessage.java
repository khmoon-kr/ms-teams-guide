package com.example.teams.cards;

import com.example.teams.domain.TeamsFormat;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * {@link TeamsFormatFactory}가 생성한 MS Teams 알림 페이로드와 부가 메타데이터를 담는 불변(Immutable) 결과 레코드입니다.
 *
 * <p>내부의 {@code primaryPayload}와 {@code workflowsTriggerPayload} 맵은 생성 시점에 방어적으로 복사되어
 * 수정 불가능한(Unmodifiable) 맵으로 보관됩니다.</p>
 *
 * @param format                 알림 포맷 종류 ({@link TeamsFormat})
 * @param displayName            포맷의 한국어 표시 이름
 * @param supportedChannels      해당 포맷을 전송할 수 있는 MS Teams 연동 채널 요약
 * @param recommendedUseCase     권장 활용 시나리오 설명
 * @param resourceId             알림 대상 리소스·문서 식별자
 * @param signedEntryUrl         본문 또는 버튼에 삽입된 {@code HMAC-SHA256} 서명 진입 URL
 * @param targetEndpointHint     실제 발송 시 호출해야 할 Graph API 경로 또는 Workflows 액션 안내
 * @param generatedAt            페이로드 생성 시각 (UTC {@link Instant})
 * @param primaryPayload         Graph API / Bot Framework / Webhook 표준 규격 JSON 맵 (수정 불가)
 * @param workflowsTriggerPayload Power Automate Workflows 트리거 연동 참고용 JSON 맵 (수정 불가)
 * @param prettyPrimaryJson      들여쓰기가 적용된 {@code primaryPayload} JSON 문자열
 * @param prettyWorkflowsJson    들여쓰기가 적용된 {@code workflowsTriggerPayload} JSON 문자열
 * @see TeamsFormatFactory
 */
public record FormatMessage(
        TeamsFormat format,
        String displayName,
        String supportedChannels,
        String recommendedUseCase,
        String resourceId,
        String signedEntryUrl,
        String targetEndpointHint,
        Instant generatedAt,
        Map<String, Object> primaryPayload,
        Map<String, Object> workflowsTriggerPayload,
        String prettyPrimaryJson,
        String prettyWorkflowsJson
) {
    /**
     * 필수 인자 검증 및 방어적 불변 복사를 수행하는 정규 생성자입니다.
     *
     * @param format                 알림 포맷 종류 ({@link TeamsFormat})
     * @param displayName            포맷의 한국어 표시 이름
     * @param supportedChannels      해당 포맷을 전송할 수 있는 MS Teams 연동 채널 요약
     * @param recommendedUseCase     권장 활용 시나리오 설명
     * @param resourceId             알림 대상 리소스·문서 식별자
     * @param signedEntryUrl         본문 또는 버튼에 삽입된 {@code HMAC-SHA256} 서명 진입 URL
     * @param targetEndpointHint     실제 발송 시 호출해야 할 Graph API 경로 또는 Workflows 액션 안내
     * @param generatedAt            페이로드 생성 시각 (UTC {@link Instant})
     * @param primaryPayload         Graph API / Bot Framework / Webhook 표준 규격 JSON 맵 (수정 불가)
     * @param workflowsTriggerPayload Power Automate Workflows 트리거 연동 참고용 JSON 맵 (수정 불가)
     * @param prettyPrimaryJson      들여쓰기가 적용된 {@code primaryPayload} JSON 문자열
     * @param prettyWorkflowsJson    들여쓰기가 적용된 {@code workflowsTriggerPayload} JSON 문자열
     */
    public FormatMessage {
        Objects.requireNonNull(format, "format must not be null");
        primaryPayload = primaryPayload != null
                ? Collections.unmodifiableMap(new LinkedHashMap<>(primaryPayload))
                : Map.of();
        workflowsTriggerPayload = workflowsTriggerPayload != null
                ? Collections.unmodifiableMap(new LinkedHashMap<>(workflowsTriggerPayload))
                : Map.of();
    }
}
