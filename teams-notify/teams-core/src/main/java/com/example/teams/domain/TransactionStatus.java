package com.example.teams.domain;

public enum TransactionStatus {
    AUTHORIZED("결제 승인"),
    CAPTURED("매입 완료"),
    CAPTURE_FAILED("매입 실패"),
    REFUND_REQUESTED("환불 승인 대기"),
    PARTIALLY_REFUNDED("부분 환불 완료"),
    REFUNDED("전액 환불 완료");

    private final String label;

    TransactionStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
