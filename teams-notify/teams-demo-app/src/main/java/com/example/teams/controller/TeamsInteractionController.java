package com.example.teams.controller;

import com.example.teams.TeamsNotifier;
import com.example.teams.client.TeamsWebhookClient.TeamsDispatchRecord;
import com.example.teams.domain.PaymentTransaction;
import com.example.teams.dto.BillingEventResponse;
import com.example.teams.dto.RefundDecisionCallbackRequest;
import com.example.teams.security.LinkVerificationResult;
import com.example.teams.service.BillingOperationsService;
import com.example.teams.service.BillingTeamsComposer;
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
    private final BillingTeamsComposer composer;
    private final TeamsNotifier teamsNotifier;
    private final ObjectMapper objectMapper;

    public TeamsInteractionController(
            BillingOperationsService billingService,
            BillingTeamsComposer composer,
            TeamsNotifier teamsNotifier,
            ObjectMapper objectMapper
    ) {
        this.billingService = billingService;
        this.composer = composer;
        this.teamsNotifier = teamsNotifier;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/callbacks/refund-decision")
    public BillingEventResponse handleRefundDecision(@Valid @RequestBody RefundDecisionCallbackRequest request) {
        return billingService.decidePendingRefund(request);
    }

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
        return teamsNotifier.verifySignedLink(txId, src, cardType, action, iat, exp, sig);
    }

    @GetMapping("/outbox")
    public List<TeamsDispatchRecord> getOutbox() {
        return teamsNotifier.webhookClient().getOutbox();
    }

    @PostMapping("/outgoing-webhook")
    public ResponseEntity<Map<String, Object>> handleOutgoingWebhook(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody byte[] rawBody
    ) throws Exception {
        if (!teamsNotifier.outgoingWebhookVerifier().verifyAuthorizationHeader(authorization, rawBody)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("type", "message", "text", "⚠️ Outgoing Webhook HMAC 서명 검증에 실패했습니다."));
        }

        JsonNode root = objectMapper.readTree(new String(rawBody, StandardCharsets.UTF_8));
        String rawText = root.path("text").asText("");
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

        Map<String, Object> cardEnvelope = composer.composeInquiryCard(found.get()).teamsEnvelope();
        return ResponseEntity.ok(cardEnvelope);
    }
}
