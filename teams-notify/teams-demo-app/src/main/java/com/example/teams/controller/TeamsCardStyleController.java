package com.example.teams.controller;

import com.example.teams.client.TeamsWebhookClient.TeamsDispatchRecord;
import com.example.teams.service.AdaptiveCardShowcaseService;
import com.example.teams.service.AdaptiveCardShowcaseService.CardStyleInfo;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 6가지 서로 다른 레이아웃의 Adaptive Card v1.4 쇼케이스를 1번 웹후크({@code teams.webhook-url})로
 * 개별 또는 일괄 발송하는 REST API 컨트롤러입니다.
 */
@RestController
@RequestMapping("/api/v1/teams/card-styles")
public class TeamsCardStyleController {

    private final AdaptiveCardShowcaseService showcaseService;

    public TeamsCardStyleController(AdaptiveCardShowcaseService showcaseService) {
        this.showcaseService = showcaseService;
    }

    @GetMapping
    public List<CardStyleInfo> listStyles() {
        return showcaseService.listStyles();
    }

    @PostMapping("/{styleId}/send")
    public TeamsDispatchRecord sendStyle(@PathVariable String styleId) {
        return showcaseService.sendStyle(styleId);
    }

    @PostMapping("/send-all")
    public List<TeamsDispatchRecord> sendAllStyles() {
        return showcaseService.sendAllStyles();
    }
}
