package com.example.teams.domain;

public enum EventType {
    PAYMENT_AUTHORIZED("결제 승인 알림"),
    CAPTURE_SUCCEEDED("매입 완료 알림"),
    CAPTURE_FAILED("매입 실패 경보"),
    REFUND_REQUESTED("환불 승인 요청"),
    REFUND_COMPLETED("환불 처리 완료"),
    REFUND_REJECTED("환불 반려 안내"),
    TRANSACTION_INQUIRY("채널 거래 조회 응답");

    private final String title;

    EventType(String title) {
        this.title = title;
    }

    public String getTitle() {
        return title;
    }
}
