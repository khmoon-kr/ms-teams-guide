package com.example.teams;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TeamsDemoIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void fullLifecycleAuthorizeCaptureRefundAndTeamsLinkVerification() throws Exception {
        // 1. 결제 승인 API 호출
        String authorizeJson = """
                {
                  "txId": "TX-TEST-2026-01",
                  "orderId": "ORD-778899",
                  "merchantName": "빌링 프로 플랜",
                  "amount": 600000,
                  "paymentMethod": "국민카드 (5570-****-****-1234)"
                }
                """;

        MvcResult authResult = mockMvc.perform(post("/api/v1/billing/events/authorize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(authorizeJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.txId").value("TX-TEST-2026-01"))
                .andExpect(jsonPath("$.eventType").value("PAYMENT_AUTHORIZED"))
                .andExpect(jsonPath("$.teamsWebhookPayload.type").value("message"))
                .andExpect(jsonPath("$.teamsWebhookPayload.attachments[0].content.type").value("AdaptiveCard"))
                .andExpect(jsonPath("$.teamsWebhookPayload.attachments[0].content.version").value("1.4"))
                .andReturn();

        JsonNode authNode = objectMapper.readTree(authResult.getResponse().getContentAsString());
        String signedDetailUrl = authNode.get("signedDetailUrl").asText();
        URI uri = URI.create(signedDetailUrl);

        // 2. 카드에 포함된 서명된 링크로 진입 -> VERIFIED_TEAMS 배너 및 운영 액션 잠금 해제 확인
        mockMvc.perform(get(uri.getPath() + "?" + uri.getQuery()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("VERIFIED_TEAMS")))
                .andExpect(content().string(containsString("id=\"ops-action-unlocked\"")));

        // 3. 서명 없이 브라우저로 직접 진입 -> DIRECT_UNVERIFIED 및 운영 액션 잠금 확인
        mockMvc.perform(get("/ops/transactions/TX-TEST-2026-01"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("DIRECT_UNVERIFIED")))
                .andExpect(content().string(containsString("id=\"ops-action-locked\"")));

        // 4. 매입 완료 API 호출
        mockMvc.perform(post("/api/v1/billing/events/capture")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "txId": "TX-TEST-2026-01",
                                  "success": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStatus").value("CAPTURED"));

        // 5. 50만 원 이상 고액 환불 요청 -> REFUND_REQUESTED (승인 대기) 전환 확인
        mockMvc.perform(post("/api/v1/billing/events/refund")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "txId": "TX-TEST-2026-01",
                                  "refundAmount": 500000,
                                  "reason": "플랜 변경 부분 환불",
                                  "requestedBy": "CS팀"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventType").value("REFUND_REQUESTED"))
                .andExpect(jsonPath("$.currentStatus").value("REFUND_REQUESTED"));

        // 6. Teams 콜백으로 환불 최종 승인 -> PARTIALLY_REFUNDED 및 잔액 100,000원 확인
        mockMvc.perform(post("/api/v1/teams/callbacks/refund-decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "txId": "TX-TEST-2026-01",
                                  "approved": true,
                                  "actor": "재무팀장",
                                  "comment": "승인 완료"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventType").value("REFUND_COMPLETED"))
                .andExpect(jsonPath("$.currentStatus").value("PARTIALLY_REFUNDED"))
                .andExpect(jsonPath("$.remainingAmount").value(100000));

        // 7. Teams 채널 Outgoing Webhook (@빌링조회) 호출 검증
        MvcResult webhookResult = mockMvc.perform(post("/api/v1/teams/outgoing-webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "message",
                                  "text": "<at>빌링조회</at> TX-TEST-2026-01"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attachments[0].content.type").value("AdaptiveCard"))
                .andReturn();

        assertThat(webhookResult.getResponse().getContentAsString()).contains("TX-TEST-2026-01");
    }
}
