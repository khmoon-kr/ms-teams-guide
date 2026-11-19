package com.example.teams.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;

public record AuthorizePaymentRequest(
        String txId,
        @NotBlank(message = "주문번호(orderId)는 필수입니다.")
        String orderId,
        @NotBlank(message = "가맹점/서비스명(merchantName)은 필수입니다.")
        String merchantName,
        @Min(value = 100, message = "결제 금액(amount)은 100원 이상이어야 합니다.")
        long amount,
        @NotBlank(message = "결제수단(paymentMethod)은 필수입니다.")
        String paymentMethod,
        String authCode,
        Instant approvedAt
) {}
