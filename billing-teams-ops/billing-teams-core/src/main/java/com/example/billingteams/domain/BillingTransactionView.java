package com.example.billingteams.domain;

import java.time.Instant;

/**
 * 외부 서비스의 자체 결제 엔티티(JPA Entity, DTO 등)를 라이브러리 카드 팩토리에
 * 바로 전달할 수 있도록 추상화한 읽기 전용 인터페이스입니다.
 * 직접 구현하기 번거로운 경우 불변 레코드인 {@link BillingTransactionSnapshot}을 사용할 수 있습니다.
 */
public interface BillingTransactionView {

    String getTxId();

    String getOrderId();

    String getMerchantName();

    long getAmount();

    default long getRefundedAmount() {
        return 0L;
    }

    default long getRemainingAmount() {
        return Math.max(0L, getAmount() - getRefundedAmount());
    }

    String getPaymentMethod();

    String getAuthCode();

    Instant getApprovedAt();

    TransactionStatus getStatus();

    default String getLastFailureReason() {
        return null;
    }
}
