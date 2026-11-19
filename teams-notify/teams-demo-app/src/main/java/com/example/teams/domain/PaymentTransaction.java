package com.example.teams.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PaymentTransaction implements TransactionView {

    public record AuditLog(Instant timestamp, String title, String detail) {}

    private final String txId;
    private final String orderId;
    private final String merchantName;
    private final long amount;
    private final String paymentMethod;
    private final String authCode;
    private final Instant approvedAt;

    private TransactionStatus status;
    private Instant capturedAt;
    private String lastFailureReason;
    private long refundedAmount;
    private long pendingRefundAmount;
    private String pendingRefundReason;
    private String pendingRefundRequester;
    private final List<AuditLog> history = new ArrayList<>();

    public PaymentTransaction(
            String txId,
            String orderId,
            String merchantName,
            long amount,
            String paymentMethod,
            String authCode,
            Instant approvedAt
    ) {
        this.txId = txId;
        this.orderId = orderId;
        this.merchantName = merchantName;
        this.amount = amount;
        this.paymentMethod = paymentMethod;
        this.authCode = authCode;
        this.approvedAt = approvedAt;
        this.status = TransactionStatus.AUTHORIZED;
        addLog("결제 승인 완료", "승인번호 " + authCode + " / 결제수단 " + paymentMethod);
    }

    public synchronized void markCaptured(Instant processedAt) {
        this.status = this.refundedAmount > 0 ? TransactionStatus.PARTIALLY_REFUNDED : TransactionStatus.CAPTURED;
        this.capturedAt = processedAt;
        this.lastFailureReason = null;
        addLog("매입 확정 완료", "PG 매입 정상 처리");
    }

    public synchronized void markCaptureFailed(String reason, Instant processedAt) {
        this.status = TransactionStatus.CAPTURE_FAILED;
        this.lastFailureReason = reason;
        addLog("매입 실패", "사유: " + reason);
    }

    public synchronized void requestRefund(long refundAmount, String reason, String requestedBy) {
        validateRefundable(refundAmount);
        this.pendingRefundAmount = refundAmount;
        this.pendingRefundReason = reason;
        this.pendingRefundRequester = requestedBy;
        this.status = TransactionStatus.REFUND_REQUESTED;
        addLog("환불 승인 요청", String.format("요청 금액 %,d원 / 요청자: %s / 사유: %s", refundAmount, requestedBy, reason));
    }

    public synchronized void completeRefund(long refundAmount, String reason, String actor) {
        validateRefundable(refundAmount);
        this.refundedAmount += refundAmount;
        this.pendingRefundAmount = 0;
        this.pendingRefundReason = null;
        this.pendingRefundRequester = null;
        this.status = (this.refundedAmount >= this.amount)
                ? TransactionStatus.REFUNDED
                : TransactionStatus.PARTIALLY_REFUNDED;
        addLog("환불 완료", String.format("환불 금액 %,d원 (잔액 %,d원) / 처리자: %s / 사유: %s",
                refundAmount, getRemainingAmount(), actor, reason));
    }

    public synchronized void rejectPendingRefund(String actor, String comment) {
        long rejectedAmount = this.pendingRefundAmount;
        this.pendingRefundAmount = 0;
        this.pendingRefundReason = null;
        this.pendingRefundRequester = null;
        if (this.refundedAmount > 0) {
            this.status = TransactionStatus.PARTIALLY_REFUNDED;
        } else if (this.capturedAt != null) {
            this.status = TransactionStatus.CAPTURED;
        } else {
            this.status = TransactionStatus.AUTHORIZED;
        }
        addLog("환불 요청 반려", String.format("요청 금액 %,d원 반려 / 처리자: %s / 사유: %s", rejectedAmount, actor, comment));
    }

    private void validateRefundable(long refundAmount) {
        if (refundAmount <= 0) {
            throw new IllegalArgumentException("환불 금액은 0원보다 커야 합니다.");
        }
        if (refundAmount > getRemainingAmount()) {
            throw new IllegalArgumentException(
                    String.format("환불 요청 금액(%,d원)이 환불 가능 잔액(%,d원)을 초과합니다.", refundAmount, getRemainingAmount()));
        }
    }

    public synchronized void addLog(String title, String detail) {
        this.history.add(0, new AuditLog(Instant.now(), title, detail));
    }

    public String getTxId() {
        return txId;
    }

    public String getOrderId() {
        return orderId;
    }

    public String getMerchantName() {
        return merchantName;
    }

    public long getAmount() {
        return amount;
    }

    public synchronized long getRefundedAmount() {
        return refundedAmount;
    }

    public synchronized long getRemainingAmount() {
        return Math.max(0, amount - refundedAmount);
    }

    public synchronized long getPendingRefundAmount() {
        return pendingRefundAmount;
    }

    public synchronized String getPendingRefundReason() {
        return pendingRefundReason;
    }

    public synchronized String getPendingRefundRequester() {
        return pendingRefundRequester;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public String getAuthCode() {
        return authCode;
    }

    public Instant getApprovedAt() {
        return approvedAt;
    }

    public synchronized TransactionStatus getStatus() {
        return status;
    }

    public synchronized Instant getCapturedAt() {
        return capturedAt;
    }

    public synchronized String getLastFailureReason() {
        return lastFailureReason;
    }

    public synchronized List<AuditLog> getHistory() {
        return Collections.unmodifiableList(new ArrayList<>(history));
    }
}
