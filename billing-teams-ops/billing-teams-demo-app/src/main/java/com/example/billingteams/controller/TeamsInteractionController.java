package com.example.billingteams.controller;

import com.example.billingteams.cards.BillingAdaptiveCardFactory;
import com.example.billingteams.client.TeamsWorkflowWebhookClient;
import com.example.billingteams.client.TeamsWorkflowWebhookClient.TeamsDispatchRecord;
import com.example.billingteams.domain.PaymentTransaction;
import com.example.billingteams.dto.BillingEventResponse;
import com.example.billingteams.dto.RefundDecisionCallbackRequest;
import com.example.billingteams.security.LinkVerificationResult;
import com.example.billingteams.security.OutgoingWebhookVerifier;
import com.example.billingteams.security.TeamsLinkSigner;
import com.example.billingteams.service.BillingOperationsService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/v1/teams")
public class TeamsInteractionController {

    private static final Pattern TX_ID_PATTERN = Pattern.compile("(TX-[0-9A-Za-z-]+)");

    private final BillingOperationsService billingService;
    private final BillingAdaptiveCardFactory cardFactory;
    private final TeamsWorkflowWebhookClient webhookClient;
    private final TeamsLinkSigner linkSigner;
    private final OutgoingWebhookVerifier outgoingWebhookVerifier;
    private final ObjectMapper objectMapper;

    public TeamsInteractionController(
            BillingOperationsService billingService,
            BillingAdaptiveCardFactory cardFactory,
            TeamsWorkflowWebhookClient webhookClient,
            TeamsLinkSigner linkSigner,
            OutgoingWebhookVerifier outgoingWebhookVerifier,
            ObjectMapper objectMapper
    ) {
        this.billingService = billingService;
        this.cardFactory = cardFactory;
        this.webhookClient = webhookClient;
        this.linkSigner = linkSigner;
        this.outgoingWebhookVerifier = outgoingWebhookVerifier;
        this.objectMapper = objectMapper;
    }

    /**
     * Teams Workflows 양방향 카드(Wait for a response) 등에서 환불 승인/반려 결과를 콜백으로 전달할 때 사용
     */
    @PostMapping("/callbacks/refund-decision")
    public BillingEventResponse handleRefundDecision(@Valid @RequestBody RefundDecisionCallbackRequest request) {
        return billingService.decidePendingRefund(request);
    }

    /**
     * 프론트엔드(SPA 등)에서 API 방식으로 Teams 링크 서명을 검증하고 싶을 때 호출할 수 있는 검증 엔드포인트
     */
    @GetMapping("/verify-link")
    public LinkVerificationResult verifyTeamsLink(
            @RequestParam String txId,
            @RequestParam(required = false) String src,
            @RequestParam(required = false) String cardType,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) Long iat,
            @RequestParam(required = false) Long exp,
            @RequestParam(required = false) String sig
    ) {
        return linkSigner.verify(txId, src, cardType, action, iat, exp, sig);
    }

    /**
     * 발송된 Teams Webhook 페이로드 목록 조회 (Dry-Run 디버깅 및 카드 JSON 확인용)
     */
    @GetMapping("/outbox")
    public List<TeamsDispatchRecord> getOutbox() {
        return webhookClient.getOutbox();
    }

    /**
     * Teams 표준 채널 Outgoing Webhook 핸들러
     * 채널에서 "@빌링조회 TX-20261119-1000" 입력 시 5초 이내에 거래 상세 Adaptive Card 반환
     */
    @PostMapping("/outgoing-webhook")
    public ResponseEntity<Map<String, Object>> handleOutgoingWebhook(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody byte[] rawBody
    ) throws Exception {
        if (!outgoingWebhookVerifier.verifyAuthorizationHeader(authorization, rawBody)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("type", "message", "text", "⚠️ Outgoing Webhook HMAC 서명 검증에 실패했습니다."));
        }

        JsonNode root = objectMapper.readTree(new String(rawBody, StandardCharsets.UTF_8));
        String rawText = root.path("text").asText("");
        // Teams 멘션 태그 (<at>빌링조회</at>) 제거 후 TX ID 추출
        String cleaned = rawText.replaceAll("<at>.*?</at>", "").trim();

        Matcher matcher = TX_ID_PATTERN.matcher(cleaned);
        if (!matcher.find()) {
            return ResponseEntity.ok(Map.of(
                    "type", "message",
                    "text", "조회할 거래 ID(`TX-...`)를 함께 입력해 주세요. 예: `@빌링조회 TX-20261119-1000`"
            ));
        }

        String txId = matcher.group(1);
        Optional<PaymentTransaction> found = billingService.findTransaction(txId);
        if (found.isEmpty()) {
            return ResponseEntity.ok(Map.of(
                    "type", "message",
                    "text", "해당 거래 ID를 찾을 수 없습니다: `" + txId + "`"
            ));
        }

        Map<String, Object> cardEnvelope = cardFactory.buildTransactionInquiryCard(found.get()).teamsEnvelope();
        return ResponseEntity.ok(cardEnvelope);
    }
}
