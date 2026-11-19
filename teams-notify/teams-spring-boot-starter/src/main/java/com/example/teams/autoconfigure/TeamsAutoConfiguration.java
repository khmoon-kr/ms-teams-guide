package com.example.teams.autoconfigure;

import com.example.teams.TeamsNotifier;
import com.example.teams.cards.TeamsCardFactory;
import com.example.teams.cards.TeamsFormatFactory;
import com.example.teams.client.TeamsWebhookClient;
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
 * Spring Boot 3 애플리케이션에서 {@code teams-spring-boot-starter} 의존성만으로
 * {@link TeamsNotifier} 및 하위 컴포넌트 빈을 자동 등록해 주는 Auto-Configuration 클래스입니다.
 *
 * <p>모든 빈 메서드에 {@link ConditionalOnMissingBean}이 적용되어 있어 소비 애플리케이션이 원하는 빈만
 * 선택적으로 오버라이드할 수 있으며, 컨텍스트 내에 이미 등록된 {@link Clock}, {@link ObjectMapper},
 * {@link HttpClient} 빈이 존재할 경우 {@link ObjectProvider}를 통해 이를 우선 재사용합니다.</p>
 *
 * @see TeamsProperties
 * @see TeamsNotifier
 */
@AutoConfiguration
@EnableConfigurationProperties(TeamsProperties.class)
public class TeamsAutoConfiguration {

    /**
     * 기본 자동 구성 인스턴스를 생성합니다.
     */
    public TeamsAutoConfiguration() {
    }

    /**
     * 바인딩된 {@link TeamsProperties}로부터 불변 {@link TeamsSettings} 빈을 생성합니다.
     *
     * @param properties 바인딩된 {@code teams.*} 프로퍼티
     * @return {@link TeamsSettings} 빈
     */
    @Bean
    @ConditionalOnMissingBean
    public TeamsSettings teamsSettings(TeamsProperties properties) {
        return properties.toSettings();
    }

    /**
     * 외부 이동 링크 서명·검증용 {@link TeamsLinkSigner} 빈을 생성합니다.
     * 컨텍스트에 {@link Clock} 빈이 있으면 주입받아 사용하고, 없으면 {@link Clock#systemUTC()}를 사용합니다.
     *
     * @param settings      Teams 설정 빈
     * @param clockProvider 선택적 {@link Clock} 제공자
     * @return {@link TeamsLinkSigner} 빈
     */
    @Bean
    @ConditionalOnMissingBean
    public TeamsLinkSigner teamsLinkSigner(TeamsSettings settings, ObjectProvider<Clock> clockProvider) {
        Clock clock = clockProvider.getIfAvailable(Clock::systemUTC);
        return new TeamsLinkSigner(settings, clock);
    }

    /**
     * 표준 채널 Outgoing Webhook 헤더 검증용 {@link OutgoingWebhookVerifier} 빈을 생성합니다.
     *
     * @param settings Teams 설정 빈
     * @return {@link OutgoingWebhookVerifier} 빈
     */
    @Bean
    @ConditionalOnMissingBean
    public OutgoingWebhookVerifier outgoingWebhookVerifier(TeamsSettings settings) {
        return new OutgoingWebhookVerifier(settings);
    }

    /**
     * 선언적 Adaptive Card v1.4 조립용 {@link TeamsCardFactory} 빈을 생성합니다.
     *
     * @param linkSigner 링크 서명기 빈
     * @param settings   표시 시간대 조회용 Teams 설정 빈
     * @return {@link TeamsCardFactory} 빈
     */
    @Bean
    @ConditionalOnMissingBean
    public TeamsCardFactory teamsCardFactory(TeamsLinkSigner linkSigner, TeamsSettings settings) {
        return new TeamsCardFactory(linkSigner, settings.getDisplayZone());
    }

    /**
     * 6종 대안 알림 포맷 생성용 {@link TeamsFormatFactory} 빈을 생성합니다.
     *
     * @param linkSigner           링크 서명기 빈
     * @param objectMapperProvider 소비 애플리케이션의 선택적 {@link ObjectMapper} 제공자
     * @param clockProvider        소비 애플리케이션의 선택적 {@link Clock} 제공자
     * @return {@link TeamsFormatFactory} 빈
     */
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

    /**
     * Teams Workflows Incoming Webhook 전송용 {@link TeamsWebhookClient} 빈을 생성합니다.
     * 소비 애플리케이션에 커스텀 프록시·mTLS 설정이 적용된 {@link HttpClient} 빈이 등록되어 있다면 우선 재사용합니다.
     *
     * @param settings             Teams 설정 빈
     * @param objectMapperProvider 선택적 {@link ObjectMapper} 제공자
     * @param httpClientProvider   선택적 {@link HttpClient} 제공자
     * @param clockProvider        선택적 {@link Clock} 제공자
     * @return {@link TeamsWebhookClient} 빈
     */
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

    /**
     * 소비 애플리케이션에서 주입받아 바로 사용할 대표 진입점 {@link TeamsNotifier} 빈을 생성합니다.
     *
     * @param settings                Teams 설정 빈
     * @param linkSigner              링크 서명기 빈
     * @param outgoingWebhookVerifier Outgoing Webhook 검증기 빈
     * @param cardFactory             Adaptive Card 팩토리 빈
     * @param formatFactory           멀티 포맷 팩토리 빈
     * @param webhookClient           웹훅 HTTP 클라이언트 빈
     * @return {@link TeamsNotifier} 빈
     */
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
