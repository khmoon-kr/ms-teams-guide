package com.example.teams.service;

import com.example.teams.TeamsNotifier;
import com.example.teams.client.TeamsWebhookClient.TeamsDispatchRecord;
import com.example.teams.domain.BillingEventType;
import com.example.teams.domain.PaymentTransaction;
import com.example.teams.dto.AuthorizePaymentRequest;
import com.example.teams.dto.BillingEventResponse;
import com.example.teams.dto.CapturePaymentRequest;
import com.example.teams.dto.RefundDecisionCallbackRequest;
import com.example.teams.dto.RefundPaymentRequest;
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

    private final TeamsNotifier teamsNotifier;
    private final BillingTeamsComposer composer;
    private final Map<String, PaymentTransaction> ledger = new ConcurrentHashMap<>();
    private final AtomicInteger sequence = new AtomicInteger(1001);

    public BillingOperationsService(TeamsNotifier teamsNotifier, BillingTeamsComposer composer) {
        this.teamsNotifier = teamsNotifier;
        this.composer = composer;
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

        TeamsDispatchRecord dispatch = teamsNotifier.sendCard(txId, composer.composeAuthorizedCard(tx));
        return toResponse(tx, BillingEventType.PAYMENT_AUTHORIZED, dispatch);
    }

    public BillingEventResponse capturePayment(CapturePaymentRequest req) {
        PaymentTransaction tx = getRequiredTransaction(req.txId());
        Instant processedAt = req.processedAt() != null ? req.processedAt() : Instant.now();

        BillingEventType eventType;
        if (req.success()) {
            tx.markCaptured(processedAt);
            eventType = BillingEventType.CAPTURE_SUCCEEDED;
        } else {
            String reason = (req.failureReason() != null && !req.failureReason().isBlank())
                    ? req.failureReason().trim()
                    : "PG_CAPTURE_TIMEOUT (매입 게이트웨이 응답 지연)";
            tx.markCaptureFailed(reason, processedAt);
            eventType = BillingEventType.CAPTURE_FAILED;
        }

        TeamsDispatchRecord dispatch = teamsNotifier.sendCard(
                tx.getTxId(),
                composer.composeCaptureCard(tx, req.success(), processedAt)
        );
        return toResponse(tx, eventType, dispatch);
    }

    public BillingEventResponse refundPayment(RefundPaymentRequest req) {
        PaymentTransaction tx = getRequiredTransaction(req.txId());
        String actor = (req.requestedBy() != null && !req.requestedBy().isBlank())
                ? req.requestedBy().trim()
                : "고객센터/API";

        boolean needApproval = req.requireApproval() != null
                ? req.requireApproval()
                : req.refundAmount() >= AUTO_APPROVAL_THRESHOLD_KRW;

        BillingEventType eventType;
        if (needApproval) {
            tx.requestRefund(req.refundAmount(), req.reason().trim(), actor);
            eventType = BillingEventType.REFUND_REQUESTED;
        } else {
            tx.completeRefund(req.refundAmount(), req.reason().trim(), actor);
            eventType = BillingEventType.REFUND_COMPLETED;
        }

        TeamsDispatchRecord dispatch = teamsNotifier.sendCard(
                tx.getTxId(),
                composer.composeRefundCard(tx, eventType, req.refundAmount(), req.reason().trim(), actor)
        );
        return toResponse(tx, eventType, dispatch);
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
        BillingEventType eventType;
        if (req.approved()) {
            tx.completeRefund(targetAmount, comment, actor);
            eventType = BillingEventType.REFUND_COMPLETED;
        } else {
            tx.rejectPendingRefund(actor, comment);
            eventType = BillingEventType.REFUND_REJECTED;
        }

        TeamsDispatchRecord dispatch = teamsNotifier.sendCard(
                tx.getTxId(),
                composer.composeRefundCard(tx, eventType, targetAmount, comment, actor)
        );
        return toResponse(tx, eventType, dispatch);
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

    private BillingEventResponse toResponse(
            PaymentTransaction tx,
            BillingEventType eventType,
            TeamsDispatchRecord dispatch
    ) {
        return new BillingEventResponse(
                dispatch.dispatchId(),
                tx.getTxId(),
                eventType,
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
