package com.example.billingteams.autoconfigure;

import com.example.billingteams.BillingTeamsNotifier;
import com.example.billingteams.cards.BillingAdaptiveCardFactory;
import com.example.billingteams.cards.TeamsMultiFormatNotificationFactory;
import com.example.billingteams.client.TeamsWorkflowWebhookClient;
import com.example.billingteams.config.TeamsClientSettings;
import com.example.billingteams.security.TeamsLinkSigner;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class BillingTeamsAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(BillingTeamsAutoConfiguration.class));

    @Test
    void registersAllLibraryBeansWithCustomProperties() {
        contextRunner
                .withPropertyValues(
                        "billing.teams.base-url=https://admin.external-billing.kr",
                        "billing.teams.entry-path-template=/payments/{txId}/verify",
                        "billing.teams.link-secret=external-prod-secret-key",
                        "billing.teams.link-ttl-minutes=15"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(BillingTeamsNotifier.class);
                    assertThat(context).hasSingleBean(TeamsLinkSigner.class);
                    assertThat(context).hasSingleBean(BillingAdaptiveCardFactory.class);
                    assertThat(context).hasSingleBean(TeamsMultiFormatNotificationFactory.class);
                    assertThat(context).hasSingleBean(TeamsWorkflowWebhookClient.class);

                    BillingTeamsNotifier notifier = context.getBean(BillingTeamsNotifier.class);
                    String signed = notifier.createSignedUrl("TX-EXT-100", "PAYMENT_AUTHORIZED", "view");
                    assertThat(signed).startsWith("https://admin.external-billing.kr/payments/TX-EXT-100/verify?");
                });
    }

    @Test
    void backsOffWhenConsumerDefinesCustomBean() {
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
        TeamsLinkSigner customTeamsLinkSigner(TeamsClientSettings settings) {
            return new TeamsLinkSigner(settings, Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
        }
    }
}
