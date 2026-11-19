package com.example.billingteams.security;

import com.example.billingteams.BillingTeamsNotifier;
import com.example.billingteams.config.TeamsClientSettings;
import com.example.billingteams.domain.BillingTransactionSnapshot;
import com.example.billingteams.domain.TeamsNotificationFormat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TeamsLinkSignerTest {

    private TeamsLinkSigner signer;
    private final Instant fixedNow = Instant.parse("2026-11-19T09:00:00Z");

    @BeforeEach
    void setUp() {
        TeamsClientSettings settings = TeamsClientSettings.builder()
                .baseUrl("https://billing-ops.example.com")
                .entryPathTemplate("/admin/payments/{txId}")
                .linkSecret("unit-test-hmac-secret-2026")
                .linkTtlMinutes(30)
                .build();
        signer = new TeamsLinkSigner(settings, Clock.fixed(fixedNow, ZoneOffset.UTC));
    }

    @Test
    void validSignedUrlWithCustomPathTemplatePassesVerification() {
        String url = signer.createSignedTransactionUrl("TX-20261119-001", "PAYMENT_AUTHORIZED", "view");
        assertThat(url).startsWith("https://billing-ops.example.com/admin/payments/TX-20261119-001?");

        Map<String, String> params = parseQueryParams(url);
        LinkVerificationResult result = signer.verifyQueryMap("TX-20261119-001", params);

        assertThat(result.status()).isEqualTo(LinkVerificationStatus.VERIFIED_TEAMS);
        assertThat(result.isVerified()).isTrue();
    }

    @Test
    void signCustomUrlAppendsParamsWithExistingQueryString() {
        String signed = signer.signCustomUrl(
                "https://portal.other-service.io/settlements/detail?tenant=kr",
                "SET-9910",
                "RECEIPT_CARD",
                "approve"
        );
        assertThat(signed).contains("tenant=kr&src=msteams&cardType=RECEIPT_CARD&action=approve");

        Map<String, String> params = parseQueryParams(signed);
        LinkVerificationResult result = signer.verifyQueryMap("SET-9910", params);
        assertThat(result.isVerified()).isTrue();
        assertThat(result.cardType()).isEqualTo("RECEIPT_CARD");
    }

    @Test
    void directAccessWithoutParamsReturnsDirectUnverified() {
        LinkVerificationResult result = signer.verify("TX-20261119-001", null, null, null, null, null, null);
        assertThat(result.status()).isEqualTo(LinkVerificationStatus.DIRECT_UNVERIFIED);
        assertThat(result.isVerified()).isFalse();
    }

    @Test
    void tamperedParameterFailsSignatureCheck() {
        String url = signer.createSignedTransactionUrl("TX-20261119-001", "PAYMENT_AUTHORIZED", "view");
        Map<String, String> params = parseQueryParams(url);
        params.put("action", "refund"); // 공격자가 action 변조

        LinkVerificationResult result = signer.verifyQueryMap("TX-20261119-001", params);
        assertThat(result.status()).isEqualTo(LinkVerificationStatus.INVALID_SIGNATURE);
        assertThat(result.isVerified()).isFalse();
    }

    @Test
    void expiredLinkReturnsExpiredStatus() {
        long pastIat = fixedNow.getEpochSecond() - 3600;
        long pastExp = fixedNow.getEpochSecond() - 60;
        String url = signer.createSignedTransactionUrl("TX-20261119-001", "PAYMENT_AUTHORIZED", "view", pastIat, pastExp);
        Map<String, String> params = parseQueryParams(url);

        LinkVerificationResult result = signer.verifyQueryMap("TX-20261119-001", params);
        assertThat(result.status()).isEqualTo(LinkVerificationStatus.EXPIRED);
        assertThat(result.isVerified()).isFalse();
    }

    @Test
    void pureJavaStandaloneFacadeWorksWithoutSpring() {
        BillingTeamsNotifier notifier = BillingTeamsNotifier.create(
                TeamsClientSettings.builder()
                        .baseUrl("https://console.my-company.com")
                        .entryPathTemplate("/billing/tx/{txId}")
                        .linkSecret("standalone-java-secret-key")
                        .build()
        );

        BillingTransactionSnapshot snapshot = BillingTransactionSnapshot.builder()
                .txId("TX-LIB-777")
                .orderId("ORD-LIB-01")
                .merchantName("외부 정산 서비스")
                .amount(330_000L)
                .paymentMethod("법인카드")
                .authCode("99112233")
                .approvedAt(fixedNow)
                .build();

        var dispatch = notifier.notifyPaymentAuthorized(snapshot);
        assertThat(dispatch.signedDetailUrl()).startsWith("https://console.my-company.com/billing/tx/TX-LIB-777?");

        var receipt = notifier.formats().buildReceiptCard(snapshot);
        assertThat(receipt.format()).isEqualTo(TeamsNotificationFormat.RECEIPT_CARD);
        assertThat(notifier.verifySignedQueryMap("TX-LIB-777", parseQueryParams(receipt.signedEntryUrl())).isVerified()).isTrue();
    }

    private static Map<String, String> parseQueryParams(String rawUrl) {
        URI uri = URI.create(rawUrl);
        Map<String, String> map = new LinkedHashMap<>();
        if (uri.getRawQuery() == null) {
            return map;
        }
        for (String pair : uri.getRawQuery().split("&")) {
            int idx = pair.indexOf('=');
            if (idx > 0) {
                String k = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8);
                String v = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8);
                map.put(k, v);
            }
        }
        return map;
    }
}
