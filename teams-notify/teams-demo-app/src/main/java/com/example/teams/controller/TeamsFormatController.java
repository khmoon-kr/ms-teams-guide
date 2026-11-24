package com.example.teams.controller;

import com.example.teams.TeamsNotifier;
import com.example.teams.cards.FormatMessage;
import com.example.teams.client.TeamsWebhookClient.TeamsDispatchRecord;
import com.example.teams.domain.PaymentTransaction;
import com.example.teams.domain.TeamsFormat;
import com.example.teams.dto.FormatSampleRequest;
import com.example.teams.service.BillingOperationsService;
import com.example.teams.service.BillingTeamsComposer;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/teams/formats")
public class TeamsFormatController {

    private final BillingOperationsService billingService;
    private final BillingTeamsComposer composer;
    private final TeamsNotifier teamsNotifier;

    public TeamsFormatController(
            BillingOperationsService billingService,
            BillingTeamsComposer composer,
            TeamsNotifier teamsNotifier
    ) {
        this.billingService = billingService;
        this.composer = composer;
        this.teamsNotifier = teamsNotifier;
    }

    @GetMapping
    public List<Map<String, String>> listAvailableFormats() {
        return Arrays.stream(TeamsFormat.values())
                .map(fmt -> {
                    Map<String, String> item = new LinkedHashMap<>();
                    item.put("format", fmt.name());
                    item.put("displayName", fmt.getDisplayName());
                    item.put("supportedChannels", fmt.getSupportedChannels());
                    item.put("recommendedUseCase", fmt.getRecommendedUseCase());
                    return item;
                })
                .toList();
    }

    @GetMapping("/compare-all")
    public List<FormatMessage> compareAllFormats(@RequestParam(required = false) String txId) {
        PaymentTransaction tx = resolveTransaction(txId);
        return List.of(
                composer.composeHtmlFormat(tx, "billing-oncall@company.com", null),
                composer.composeActivityFeedFormat(tx, "approver@company.com", null),
                composer.composeUrgentFormat(tx, "결제운영당직", null),
                composer.composeReceiptFormat(tx),
                composer.composeHeroFormat(tx),
                composer.composeAdaptiveComparisonFormat(tx)
        );
    }

    @PostMapping("/html-message")
    public FormatMessage generateHtmlMessage(@RequestBody(required = false) FormatSampleRequest req) {
        PaymentTransaction tx = resolveTransaction(req != null ? req.txId() : null);
        return composer.composeHtmlFormat(
                tx,
                req != null ? req.recipientEmail() : null,
                req != null ? req.note() : null
        );
    }

    /**
     * 2번 워크플로(일반/HTML 메시지 전용 웹훅: {@code teams.message-webhook-url})로
     * 거래 HTML 표 메시지 또는 사용자 지정 메시지를 실제로 발송합니다.
     */
    @PostMapping("/html-message/send")
    public TeamsDispatchRecord sendHtmlMessage(@RequestBody(required = false) FormatSampleRequest req) {
        PaymentTransaction tx = resolveTransaction(req != null ? req.txId() : null);
        FormatMessage htmlFmt = composer.composeHtmlFormat(
                tx,
                req != null ? req.recipientEmail() : null,
                req != null ? req.note() : null
        );
        String htmlBody = extractHtmlForWebhook(htmlFmt);
        if (req != null && req.note() != null && !req.note().isBlank()) {
            htmlBody = "<p>" + req.note().trim() + "</p>" + htmlBody;
        }
        return teamsNotifier.sendMessage(
                tx.getTxId(),
                "[HTML 메시지] " + tx.getMerchantName() + " (" + tx.getTxId() + ")",
                htmlBody,
                htmlFmt.signedEntryUrl()
        );
    }

    @PostMapping("/activity-feed")
    public FormatMessage generateActivityFeed(@RequestBody(required = false) FormatSampleRequest req) {
        PaymentTransaction tx = resolveTransaction(req != null ? req.txId() : null);
        return composer.composeActivityFeedFormat(
                tx,
                req != null ? req.recipientEmail() : null,
                req != null ? req.note() : null
        );
    }

    @PostMapping("/urgent-alert")
    public FormatMessage generateUrgentAlert(@RequestBody(required = false) FormatSampleRequest req) {
        PaymentTransaction tx = resolveTransaction(req != null ? req.txId() : null);
        return composer.composeUrgentFormat(
                tx,
                req != null ? req.teamTagName() : null,
                req != null ? req.note() : null
        );
    }

    @PostMapping("/receipt-card")
    public FormatMessage generateReceiptCard(@RequestBody(required = false) FormatSampleRequest req) {
        PaymentTransaction tx = resolveTransaction(req != null ? req.txId() : null);
        return composer.composeReceiptFormat(tx);
    }

    @PostMapping("/hero-card")
    public FormatMessage generateHeroCard(@RequestBody(required = false) FormatSampleRequest req) {
        PaymentTransaction tx = resolveTransaction(req != null ? req.txId() : null);
        return composer.composeHeroFormat(tx);
    }

    private PaymentTransaction resolveTransaction(String txId) {
        if (txId != null && !txId.isBlank()) {
            return billingService.getRequiredTransaction(txId.trim());
        }
        return billingService.listTransactions().stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("현재 등록된 거래가 없습니다."));
    }

    static String extractHtmlForWebhook(FormatMessage htmlFmt) {
        Object bodyObj = htmlFmt.primaryPayload().get("body");
        if (bodyObj instanceof Map<?, ?> bodyMap) {
            Object content = bodyMap.get("content");
            if (content instanceof String html && !html.isBlank()) {
                return html.replaceAll("<at id=\"\\d+\">([^<]+)</at>", "<b>@$1</b>");
            }
        }
        Object workflowsHtml = htmlFmt.workflowsPayload().get("htmlBody");
        return workflowsHtml != null ? workflowsHtml.toString() : "";
    }
}
