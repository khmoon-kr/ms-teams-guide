package com.example.teams.domain;

import java.time.Instant;

/**
 * 다른 프로젝트에서 라이브러리를 호출할 때 별도 클래스 구현 없이 즉시 생성해 넘길 수 있는 불변 거래 스냅샷 DTO.
 */
public record TransactionSnapshot(
        String txId,
        String orderId,
        String merchantName,
        long amount,
        long refundedAmount,
        String paymentMethod,
        String authCode,
        Instant approvedAt,
        TransactionStatus status,
        String lastFailureReason
) implements TransactionView {

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public String getTxId() {
        return txId;
    }

    @Override
    public String getOrderId() {
        return orderId;
    }

    @Override
    public String getMerchantName() {
        return merchantName;
    }

    @Override
    public long getAmount() {
        return amount;
    }

    @Override
    public long getRefundedAmount() {
        return refundedAmount;
    }

    @Override
    public String getPaymentMethod() {
        return paymentMethod;
    }

    @Override
    public String getAuthCode() {
        return authCode;
    }

    @Override
    public Instant getApprovedAt() {
        return approvedAt;
    }

    @Override
    public TransactionStatus getStatus() {
        return status != null ? status : TransactionStatus.AUTHORIZED;
    }

    @Override
    public String getLastFailureReason() {
        return lastFailureReason;
    }

    public static final class Builder {
        private String txId;
        private String orderId = "-";
        private String merchantName = "-";
        private long amount;
        private long refundedAmount = 0L;
        private String paymentMethod = "-";
        private String authCode = "-";
        private Instant approvedAt = Instant.now();
        private TransactionStatus status = TransactionStatus.AUTHORIZED;
        private String lastFailureReason;

        private Builder() {}

        public Builder txId(String txId) {
            this.txId = txId;
            return this;
        }

        public Builder orderId(String orderId) {
            this.orderId = orderId;
            return this;
        }

        public Builder merchantName(String merchantName) {
            this.merchantName = merchantName;
            return this;
        }

        public Builder amount(long amount) {
            this.amount = amount;
            return this;
        }

        public Builder refundedAmount(long refundedAmount) {
            this.refundedAmount = refundedAmount;
            return this;
        }

        public Builder paymentMethod(String paymentMethod) {
            this.paymentMethod = paymentMethod;
            return this;
        }

        public Builder authCode(String authCode) {
            this.authCode = authCode;
            return this;
        }

        public Builder approvedAt(Instant approvedAt) {
            this.approvedAt = approvedAt;
            return this;
        }

        public Builder status(TransactionStatus status) {
            this.status = status;
            return this;
        }

        public Builder lastFailureReason(String lastFailureReason) {
            this.lastFailureReason = lastFailureReason;
            return this;
        }

        public TransactionSnapshot build() {
            if (txId == null || txId.isBlank()) {
                throw new IllegalArgumentException("txId must not be blank");
            }
            return new TransactionSnapshot(
                    txId, orderId, merchantName, amount, refundedAmount,
                    paymentMethod, authCode, approvedAt, status, lastFailureReason
            );
        }
    }
}
