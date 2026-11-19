package com.example.billingteams.dto;

import com.example.billingteams.domain.BillingEventType;
import com.example.billingteams.domain.TransactionStatus;

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
