package com.example.teams.controller;

import com.example.teams.cards.TeamsCardFactory;
import com.example.teams.cards.TeamsFormatFactory;
import com.example.teams.client.TeamsWebhookClient;
import com.example.teams.config.TeamsProperties;
import com.example.teams.domain.EventType;
import com.example.teams.domain.PaymentTransaction;
import com.example.teams.dto.AuthorizePaymentRequest;
import com.example.teams.dto.BillingEventResponse;
import com.example.teams.dto.CapturePaymentRequest;
import com.example.teams.dto.FormatShowcaseResponse;
import com.example.teams.dto.RefundDecisionCallbackRequest;
import com.example.teams.dto.RefundPaymentRequest;
import com.example.teams.security.LinkVerificationResult;
import com.example.teams.security.TeamsLinkSigner;
import com.example.teams.service.BillingOperationsService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
public class OpsViewController {

    public record DemoLinks(
            String validTeamsUrl,
            String tamperedUrl,
            String expiredUrl,
            String directUrl
    ) {}

    private final BillingOperationsService billingService;
    private final TeamsWebhookClient webhookClient;
    private final TeamsLinkSigner linkSigner;
    private final TeamsProperties properties;
    private final TeamsFormatFactory formatFactory;

    public OpsViewController(
            BillingOperationsService billingService,
            TeamsWebhookClient webhookClient,
            TeamsLinkSigner linkSigner,
            TeamsProperties properties,
            TeamsFormatFactory formatFactory
    ) {
        this.billingService = billingService;
        this.webhookClient = webhookClient;
        this.linkSigner = linkSigner;
        this.properties = properties;
        this.formatFactory = formatFactory;
    }

    @GetMapping("/ops/formats")
    public String formatsLab(@RequestParam(required = false) String txId, Model model) {
        List<PaymentTransaction> transactions = billingService.listTransactions();
        PaymentTransaction selectedTx = (txId != null && !txId.isBlank())
                ? billingService.findTransaction(txId).orElseGet(() -> transactions.get(0))
                : transactions.get(0);

        long supplyAmount = Math.round(selectedTx.getAmount() / 1.1);
        long vatAmount = selectedTx.getAmount() - supplyAmount;

        FormatShowcaseResponse htmlFmt = formatFactory.buildHtmlMessage(selectedTx, "billing-oncall@company.com", null);
        FormatShowcaseResponse feedFmt = formatFactory.buildActivityFeedAlert(selectedTx, "approver@company.com", null);
        FormatShowcaseResponse urgentFmt = formatFactory.buildUrgentTagAlert(selectedTx, "결제운영당직", null);
        FormatShowcaseResponse receiptFmt = formatFactory.buildReceiptCard(selectedTx);
        FormatShowcaseResponse heroFmt = formatFactory.buildHeroCard(selectedTx);
        FormatShowcaseResponse adaptiveFmt = formatFactory.buildAdaptiveCardComparison(selectedTx);

        model.addAttribute("transactions", transactions);
        model.addAttribute("tx", selectedTx);
        model.addAttribute("approvedAtKst", TeamsCardFactory.formatKst(selectedTx.getApprovedAt()));
        model.addAttribute("supplyAmount", supplyAmount);
        model.addAttribute("vatAmount", vatAmount);
        model.addAttribute("htmlFmt", htmlFmt);
        model.addAttribute("feedFmt", feedFmt);
        model.addAttribute("urgentFmt", urgentFmt);
        model.addAttribute("receiptFmt", receiptFmt);
        model.addAttribute("heroFmt", heroFmt);
        model.addAttribute("adaptiveFmt", adaptiveFmt);
        return "formats-lab";
    }

    @GetMapping({"/", "/ops"})
    public String opsLauncher(Model model) {
        List<PaymentTransaction> transactions = billingService.listTransactions();
        Map<String, DemoLinks> demoLinksByTx = new LinkedHashMap<>();
        long now = Instant.now().getEpochSecond();

        for (PaymentTransaction tx : transactions) {
            String validUrl = linkSigner.createSignedTransactionUrl(
                    tx.getTxId(), EventType.PAYMENT_AUTHORIZED.name(), "view");
            // 서명된 URL의 action 파라미터를 임의로 변조한 테스트 링크
            String tamperedUrl = validUrl.replace("action=view", "action=admin_override");
            // 과거 시각으로 서명하여 만료(EXPIRED) 상태를 재현하는 테스트 링크
            String expiredUrl = linkSigner.createSignedTransactionUrl(
                    tx.getTxId(), EventType.PAYMENT_AUTHORIZED.name(), "view", now - 7200, now - 600);
            String directUrl = "/ops/transactions/" + tx.getTxId();

            demoLinksByTx.put(tx.getTxId(), new DemoLinks(validUrl, tamperedUrl, expiredUrl, directUrl));
        }

        boolean webhookConfigured = properties.getWebhookUrl() != null && !properties.getWebhookUrl().isBlank();
        model.addAttribute("webhookConfigured", webhookConfigured);
        model.addAttribute("transactions", transactions);
        model.addAttribute("demoLinksByTx", demoLinksByTx);
        model.addAttribute("outbox", webhookClient.getOutbox());
        model.addAttribute("linkTtlMinutes", properties.getLinkTtlMinutes());
        return "ops-launcher";
    }

    /**
     * Teams Adaptive Card의 Action.OpenUrl 링크를 클릭했을 때 진입하는 검증 & 운영 처리 페이지
     */
    @GetMapping("/ops/transactions/{txId}")
    public String transactionDetail(
            @PathVariable String txId,
            @RequestParam(required = false) String src,
            @RequestParam(required = false) String cardType,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) Long iat,
            @RequestParam(required = false) Long exp,
            @RequestParam(required = false) String sig,
            Model model
    ) {
        LinkVerificationResult verification = linkSigner.verify(txId, src, cardType, action, iat, exp, sig);
        PaymentTransaction tx = billingService.findTransaction(txId).orElse(null);

        long now = Instant.now().getEpochSecond();
        String freshValidUrl = linkSigner.createSignedTransactionUrl(txId, "OPS_REFRESH", "view");
        String tamperedSampleUrl = freshValidUrl.replace("src=msteams", "src=msteams_hacked");
        String expiredSampleUrl = linkSigner.createSignedTransactionUrl(txId, "OPS_REFRESH", "view", now - 7200, now - 300);

        model.addAttribute("txId", txId);
        model.addAttribute("tx", tx);
        model.addAttribute("verification", verification);
        model.addAttribute("src", src);
        model.addAttribute("cardType", cardType);
        model.addAttribute("action", action != null ? action : "view");
        model.addAttribute("iat", iat);
        model.addAttribute("exp", exp);
        model.addAttribute("sig", sig);
        model.addAttribute("issuedAtKst", TeamsCardFactory.formatKst(verification.issuedAt()));
        model.addAttribute("expiresAtKst", TeamsCardFactory.formatKst(verification.expiresAt()));
        model.addAttribute("freshValidUrl", freshValidUrl);
        model.addAttribute("tamperedSampleUrl", tamperedSampleUrl);
        model.addAttribute("expiredSampleUrl", expiredSampleUrl);
        return "transaction-detail";
    }

    @PostMapping("/ops/simulate/authorize")
    public String simulateAuthorize(
            @RequestParam(defaultValue = "클라우드 정기결제 (Enterprise)") String merchantName,
            @RequestParam(defaultValue = "240000") long amount,
            @RequestParam(defaultValue = "현대카드 (4009-****-****-1190)") String paymentMethod,
            RedirectAttributes redirectAttributes
    ) {
        String orderId = "ORD-" + (100000 + (int) (Math.random() * 899999));
        BillingEventResponse res = billingService.authorizePayment(new AuthorizePaymentRequest(
                null, orderId, merchantName, amount, paymentMethod, null, Instant.now()
        ));
        redirectAttributes.addFlashAttribute("toastMessage",
                "결제 승인 이벤트 생성 및 Teams 카드 발송 완료: " + res.txId());
        return "redirect:/ops";
    }

    @PostMapping("/ops/simulate/capture-fail")
    public String simulateCaptureFailure(@RequestParam String txId, RedirectAttributes redirectAttributes) {
        billingService.capturePayment(new CapturePaymentRequest(
                txId, false, "PG_NETWORK_TIMEOUT (매입사 응답 지연으로 재시도 필요)", Instant.now()
        ));
        redirectAttributes.addFlashAttribute("toastMessage",
                "매입 실패 경보 카드 발송 완료: " + txId);
        return "redirect:/ops";
    }

    @PostMapping("/ops/simulate/refund-request")
    public String simulateRefundRequest(@RequestParam String txId, RedirectAttributes redirectAttributes) {
        PaymentTransaction tx = billingService.getRequiredTransaction(txId);
        long target = Math.min(tx.getRemainingAmount(), Math.max(10_000L, tx.getRemainingAmount() / 2));
        billingService.refundPayment(new RefundPaymentRequest(
                txId, target, "고객 변심 부분 환불 요청 (운영자 확인 필요)", "CS팀 김민수", true
        ));
        redirectAttributes.addFlashAttribute("toastMessage",
                "환불 승인 요청 카드 발송 완료: " + txId);
        return "redirect:/ops";
    }

    /**
     * 검증된 Teams 링크 화면 내에서 [매입 확정] 실행
     */
    @PostMapping("/ops/transactions/{txId}/capture")
    public String executeCaptureFromVerifiedLink(
            @PathVariable String txId,
            @RequestParam String src,
            @RequestParam String cardType,
            @RequestParam String action,
            @RequestParam Long iat,
            @RequestParam Long exp,
            @RequestParam String sig,
            RedirectAttributes redirectAttributes
    ) {
        LinkVerificationResult verification = linkSigner.verify(txId, src, cardType, action, iat, exp, sig);
        if (!verification.isVerified()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Teams 서명 링크 검증 실패로 작업이 거부되었습니다: " + verification.status().getBadgeLabel());
            return "redirect:/ops/transactions/" + txId;
        }

        BillingEventResponse res = billingService.capturePayment(
                new CapturePaymentRequest(txId, true, null, Instant.now()));
        redirectAttributes.addFlashAttribute("toastMessage",
                "매입 확정이 완료되었으며 Teams 채널로 완료 카드가 발송되었습니다.");
        return "redirect:" + res.signedDetailUrl();
    }

    /**
     * 검증된 Teams 링크 화면 내에서 [즉시 환불] 실행
     */
    @PostMapping("/ops/transactions/{txId}/refund")
    public String executeRefundFromVerifiedLink(
            @PathVariable String txId,
            @RequestParam long refundAmount,
            @RequestParam String reason,
            @RequestParam String src,
            @RequestParam String cardType,
            @RequestParam String action,
            @RequestParam Long iat,
            @RequestParam Long exp,
            @RequestParam String sig,
            RedirectAttributes redirectAttributes
    ) {
        LinkVerificationResult verification = linkSigner.verify(txId, src, cardType, action, iat, exp, sig);
        if (!verification.isVerified()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Teams 서명 링크 검증 실패로 작업이 거부되었습니다: " + verification.status().getBadgeLabel());
            return "redirect:/ops/transactions/" + txId;
        }

        try {
            BillingEventResponse res = billingService.refundPayment(
                    new RefundPaymentRequest(txId, refundAmount, reason, "Teams 인증 운영자", false));
            redirectAttributes.addFlashAttribute("toastMessage",
                    String.format("%,d원 환불이 완료되었으며 Teams로 완료 카드가 발송되었습니다.", refundAmount));
            return "redirect:" + res.signedDetailUrl();
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:" + linkSigner.createSignedTransactionUrl(txId, cardType, action, iat, exp);
        }
    }

    /**
     * 검증된 Teams 링크 화면 내에서 대기 중인 환불 요청 [승인 / 반려]
     */
    @PostMapping("/ops/transactions/{txId}/refund-decision")
    public String decidePendingRefundFromVerifiedLink(
            @PathVariable String txId,
            @RequestParam boolean approved,
            @RequestParam(defaultValue = "") String comment,
            @RequestParam String src,
            @RequestParam String cardType,
            @RequestParam String action,
            @RequestParam Long iat,
            @RequestParam Long exp,
            @RequestParam String sig,
            RedirectAttributes redirectAttributes
    ) {
        LinkVerificationResult verification = linkSigner.verify(txId, src, cardType, action, iat, exp, sig);
        if (!verification.isVerified()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Teams 서명 링크 검증 실패로 작업이 거부되었습니다: " + verification.status().getBadgeLabel());
            return "redirect:/ops/transactions/" + txId;
        }

        BillingEventResponse res = billingService.decidePendingRefund(
                new RefundDecisionCallbackRequest(txId, approved, "Teams 인증 운영자", comment));
        redirectAttributes.addFlashAttribute("toastMessage",
                approved ? "환불 승인이 완료되었으며 Teams 완료 카드가 발송되었습니다." : "환불 요청이 반려 처리되었습니다.");
        return "redirect:" + res.signedDetailUrl();
    }
}
