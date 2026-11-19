package com.example.billingteams.service;

import com.example.billingteams.BillingTeamsNotifier;
import com.example.billingteams.client.TeamsWorkflowWebhookClient.TeamsDispatchRecord;
import com.example.billingteams.domain.BillingEventType;
import com.example.billingteams.domain.PaymentTransaction;
import com.example.billingteams.dto.AuthorizePaymentRequest;
import com.example.billingteams.dto.BillingEventResponse;
import com.example.billingteams.dto.CapturePaymentRequest;
import com.example.billingteams.dto.RefundDecisionCallbackRequest;
import com.example.billingteams.dto.RefundPaymentRequest;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class BillingOperationsService {

    public static final long AUTO_APPROVAL_THRESHOLD_KRW = 500_000L;
    private static final DateTimeFormatter DATE_COMPACT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final BillingTeamsNotifier teamsNotifier;
    private final Map<String, PaymentTransaction> ledger = new ConcurrentHashMap<>();
    private final AtomicInteger sequence = new AtomicInteger(1001);

    public BillingOperationsService(BillingTeamsNotifier teamsNotifier) {
        this.teamsNotifier = teamsNotifier;
    }

    @PostConstruct
    public void initSampleData() {
        authorizePayment(new AuthorizePaymentRequest(
                "TX-20261119-1000",
                "ORD-900102",
                "클라우드 엔터프라이즈 구독 (Pro)",
                128_000L,
                "신한법인카드 (4518-****-****-8821)",
                "38492011",
                Instant.now().minusSeconds(300)
        ));
    }

    public BillingEventResponse authorizePayment(AuthorizePaymentRequest req) {
        String txId = (req.txId() != null && !req.txId().isBlank())
                ? req.txId().trim()
                : generateTxId();
        String authCode = (req.authCode() != null && !req.authCode().isBlank())
                ? req.authCode().trim()
                : String.valueOf(ThreadLocalRandom.current().nextInt(10_000_000, 99_999_999));
        Instant approvedAt = req.approvedAt() != null ? req.approvedAt() : Instant.now();

        PaymentTransaction tx = new PaymentTransaction(
                txId,
                req.orderId().trim(),
                req.merchantName().trim(),
                req.amount(),
                req.paymentMethod().trim(),
                authCode,
                approvedAt
        );
        ledger.put(txId, tx);

        TeamsDispatchRecord dispatch = teamsNotifier.notifyPaymentAuthorized(tx);
        return toResponse(tx, dispatch);
    }

    public BillingEventResponse capturePayment(CapturePaymentRequest req) {
        PaymentTransaction tx = getRequiredTransaction(req.txId());
        Instant processedAt = req.processedAt() != null ? req.processedAt() : Instant.now();

        if (req.success()) {
            tx.markCaptured(processedAt);
        } else {
            String reason = (req.failureReason() != null && !req.failureReason().isBlank())
                    ? req.failureReason().trim()
                    : "PG_CAPTURE_TIMEOUT (매입 게이트웨이 응답 지연)";
            tx.markCaptureFailed(reason, processedAt);
        }

        TeamsDispatchRecord dispatch = teamsNotifier.notifyCaptureResult(tx, req.success(), processedAt);
        return toResponse(tx, dispatch);
    }

    public BillingEventResponse refundPayment(RefundPaymentRequest req) {
        PaymentTransaction tx = getRequiredTransaction(req.txId());
        String actor = (req.requestedBy() != null && !req.requestedBy().isBlank())
                ? req.requestedBy().trim()
                : "고객센터/API";

        boolean needApproval = req.requireApproval() != null
                ? req.requireApproval()
                : req.refundAmount() >= AUTO_APPROVAL_THRESHOLD_KRW;

        TeamsDispatchRecord dispatch;
        if (needApproval) {
            tx.requestRefund(req.refundAmount(), req.reason().trim(), actor);
            dispatch = teamsNotifier.notifyRefund(tx, BillingEventType.REFUND_REQUESTED, req.refundAmount(), req.reason().trim(), actor);
        } else {
            tx.completeRefund(req.refundAmount(), req.reason().trim(), actor);
            dispatch = teamsNotifier.notifyRefund(tx, BillingEventType.REFUND_COMPLETED, req.refundAmount(), req.reason().trim(), actor);
        }

        return toResponse(tx, dispatch);
    }

    public BillingEventResponse decidePendingRefund(RefundDecisionCallbackRequest req) {
        PaymentTransaction tx = getRequiredTransaction(req.txId());
        if (tx.getPendingRefundAmount() <= 0) {
            throw new IllegalStateException("현재 승인 대기 중인 환불 요청이 없는 거래입니다: " + tx.getTxId());
        }

        String actor = (req.actor() != null && !req.actor().isBlank())
                ? req.actor().trim()
                : "Teams 운영자";
        String comment = (req.comment() != null && !req.comment().isBlank())
                ? req.comment().trim()
                : (req.approved() ? "Teams 인증 링크에서 환불 승인" : "Teams 인증 링크에서 환불 반려");

        long targetAmount = tx.getPendingRefundAmount();
        TeamsDispatchRecord dispatch;
        if (req.approved()) {
            tx.completeRefund(targetAmount, comment, actor);
            dispatch = teamsNotifier.notifyRefund(tx, BillingEventType.REFUND_COMPLETED, targetAmount, comment, actor);
        } else {
            tx.rejectPendingRefund(actor, comment);
            dispatch = teamsNotifier.notifyRefund(tx, BillingEventType.REFUND_REJECTED, targetAmount, comment, actor);
        }

        return toResponse(tx, dispatch);
    }

    public PaymentTransaction getRequiredTransaction(String txId) {
        PaymentTransaction tx = ledger.get(txId);
        if (tx == null) {
            throw new NoSuchElementException("거래 내역을 찾을 수 없습니다: " + txId);
        }
        return tx;
    }

    public Optional<PaymentTransaction> findTransaction(String txId) {
        return Optional.ofNullable(ledger.get(txId));
    }

    public List<PaymentTransaction> listTransactions() {
        List<PaymentTransaction> list = new ArrayList<>(ledger.values());
        list.sort(Comparator.comparing(PaymentTransaction::getApprovedAt).reversed());
        return list;
    }

    private String generateTxId() {
        String datePart = LocalDate.now(ZoneId.of("Asia/Seoul")).format(DATE_COMPACT);
        return "TX-" + datePart + "-" + sequence.getAndIncrement();
    }

    private BillingEventResponse toResponse(PaymentTransaction tx, TeamsDispatchRecord dispatch) {
        return new BillingEventResponse(
                dispatch.dispatchId(),
                tx.getTxId(),
                dispatch.eventType(),
                tx.getStatus(),
                tx.getAmount(),
                tx.getRemainingAmount(),
                dispatch.signedDetailUrl(),
                dispatch.signedActionUrl(),
                dispatch.liveDelivered(),
                dispatch.httpStatus(),
                dispatch.dispatchedAt(),
                dispatch.payload()
        );
    }
}
