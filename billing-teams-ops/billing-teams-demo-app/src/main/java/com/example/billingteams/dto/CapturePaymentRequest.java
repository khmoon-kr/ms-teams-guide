package com.example.billingteams.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;

public record CapturePaymentRequest(
        @NotBlank(message = "거래 ID(txId)는 필수입니다.")
        String txId,
        boolean success,
        String failureReason,
        Instant processedAt
) {}
