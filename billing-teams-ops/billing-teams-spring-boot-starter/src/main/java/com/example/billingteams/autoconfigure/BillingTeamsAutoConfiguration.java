package com.example.billingteams.autoconfigure;

import com.example.billingteams.BillingTeamsNotifier;
import com.example.billingteams.cards.BillingAdaptiveCardFactory;
import com.example.billingteams.cards.TeamsMultiFormatNotificationFactory;
import com.example.billingteams.client.TeamsWorkflowWebhookClient;
import com.example.billingteams.config.BillingTeamsProperties;
import com.example.billingteams.config.TeamsClientSettings;
import com.example.billingteams.security.OutgoingWebhookVerifier;
import com.example.billingteams.security.TeamsLinkSigner;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 다른 Spring Boot 프로젝트에서 의존성만 추가하면 MS Teams 알림·서명 컴포넌트를 자동으로 빈 등록해 주는 AutoConfiguration.
 * 모든 빈에 {@link ConditionalOnMissingBean}이 선언되어 있어 사용처에서 원하는 컴포넌트만 손쉽게 재정의할 수 있습니다.
 */
@AutoConfiguration
@EnableConfigurationProperties(BillingTeamsProperties.class)
public class BillingTeamsAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public TeamsClientSettings teamsClientSettings(BillingTeamsProperties properties) {
        return properties.toClientSettings();
    }

    @Bean
    @ConditionalOnMissingBean
    public TeamsLinkSigner teamsLinkSigner(TeamsClientSettings settings) {
        return new TeamsLinkSigner(settings);
    }

    @Bean
    @ConditionalOnMissingBean
    public OutgoingWebhookVerifier outgoingWebhookVerifier(TeamsClientSettings settings) {
        return new OutgoingWebhookVerifier(settings);
    }

    @Bean
    @ConditionalOnMissingBean
    public BillingAdaptiveCardFactory billingAdaptiveCardFactory(TeamsLinkSigner linkSigner) {
        return new BillingAdaptiveCardFactory(linkSigner);
    }

    @Bean
    @ConditionalOnMissingBean
    public TeamsMultiFormatNotificationFactory teamsMultiFormatNotificationFactory(
            TeamsLinkSigner linkSigner,
            BillingAdaptiveCardFactory adaptiveCardFactory,
            ObjectProvider<ObjectMapper> objectMapperProvider
    ) {
        ObjectMapper mapper = objectMapperProvider.getIfAvailable();
        return mapper != null
                ? new TeamsMultiFormatNotificationFactory(linkSigner, adaptiveCardFactory, mapper)
                : new TeamsMultiFormatNotificationFactory(linkSigner, adaptiveCardFactory);
    }

    @Bean
    @ConditionalOnMissingBean
    public TeamsWorkflowWebhookClient teamsWorkflowWebhookClient(
            TeamsClientSettings settings,
            ObjectProvider<ObjectMapper> objectMapperProvider
    ) {
        ObjectMapper mapper = objectMapperProvider.getIfAvailable();
        return mapper != null
                ? new TeamsWorkflowWebhookClient(settings, mapper)
                : new TeamsWorkflowWebhookClient(settings);
    }

    @Bean
    @ConditionalOnMissingBean
    public BillingTeamsNotifier billingTeamsNotifier(
            TeamsClientSettings settings,
            TeamsLinkSigner linkSigner,
            OutgoingWebhookVerifier outgoingWebhookVerifier,
            BillingAdaptiveCardFactory cardFactory,
            TeamsMultiFormatNotificationFactory formatFactory,
            TeamsWorkflowWebhookClient webhookClient
    ) {
        return new BillingTeamsNotifier(
                settings,
                linkSigner,
                outgoingWebhookVerifier,
                cardFactory,
                formatFactory,
                webhookClient
        );
    }
}
