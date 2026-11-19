package com.example.teams.dto;

public record FormatSampleRequest(
        String txId,
        String recipientEmail,
        String teamTagName,
        String note
) {}
