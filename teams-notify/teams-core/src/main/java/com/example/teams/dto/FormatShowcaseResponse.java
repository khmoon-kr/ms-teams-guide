package com.example.teams.dto;

import com.example.teams.domain.TeamsFormat;

import java.time.Instant;
import java.util.Map;

public record FormatShowcaseResponse(
        TeamsFormat format,
        String displayName,
        String supportedChannels,
        String recommendedUseCase,
        String txId,
        String signedEntryUrl,
        String targetEndpointHint,
        Instant generatedAt,
        Map<String, Object> primaryPayload,
        Map<String, Object> workflowsTriggerPayload,
        String prettyPrimaryJson,
        String prettyWorkflowsJson
) {}
