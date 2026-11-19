package com.example.teams.cards;

import com.example.teams.domain.TeamsFormat;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 6종 MS Teams 알림 포맷 생성 결과를 담는 도메인 중립 불변(Immutable) 레코드. (규칙 3.2)
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
