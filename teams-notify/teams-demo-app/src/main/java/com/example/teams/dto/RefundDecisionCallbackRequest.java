package com.example.teams.dto;

import jakarta.validation.constraints.NotBlank;

public record RefundDecisionCallbackRequest(
        @NotBlank(message = "거래 ID(txId)는 필수입니다.")
        String txId,
        boolean approved,
        String actor,
        String comment
) {}
