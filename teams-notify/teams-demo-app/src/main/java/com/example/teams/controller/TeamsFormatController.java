package com.example.teams.controller;

import com.example.teams.cards.FormatMessage;
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

    public TeamsFormatController(
            BillingOperationsService billingService,
            BillingTeamsComposer composer
    ) {
        this.billingService = billingService;
        this.composer = composer;
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
}
