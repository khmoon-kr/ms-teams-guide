package com.example.billingteams.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record RefundPaymentRequest(
        @NotBlank(message = "거래 ID(txId)는 필수입니다.")
        String txId,
        @Min(value = 1, message = "환불 금액(refundAmount)은 1원 이상이어야 합니다.")
        long refundAmount,
        @NotBlank(message = "환불 사유(reason)는 필수입니다.")
        String reason,
        String requestedBy,
        Boolean requireApproval
) {}
