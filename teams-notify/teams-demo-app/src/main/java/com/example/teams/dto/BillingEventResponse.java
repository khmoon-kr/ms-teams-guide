package com.example.teams.dto;

import com.example.teams.domain.BillingEventType;
import com.example.teams.domain.TransactionStatus;

import java.time.Instant;
import java.util.Map;

public record BillingEventResponse(
        String dispatchId,
        String txId,
        BillingEventType eventType,
        TransactionStatus currentStatus,
        long amount,
        long remainingAmount,
        String signedDetailUrl,
        String signedActionUrl,
        boolean webhookLiveDelivered,
        int webhookStatusCode,
        Instant dispatchedAt,
        Map<String, Object> teamsWebhookPayload
) {}
