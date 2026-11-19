package com.example.billingteams.dto;

public record FormatSampleRequest(
        String txId,
        String recipientEmail,
        String teamTagName,
        String note
) {}
