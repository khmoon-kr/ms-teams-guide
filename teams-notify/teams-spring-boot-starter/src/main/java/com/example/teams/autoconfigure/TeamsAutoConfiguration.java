package com.example.teams.autoconfigure;

import com.example.teams.TeamsNotifier;
import com.example.teams.cards.TeamsCardFactory;
import com.example.teams.cards.TeamsFormatFactory;
import com.example.teams.client.TeamsWebhookClient;
import com.example.teams.config.TeamsProperties;
import com.example.teams.config.TeamsSettings;
import com.example.teams.security.OutgoingWebhookVerifier;
import com.example.teams.security.TeamsLinkSigner;
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
@EnableConfigurationProperties(TeamsProperties.class)
public class TeamsAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public TeamsSettings teamsSettings(TeamsProperties properties) {
        return properties.toSettings();
    }

    @Bean
    @ConditionalOnMissingBean
    public TeamsLinkSigner teamsLinkSigner(TeamsSettings settings) {
        return new TeamsLinkSigner(settings);
    }

    @Bean
    @ConditionalOnMissingBean
    public OutgoingWebhookVerifier outgoingWebhookVerifier(TeamsSettings settings) {
        return new OutgoingWebhookVerifier(settings);
    }

    @Bean
    @ConditionalOnMissingBean
    public TeamsCardFactory teamsCardFactory(TeamsLinkSigner linkSigner) {
        return new TeamsCardFactory(linkSigner);
    }

    @Bean
    @ConditionalOnMissingBean
    public TeamsFormatFactory teamsFormatFactory(
            TeamsLinkSigner linkSigner,
            TeamsCardFactory adaptiveCardFactory,
            ObjectProvider<ObjectMapper> objectMapperProvider
    ) {
        ObjectMapper mapper = objectMapperProvider.getIfAvailable();
        return mapper != null
                ? new TeamsFormatFactory(linkSigner, adaptiveCardFactory, mapper)
                : new TeamsFormatFactory(linkSigner, adaptiveCardFactory);
    }

    @Bean
    @ConditionalOnMissingBean
    public TeamsWebhookClient teamsWebhookClient(
            TeamsSettings settings,
            ObjectProvider<ObjectMapper> objectMapperProvider
    ) {
        ObjectMapper mapper = objectMapperProvider.getIfAvailable();
        return mapper != null
                ? new TeamsWebhookClient(settings, mapper)
                : new TeamsWebhookClient(settings);
    }

    @Bean
    @ConditionalOnMissingBean
    public TeamsNotifier teamsNotifier(
            TeamsSettings settings,
            TeamsLinkSigner linkSigner,
            OutgoingWebhookVerifier outgoingWebhookVerifier,
            TeamsCardFactory cardFactory,
            TeamsFormatFactory formatFactory,
            TeamsWebhookClient webhookClient
    ) {
        return new TeamsNotifier(
                settings,
                linkSigner,
                outgoingWebhookVerifier,
                cardFactory,
                formatFactory,
                webhookClient
        );
    }
}
