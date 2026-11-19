package com.example.teams.autoconfigure;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Starter 모듈의 AutoConfiguration 코딩 규칙(@ComponentScan 금지, 모든 @Bean에 @ConditionalOnMissingBean 필수) 자동 검증 테스트.
 */
class LibraryArchitectureRulesTest {

    @Test
    void autoConfigurationMustNotUseComponentScan() {
        assertThat(TeamsAutoConfiguration.class.isAnnotationPresent(ComponentScan.class))
                .as("Starter AutoConfiguration은 소비 앱 오염 방지를 위해 @ComponentScan을 사용하면 안 됩니다.")
                .isFalse();
    }

    @Test
    void everyAutoConfiguredBeanMustHaveConditionalOnMissingBean() {
        Method[] methods = TeamsAutoConfiguration.class.getDeclaredMethods();
        int beanCount = 0;
        for (Method method : methods) {
            if (method.isAnnotationPresent(Bean.class)) {
                beanCount++;
                assertThat(method.isAnnotationPresent(ConditionalOnMissingBean.class))
                        .as("@Bean 메서드 '%s'에는 반드시 @ConditionalOnMissingBean이 선언되어야 합니다.", method.getName())
                        .isTrue();
            }
        }
        assertThat(beanCount).isGreaterThan(0);
    }
}
