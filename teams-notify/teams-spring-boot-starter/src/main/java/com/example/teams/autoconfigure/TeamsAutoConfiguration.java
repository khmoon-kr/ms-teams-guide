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

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;

/**
 * 다른 Spring Boot 프로젝트에서 의존성만 추가하면 MS Teams 알림·서명 컴포넌트를 자동으로 빈 등록해 주는 AutoConfiguration.
 * 모든 빈에 {@link ConditionalOnMissingBean}이 선언되어 있으며, 소비 앱의 {@link Clock}, {@link ObjectMapper}, {@link HttpClient} 빈을 우선 존중합니다. (규칙 4)
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
    public TeamsLinkSigner teamsLinkSigner(TeamsSettings settings, ObjectProvider<Clock> clockProvider) {
        Clock clock = clockProvider.getIfAvailable(Clock::systemUTC);
        return new TeamsLinkSigner(settings, clock);
    }

    @Bean
    @ConditionalOnMissingBean
    public OutgoingWebhookVerifier outgoingWebhookVerifier(TeamsSettings settings) {
        return new OutgoingWebhookVerifier(settings);
    }

    @Bean
    @ConditionalOnMissingBean
    public TeamsCardFactory teamsCardFactory(TeamsLinkSigner linkSigner, TeamsSettings settings) {
        return new TeamsCardFactory(linkSigner, settings.getDisplayZone());
    }

    @Bean
    @ConditionalOnMissingBean
    public TeamsFormatFactory teamsFormatFactory(
            TeamsLinkSigner linkSigner,
            ObjectProvider<ObjectMapper> objectMapperProvider,
            ObjectProvider<Clock> clockProvider
    ) {
        ObjectMapper mapper = objectMapperProvider.getIfAvailable();
        Clock clock = clockProvider.getIfAvailable(Clock::systemUTC);
        return new TeamsFormatFactory(linkSigner, mapper, clock);
    }

    @Bean
    @ConditionalOnMissingBean
    public TeamsWebhookClient teamsWebhookClient(
            TeamsSettings settings,
            ObjectProvider<ObjectMapper> objectMapperProvider,
            ObjectProvider<HttpClient> httpClientProvider,
            ObjectProvider<Clock> clockProvider
    ) {
        ObjectMapper mapper = objectMapperProvider.getIfAvailable();
        Clock clock = clockProvider.getIfAvailable(Clock::systemUTC);
        HttpClient httpClient = httpClientProvider.getIfAvailable(() ->
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(settings.getConnectTimeoutSeconds()))
                        .build()
        );
        return new TeamsWebhookClient(settings, mapper, httpClient, clock);
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
