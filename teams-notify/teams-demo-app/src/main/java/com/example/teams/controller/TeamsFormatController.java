package com.example.teams.controller;

import com.example.teams.cards.TeamsFormatFactory;
import com.example.teams.domain.PaymentTransaction;
import com.example.teams.domain.TeamsFormat;
import com.example.teams.dto.FormatSampleRequest;
import com.example.teams.dto.FormatShowcaseResponse;
import com.example.teams.service.BillingOperationsService;
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
    private final TeamsFormatFactory formatFactory;

    public TeamsFormatController(
            BillingOperationsService billingService,
            TeamsFormatFactory formatFactory
    ) {
        this.billingService = billingService;
        this.formatFactory = formatFactory;
    }

    /**
     * Teams에서 활용 가능한 전체 알림 포맷 카탈로그 조회
     */
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

    /**
     * 특정 거래에 대해 6가지 알림 포맷 전체를 한 번에 생성하여 비교 조회
     */
    @GetMapping("/compare-all")
    public List<FormatShowcaseResponse> compareAllFormats(@RequestParam(required = false) String txId) {
        PaymentTransaction tx = resolveTransaction(txId);
        return List.of(
                formatFactory.buildHtmlMessage(tx, "billing-oncall@company.com", null),
                formatFactory.buildActivityFeedAlert(tx, "approver@company.com", null),
                formatFactory.buildUrgentTagAlert(tx, "결제운영당직", null),
                formatFactory.buildReceiptCard(tx),
                formatFactory.buildHeroCard(tx),
                formatFactory.buildAdaptiveCardComparison(tx)
        );
    }

    @PostMapping("/html-message")
    public FormatShowcaseResponse generateHtmlMessage(@RequestBody(required = false) FormatSampleRequest req) {
        PaymentTransaction tx = resolveTransaction(req != null ? req.txId() : null);
        return formatFactory.buildHtmlMessage(
                tx,
                req != null ? req.recipientEmail() : null,
                req != null ? req.note() : null
        );
    }

    @PostMapping("/activity-feed")
    public FormatShowcaseResponse generateActivityFeed(@RequestBody(required = false) FormatSampleRequest req) {
        PaymentTransaction tx = resolveTransaction(req != null ? req.txId() : null);
        return formatFactory.buildActivityFeedAlert(
                tx,
                req != null ? req.recipientEmail() : null,
                req != null ? req.note() : null
        );
    }

    @PostMapping("/urgent-alert")
    public FormatShowcaseResponse generateUrgentAlert(@RequestBody(required = false) FormatSampleRequest req) {
        PaymentTransaction tx = resolveTransaction(req != null ? req.txId() : null);
        return formatFactory.buildUrgentTagAlert(
                tx,
                req != null ? req.teamTagName() : null,
                req != null ? req.note() : null
        );
    }

    @PostMapping("/receipt-card")
    public FormatShowcaseResponse generateReceiptCard(@RequestBody(required = false) FormatSampleRequest req) {
        PaymentTransaction tx = resolveTransaction(req != null ? req.txId() : null);
        return formatFactory.buildReceiptCard(tx);
    }

    @PostMapping("/hero-card")
    public FormatShowcaseResponse generateHeroCard(@RequestBody(required = false) FormatSampleRequest req) {
        PaymentTransaction tx = resolveTransaction(req != null ? req.txId() : null);
        return formatFactory.buildHeroCard(tx);
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
