package com.example.billingteams.dto;

import com.example.billingteams.domain.TeamsNotificationFormat;

import java.time.Instant;
import java.util.Map;

public record FormatShowcaseResponse(
        TeamsNotificationFormat format,
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
