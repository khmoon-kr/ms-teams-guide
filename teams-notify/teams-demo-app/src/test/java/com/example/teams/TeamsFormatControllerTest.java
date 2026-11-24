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

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TeamsFormatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void allAlternativeTeamsFormatsAndLabPageWorkEndToEnd() throws Exception {
        // 1. 카탈로그 목록 확인 (6종)
        mockMvc.perform(get("/api/v1/teams/formats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(6)));

        // 2. 리치 HTML 메시지 포맷 생성
        mockMvc.perform(post("/api/v1/teams/formats/html-message")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "txId": "TX-20261119-1000",
                                  "recipientEmail": "ops@company.com",
                                  "note": "일일 정산 대사 확인"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.format").value("HTML_MESSAGE"))
                .andExpect(jsonPath("$.primaryPayload.body.contentType").value("html"))
                .andExpect(jsonPath("$.primaryPayload.mentions[0].mentionText").value("결제담당자"));

        // 3. 활동 피드 푸시(🔔) 포맷 생성
        mockMvc.perform(post("/api/v1/teams/formats/activity-feed")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "txId": "TX-20261119-1000",
                                  "recipientEmail": "cfo@company.com"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.format").value("ACTIVITY_FEED"))
                .andExpect(jsonPath("$.primaryPayload.activityType").value("billingApprovalRequired"));

        // 4. 긴급(Urgent) + @팀태그 멘션 포맷 생성
        mockMvc.perform(post("/api/v1/teams/formats/urgent-alert")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "txId": "TX-20261119-1000",
                                  "teamTagName": "결제당직팀"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.format").value("URGENT_TAG_MESSAGE"))
                .andExpect(jsonPath("$.primaryPayload.importance").value("urgent"))
                .andExpect(jsonPath("$.primaryPayload.mentions[0].mentionText").value("결제당직팀"));

        // 5. 영수증 카드(Receipt Card) 생성 및 카드 내부 서명 링크로 VERIFIED_TEAMS 통과 확인
        MvcResult receiptRes = mockMvc.perform(post("/api/v1/teams/formats/receipt-card")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "txId": "TX-20261119-1000" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.format").value("RECEIPT_CARD"))
                .andExpect(jsonPath("$.primaryPayload.attachments[0].contentType")
                        .value("application/vnd.microsoft.card.receipt"))
                .andReturn();

        JsonNode receiptNode = objectMapper.readTree(receiptRes.getResponse().getContentAsString());
        URI signedReceiptUri = URI.create(receiptNode.get("signedEntryUrl").asText());

        mockMvc.perform(get(signedReceiptUri.getPath() + "?" + signedReceiptUri.getQuery()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("VERIFIED_TEAMS")))
                .andExpect(content().string(containsString("RECEIPT_CARD")));

        // 6. 심플 Hero Card 생성 확인
        mockMvc.perform(post("/api/v1/teams/formats/hero-card")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "txId": "TX-20261119-1000" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.format").value("HERO_CARD"))
                .andExpect(jsonPath("$.primaryPayload.attachments[0].contentType")
                        .value("application/vnd.microsoft.card.hero"));

        // 7. 시각적 비교 웹 화면 (/ops/formats) 렌더링 확인
        mockMvc.perform(get("/ops/formats"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("MS Teams 알림 방식 6종 비교")));

        // 8. 2번 메시지 웹후크(html-message/send) 발송(Dry-Run) 확인
        mockMvc.perform(post("/api/v1/teams/formats/html-message/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "txId": "TX-20261119-1000",
                                  "note": "2번 웹후크 테스트"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventType").value("TEXT_HTML_MESSAGE"))
                .andExpect(jsonPath("$.payload.message", containsString("2번 웹후크 테스트")));

        // 9. 다양한 스타일의 Adaptive Card 쇼케이스 6종 목록 및 일괄 발송 확인
        mockMvc.perform(get("/api/v1/teams/card-styles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(6)));

        mockMvc.perform(post("/api/v1/teams/card-styles/send-all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(6)))
                .andExpect(jsonPath("$[0].payload.attachments[0].content.type").value("AdaptiveCard"));
    }
}
