package com.example.teams.autoconfigure;

import com.example.teams.TeamsNotifier;
import com.example.teams.cards.TeamsCardFactory;
import com.example.teams.cards.TeamsFormatFactory;
import com.example.teams.client.TeamsWebhookClient;
import com.example.teams.config.TeamsSettings;
import com.example.teams.security.TeamsLinkSigner;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class TeamsAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(TeamsAutoConfiguration.class));

    @Test
    void registersAllLibraryBeansWithCustomProperties() {
        contextRunner
                .withPropertyValues(
                        "teams.base-url=https://admin.external-service.kr",
                        "teams.entry-path-template=/orders/{id}/verify",
                        "teams.link-secret=external-prod-secret-key-2026",
                        "teams.link-ttl-minutes=15"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(TeamsNotifier.class);
                    assertThat(context).hasSingleBean(TeamsLinkSigner.class);
                    assertThat(context).hasSingleBean(TeamsCardFactory.class);
                    assertThat(context).hasSingleBean(TeamsFormatFactory.class);
                    assertThat(context).hasSingleBean(TeamsWebhookClient.class);

                    TeamsNotifier notifier = context.getBean(TeamsNotifier.class);
                    String signed = notifier.createSignedUrl("ORD-EXT-100", "ORDER_CREATED", "view");
                    assertThat(signed).startsWith("https://admin.external-service.kr/orders/ORD-EXT-100/verify?");
                });
    }

    @Test
    void honorsConsumerClockAndBacksOffOnCustomBean() {
        contextRunner
                .withUserConfiguration(CustomSignerConfig.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(TeamsLinkSigner.class);
                    assertThat(context.getBean(TeamsLinkSigner.class))
                            .isSameAs(context.getBean("customTeamsLinkSigner"));
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomSignerConfig {
        @Bean
        TeamsLinkSigner customTeamsLinkSigner(TeamsSettings settings) {
            return new TeamsLinkSigner(settings, Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
        }
    }
}
